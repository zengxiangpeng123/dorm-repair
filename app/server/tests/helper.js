'use strict';
/**
 * 测试用的小启动器：内存库 + 真 HTTP 服务 + 一个 fetch 包装。
 * 不引第三方库，node:test 直接跑。
 */

const http = require('node:http');
const store = require('../db');
const api = require('../api');
const { seed } = require('../seed');

const SYSTEM_KEY = 'test-system-key';

async function boot(opts = {}) {
  const db = store.open(':memory:');
  const seeded = opts.empty ? { orders: 0 } : seed(db);
  if (opts.demo !== false) api.enableDemo();

  const server = http.createServer((req, res) => {
    api.handle(req, res, db, { systemKey: SYSTEM_KEY }).then(handled => {
      if (!handled) { res.writeHead(404); res.end('not api'); }
    }).catch(e => { res.writeHead(500); res.end(String(e)); });
  });

  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}/api/v1`;
  const tokens = {};
  let current = null;

  async function call(method, path, o = {}) {
    const headers = {};
    const t = o.system ? null : (o.token || tokens[o.role || current]);
    if (t) headers.Authorization = 'Bearer ' + t;
    if (o.system) headers['X-System-Key'] = SYSTEM_KEY;
    let body;
    if (o.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(o.body);
    }
    const res = await fetch(base + path, { method, headers, body });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch (e) { /* 非 JSON 就不管 */ }
    return { status: res.status, ok: res.ok, body: json, data: json && json.data, error: json && json.error };
  }

  async function login(role, id) {
    const r = await call('POST', '/login', { body: { role, id } });
    if (!r.ok) throw new Error(`登录失败 ${role}/${id || ''}：${JSON.stringify(r.body)}`);
    if (!tokens[role]) tokens[role] = r.data.token;
    current = role;
    return r.data;
  }

  async function close() {
    await new Promise(r => server.close(r));
    try { db.close(); } catch (e) { /* ignore */ }
  }

  return { db, server, base, call, login, close, tokens, seeded };
}

/** 取当前所有工单的快照，用来断言「被拒之后状态一个都没变」 */
function snapshot(db) {
  const rows = db.prepare('SELECT no, status, version FROM orders ORDER BY no').all();
  return rows.map(r => `${r.no}|${r.status}|${r.version}`).join(',');
}

module.exports = { boot, snapshot, SYSTEM_KEY };
