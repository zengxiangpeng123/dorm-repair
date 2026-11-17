'use strict';
/**
 * HTTP 路由：统一响应体 + 错误码映射（SYS-003 第一、三、四章）。
 *
 * 成功 { ok: true, data: ... }
 * 失败 { ok: false, error: { code, message, from?, event?, allowed? } }
 */

const store = require('./db');
const S = require('./state');
const util = require('./util');
const biz = require('./orders');
const timers = require('./timers');
const { seed } = require('./seed');

const API = '/api/v1';

/* ---------------- 演示用身份（无密码，token 不签名） ---------------- */

function issueToken(user) {
  const payload = { id: user.id, role: user.role, name: user.name, ts: Date.now() };
  return 'demo.' + Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
}

function readToken(req) {
  const h = req.headers.authorization || '';
  const m = /^Bearer\s+(.+)$/i.exec(h);
  if (!m) return null;
  const tok = m[1].trim();
  if (!tok.startsWith('demo.')) return null;
  try {
    return JSON.parse(Buffer.from(tok.slice(5), 'base64url').toString('utf8'));
  } catch (e) { return null; }
}

/* ---------------- 路由表 ---------------- */

const routes = [];
function on(method, pattern, roles, handler) {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/:([a-zA-Z]+)/g, (_, k) => {
    keys.push(k);
    return '([^/]+)';
  }) + '$');
  routes.push({ method, re, keys, roles, handler });
}

/* 公开 */
on('GET', '/health', null, () => ({ ok: true, at: store.nowStr(ctx.db) }));
on('POST', '/login', null, (c) => {
  const role = String(c.body.role || '');
  const id = c.body.id ? String(c.body.id) : null;
  const users = store.listUsers(c.db).filter(u => u.role === role);
  const user = id ? users.find(u => u.id === id) : users[0];
  if (!user) throw new S.ApiError(404, `没有这个演示账号：${role}/${id || '(默认)'}`);
  return { token: issueToken(user), user: { id: user.id, role: user.role, name: user.name, building: user.building, room: user.room, team: user.team } };
});
on('GET', '/meta', null, (c) => biz.meta(c.db));
on('GET', '/state/matrix', null, () => S.matrix());
on('GET', '/state/transitions', null, () => ({
  transitions: Object.entries(S.TRANSITIONS).flatMap(([from, row]) =>
    Object.entries(row).map(([event, to]) => ({ from, event, to, trigger: S.TRIGGER[event] }))),
  cascades: S.CASCADES,
  statuses: S.STATUS.map(s => ({ code: s, label: S.STATUS_LABEL[s] }))
}));

/* 命令 */
on('POST', '/orders', ['student'], (c) => [201, biz.submit(c.db, c.actor, c.body)]);
on('POST', '/orders/:no/assign', ['system', 'admin'], (c) => biz.assign(c.db, c.actor, c.params.no));
on('POST', '/buildings/:id/assign', ['system', 'admin'], (c) => biz.assignBuilding(c.db, c.actor, c.params.id));
on('POST', '/orders/:no/arrive', ['worker'], (c) => biz.arrive(c.db, c.actor, c.params.no));
on('POST', '/buildings/:id/arrive', ['worker'], (c) => biz.arriveBuilding(c.db, c.actor, c.params.id));
on('POST', '/orders/:no/no-show', ['worker'], (c) => biz.noShow(c.db, c.actor, c.params.no, c.body));
on('POST', '/orders/:no/finish', ['worker'], (c) => biz.finish(c.db, c.actor, c.params.no, c.body));
on('POST', '/orders/:no/need-parts', ['worker'], (c) => biz.needParts(c.db, c.actor, c.params.no, c.body));
on('POST', '/orders/:no/parts-arrived', ['system', 'admin'], (c) => biz.partsArrived(c.db, c.actor, c.params.no));
on('POST', '/orders/:no/accept', ['student'], (c) => biz.accept(c.db, c.actor, c.params.no));
on('POST', '/orders/:no/reject', ['student'], (c) => biz.reject(c.db, c.actor, c.params.no, c.body));
on('POST', '/orders/:no/cancel', ['student'], (c) => biz.cancel(c.db, c.actor, c.params.no));
on('POST', '/orders/:no/cancel-request', ['student'], (c) => [202, biz.cancelRequest(c.db, c.actor, c.params.no, c.body)]);
on('POST', '/orders/:no/admin-cancel', ['admin'], () => biz.adminCancel());
on('POST', '/orders/:no/auto-accept', ['system'], (c) => biz.autoAccept(c.db, c.actor, c.params.no));
on('POST', '/orders/:no/archive', ['system'], (c) => biz.archive(c.db, c.actor, c.params.no));

/* 查询 */
on('GET', '/orders/:no', ['student', 'worker', 'admin', 'system'], (c) => biz.detail(c.db, c.params.no));
on('GET', '/orders/:no/events', ['student', 'worker', 'admin', 'system'], (c) => {
  const o = biz.load(c.db, c.params.no);
  const d = biz.detail(c.db, o.no);
  return { items: d.events, page: 1, pageSize: d.events.length, total: d.events.length };
});
on('GET', '/my/orders', ['student'], (c) => biz.myOrders(c.db, c.actor, c.query));
on('GET', '/worker/todo', ['worker'], (c) => biz.workerTodo(c.db, c.actor));
on('GET', '/buildings/:id/board', ['worker', 'admin'], (c) => biz.buildingBoard(c.db, c.actor, c.params.id));
on('GET', '/admin/exceptions', ['admin'], (c) => biz.exceptions(c.db, c.query.kind || 'ALL'));
on('GET', '/admin/dashboard', ['admin'], (c) => biz.dashboard(c.db));
on('GET', '/admin/config', ['admin'], (c) => store.getConfig(c.db));
on('PUT', '/admin/config', ['admin'], (c) => store.setConfig(c.db, c.body || {}));
on('GET', '/admin/orders', ['admin'], (c) => {
  const status = c.query.status && c.query.status !== 'ALL' ? c.query.status.split(',') : null;
  const list = store.listOrders(c.db, { status });
  return biz.pageOf ? biz.pageOf(list.map(o => biz.dto(c.db, o, { shortDesc: true })), c.query.page, c.query.pageSize) : list;
});

/* 演示（DEMO=1 才注册） */
function registerDemo(on) {
  on('POST', '/demo/reset', null, (c) => {
    const r = seed(c.db);
    return { ...r, now: store.nowStr(c.db) };
  });
  on('POST', '/demo/advance', null, (c) => timers.advance(c.db, c.body || {}));
  on('POST', '/demo/seed-empty-photo', null, (c) => {
    const no = c.body && c.body.no;
    const list = c.body && c.body.no
      ? [store.getOrder(c.db, c.body.no)]
      : store.listOrders(c.db, { status: 'VERIFYING' });
    const target = list.filter(Boolean)[0];
    if (!target) throw new S.ApiError(404, '没有待验收的单可以做样本');
    store.updateOrderCas(c.db, target.no, target.version, { photos_after: [] });
    return { no: target.no, photosAfter: [] };
  });
  on('POST', '/demo/seed-cancel-request', null, (c) => {
    const target = store.listOrders(c.db, { status: 'REPAIRING' })[0];
    if (!target) throw new S.ApiError(404, '没有维修中的单');
    const now = store.nowStr(c.db);
    store.updateOrderCas(c.db, target.no, target.version, {
      cancel_req_at: now, cancel_req_by: target.student.id, cancel_req_reason: '临时离校，先不修了'
    });
    store.insertEvent(c.db, {
      no: target.no, at: now, event: 'CANCEL_REQUEST',
      actorType: 'student', actorId: target.student.id, actorName: target.student.name,
      manual: 1, note: '申请撤销：临时离校，先不修了', payload: { reason: '临时离校，先不修了' }
    });
    return { no: target.no };
  });
}

let demoEnabled = false;
/** 演示接口只在 DEMO=1 时注册（生产环境这些路由根本不存在） */
function enableDemo() {
  if (demoEnabled) return;
  registerDemo(on);
  demoEnabled = true;
}

/* ---------------- 执行 ---------------- */

const ctx = { db: null };

function send(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    let size = 0;
    req.on('data', chunk => {
      size += chunk.length;
      if (size > 2 * 1024 * 1024) { reject(new S.ApiError(400, '请求体过大（>2MB）')); req.destroy(); return; }
      raw += chunk;
    });
    req.on('end', () => {
      if (!raw.trim()) return resolve({});
      try { resolve(JSON.parse(raw)); }
      catch (e) { reject(new S.ApiError(400, '请求体不是合法 JSON')); }
    });
    req.on('error', reject);
  });
}

function match(method, path) {
  for (const r of routes) {
    if (r.method !== method) continue;
    const m = r.re.exec(path);
    if (!m) continue;
    const params = {};
    r.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); });
    return { route: r, params };
  }
  return null;
}

async function handle(req, res, db, opts = {}) {
  const url = new URL(req.url, 'http://localhost');
  const path = url.pathname.replace(/\/+$/, '') || '/';

  if (!path.startsWith(API)) return false;

  const sub = path.slice(API.length) || '/';
  const hit = match(req.method, sub);
  ctx.db = db;

  if (!hit) {
    send(res, 404, { ok: false, error: { code: 404, message: `没有这个接口：${req.method} ${path}` } });
    return true;
  }

  let body = {};
  try {
    if (req.method !== 'GET') body = await readBody(req);
  } catch (e) {
    send(res, e.code || 400, { ok: false, error: { code: e.code || 400, message: e.message } });
    return true;
  }

  let actor = null;
  if (hit.route.roles) {
    const tok = readToken(req);
    const sysKey = req.headers['x-system-key'];
    if (!tok && sysKey && sysKey === (opts.systemKey || 'demo-system-key')) {
      actor = { type: 'system', id: 'system', name: '定时任务' };
    } else if (!tok) {
      send(res, 401, { ok: false, error: { code: 401, message: '未登录或 token 失效' } });
      return true;
    } else {
      actor = { type: tok.role, id: tok.id, name: tok.name };
    }
    if (!hit.route.roles.includes(actor.type)) {
      send(res, 403, {
        ok: false,
        error: { code: 403, message: `角色 ${actor.type} 不能调用这个接口`, allow: hit.route.roles }
      });
      return true;
    }
  }

  const query = Object.fromEntries(url.searchParams.entries());
  // 乐观锁统一在这里查：请求体带 version 且和库里不一致 → 409 VERSION_CONFLICT
  // （放在路由层做，12 条命令不用各写一遍，也不会漏）
  if (hit.params.no && body && body.version !== undefined && body.version !== null) {
    const row = store.getOrder(db, hit.params.no);
    if (row && Number(body.version) !== Number(row.version)) {
      send(res, 409, {
        ok: false,
        error: {
          code: 409, message: '状态已变化，请刷新',
          reason: 'VERSION_CONFLICT', from: row.status,
          expectVersion: row.version, gotVersion: Number(body.version),
          allowed: S.allowedEvents(row.status)
        }
      });
      return true;
    }
  }

  try {
    const out = await hit.route.handler({ db, actor, params: hit.params, query, body, req });
    const [code, data] = Array.isArray(out) ? out : [200, out];
    send(res, code, { ok: true, data });
  } catch (e) {
    if (e instanceof S.ApiError || e.code) {
      const code = e.code || 500;
      const error = { code, message: e.message, ...(e.extra || {}) };
      send(res, code, { ok: false, error });
    } else {
      console.error('[500]', req.method, path, e);
      send(res, 500, { ok: false, error: { code: 500, message: '服务端错：' + e.message } });
    }
  }
  return true;
}

module.exports = { handle, registerDemo, enableDemo, issueToken, readToken, API, routes };
