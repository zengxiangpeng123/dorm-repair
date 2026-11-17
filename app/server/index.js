'use strict';
/**
 * 启动入口：一条命令起服务，浏览器直接可用。
 *
 *   node app/server/index.js
 *   PORT=8080 node app/server/index.js      （默认 5173）
 *   DEMO=0 node app/server/index.js         （关掉 /demo/* 演示接口）
 */

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const store = require('./db');
const api = require('./api');
const timers = require('./timers');
const { seed } = require('./seed');

const ROOT = path.resolve(__dirname, '..', '..');   // 宿舍维修系统/
const WEB = path.join(ROOT, 'app', 'web');
const DB_FILE = process.env.DB_FILE || path.join(__dirname, 'data', 'app.db');
const PORT = Number(process.env.PORT || 5173);
const DEMO = process.env.DEMO !== '0';
const SYSTEM_KEY = process.env.SYSTEM_KEY || 'demo-system-key';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2'
};

function serveStatic(req, res, pathname) {
  let rel = decodeURIComponent(pathname);
  if (rel.endsWith('/')) rel += 'index.html';
  const file = path.join(WEB, rel);
  if (!file.startsWith(WEB)) { res.writeHead(403); return res.end('forbidden'); }

  fs.stat(file, (err, st) => {
    if (err || !st.isFile()) {
      // 目录没有 index.html 时，退回 404 页面
      const nf = path.join(WEB, '404.html');
      if (fs.existsSync(nf)) {
        res.writeHead(404, { 'Content-Type': MIME['.html'] });
        return res.end(fs.readFileSync(nf));
      }
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end('404 ' + rel);
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
      'Cache-Control': 'no-cache'
    });
    fs.createReadStream(file).pipe(res);
  });
}

/* ---------------- 启动 ---------------- */

const db = store.open(DB_FILE);

const isEmpty = db.prepare('SELECT COUNT(*) AS n FROM orders').get().n === 0;
if (isEmpty) {
  const r = seed(db);
  console.log(`[seed] 写入演示数据：${r.orders} 张工单 / ${r.users} 个账号`);
}
if (DEMO) api.enableDemo();

const server = http.createServer((req, res) => {
  api.handle(req, res, db, { systemKey: SYSTEM_KEY }).then(handled => {
    if (!handled) serveStatic(req, res, new URL(req.url, 'http://localhost').pathname);
  }).catch(e => {
    console.error('[fatal]', e);
    if (!res.headersSent) res.writeHead(500);
    res.end('500');
  });
});

server.listen(PORT, () => {
  const now = store.nowStr(db);
  console.log('');
  console.log('  宿舍维修系统 V5.0 —— 前后端 demo 已启动');
  console.log('  ─────────────────────────────────────────');
  console.log(`  入口      http://localhost:${PORT}/`);
  console.log(`  学生端    http://localhost:${PORT}/student/`);
  console.log(`  师傅端    http://localhost:${PORT}/worker/`);
  console.log(`  管理端    http://localhost:${PORT}/admin/`);
  console.log(`  自检台    http://localhost:${PORT}/lab.html`);
  console.log(`  接口      http://localhost:${PORT}/api/v1/meta`);
  console.log(`  数据库    ${DB_FILE}`);
  console.log(`  演示接口  ${DEMO ? '已开启（/demo/reset、/demo/advance）' : '已关闭'}`);
  console.log(`  服务端时间 ${now}`);
  console.log('');
});

/* 定时任务：每 30 秒扫一遍两颗计时器；启动 2 秒后先扫一次 */
setTimeout(() => {
  const r = timers.sweep(db, m => console.log('[timer] ' + m));
  if (r.autoAccepted.length || r.archived.length) {
    console.log(`[timer] 自动验收 ${r.autoAccepted.length} 单 / 归档 ${r.archived.length} 单`);
  }
}, 2000);
const timer = setInterval(() => {
  try {
    const r = timers.sweep(db);
    if (r.autoAccepted.length || r.archived.length) {
      console.log(`[timer] 自动验收 ${r.autoAccepted.join(',')} / 归档 ${r.archived.join(',')}`);
    }
  } catch (e) { console.error('[timer]', e.message); }
}, 30000);

process.on('SIGINT', () => {
  clearInterval(timer);
  try { db.close(); } catch (e) { /* 关不掉也不影响退出 */ }
  console.log('\nbye');
  process.exit(0);
});
