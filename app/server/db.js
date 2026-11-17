'use strict';
/**
 * 数据库：node:sqlite（Node ≥ 22.5 内置，零第三方依赖）。
 * 一个文件 app/server/data/app.db；表结构见 docs/04_项目管理/代码实施计划-P3P4.md 第六章。
 */

const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const { fmt, fmtDay, parse, toJson, fromJson } = require('./util');
const { STATUS, STATUS_LABEL, ACTIVE, TERMINAL } = require('./state');

const SCHEMA = `
CREATE TABLE IF NOT EXISTS orders (
  no TEXT PRIMARY KEY,
  status TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  building TEXT NOT NULL,
  room TEXT NOT NULL,
  floor INTEGER,
  student_id TEXT, student_name TEXT,
  category TEXT, level TEXT, level_source TEXT,
  desc TEXT, window TEXT,
  worker_id TEXT, worker_name TEXT, worker_team TEXT,
  photos_before TEXT, photos_after TEXT,
  ai_confidence REAL, ai_basis TEXT, ai_fallback INTEGER DEFAULT 0,
  parts_name TEXT, parts_amount REAL, parts_eta TEXT, parts_status TEXT,
  rework_of TEXT, rework_index INTEGER, rework_count INTEGER DEFAULT 0,
  no_show_count INTEGER DEFAULT 0,
  cancel_req_at TEXT, cancel_req_by TEXT, cancel_req_reason TEXT,
  created_at TEXT, assigned_at TEXT, arrive_at TEXT, finish_at TEXT,
  sign_at TEXT, closed_at TEXT, cancelled_at TEXT, status_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_building ON orders(building, status);

CREATE TABLE IF NOT EXISTS events (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  no TEXT NOT NULL,
  at TEXT NOT NULL,
  event TEXT NOT NULL,
  actor_type TEXT NOT NULL,
  actor_id TEXT,
  actor_name TEXT,
  manual INTEGER NOT NULL DEFAULT 1,
  note TEXT,
  payload TEXT
);
CREATE INDEX IF NOT EXISTS idx_events_no ON events(no, seq);

CREATE TABLE IF NOT EXISTS exceptions (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  no TEXT,
  detail TEXT,
  status TEXT NOT NULL DEFAULT 'OPEN',
  created_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_exceptions_no ON exceptions(no);

CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT);

CREATE TABLE IF NOT EXISTS seq (day TEXT PRIMARY KEY, n INTEGER NOT NULL);

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  role TEXT NOT NULL,
  name TEXT NOT NULL,
  building TEXT, room TEXT, team TEXT, phone TEXT
);

CREATE TABLE IF NOT EXISTS idem (key TEXT PRIMARY KEY, body TEXT, at TEXT);
`;

const DEFAULT_CONFIG = {
  budgetLine: 200,      // 预算线（元），超过进管理端「超预算采购」
  verifyHours: 168,     // T1：完工后 168 小时（7 天）学生不响应 → 自动验收
  archiveDays: 7,       // T2：签字后 7 天 → 自动归档
  simOffsetMs: 0        // 演示用时间前移（毫秒），不改进程时钟
};

/* ---------------- 打开 / 建表 ---------------- */

function open(file) {
  const dir = path.dirname(file);
  if (file !== ':memory:') fs.mkdirSync(dir, { recursive: true });
  const db = new DatabaseSync(file);
  db.exec('PRAGMA journal_mode = WAL;');
  db.exec('PRAGMA foreign_keys = ON;');
  db.exec(SCHEMA);
  for (const [k, v] of Object.entries(DEFAULT_CONFIG)) {
    db.prepare('INSERT OR IGNORE INTO config(key, value) VALUES (?, ?)').run(k, String(v));
  }
  return db;
}

/* ---------------- 配置 ---------------- */

function getConfig(db) {
  const rows = db.prepare('SELECT key, value FROM config').all();
  const out = { ...DEFAULT_CONFIG };
  for (const r of rows) {
    const n = Number(r.value);
    out[r.key] = Number.isFinite(n) && String(n) === String(r.value).trim() ? n : r.value;
  }
  return out;
}

function setConfig(db, patch) {
  const stmt = db.prepare('INSERT INTO config(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value');
  for (const [k, v] of Object.entries(patch)) {
    if (!(k in DEFAULT_CONFIG)) continue;
    stmt.run(k, String(v));
  }
  return getConfig(db);
}

/** 服务端"现在"：真实时间 + 演示偏移 */
function nowMs(db) {
  const cfg = getConfig(db);
  return Date.now() + Number(cfg.simOffsetMs || 0);
}

function nowStr(db) { return fmt(nowMs(db)); }

/* ---------------- 单号自增 ---------------- */

function nextSeq(db, day) {
  const row = db.prepare('SELECT n FROM seq WHERE day = ?').get(day);
  const n = row ? row.n + 1 : 1;
  db.prepare('INSERT INTO seq(day, n) VALUES (?, ?) ON CONFLICT(day) DO UPDATE SET n = excluded.n').run(day, n);
  return n;
}

/* ---------------- 事件 ---------------- */

function insertEvent(db, e) {
  db.prepare(`INSERT INTO events(no, at, event, actor_type, actor_id, actor_name, manual, note, payload)
              VALUES (?,?,?,?,?,?,?,?,?)`).run(
    e.no, e.at, e.event, e.actorType,
    e.actorId || null, e.actorName || null,
    e.manual == null ? 1 : e.manual,
    e.note || null, e.payload ? toJson(e.payload) : null
  );
}

function eventsOf(db, no) {
  return db.prepare('SELECT * FROM events WHERE no = ? ORDER BY seq ASC').all(no).map(rowToEvent);
}

function rowToEvent(r) {
  return {
    seq: r.seq, no: r.no, at: r.at, event: r.event,
    actorType: r.actor_type, actorId: r.actor_id, actorName: r.actor_name,
    manual: r.manual, note: r.note || '',
    payload: fromJson(r.payload, null)
  };
}

/* ---------------- 工单 ---------------- */

function getOrder(db, no) {
  const r = db.prepare('SELECT * FROM orders WHERE no = ?').get(no);
  return r ? rowToOrder(r) : null;
}

function rowToOrder(r) {
  return {
    no: r.no, status: r.status, statusLabel: STATUS_LABEL[r.status] || r.status,
    version: r.version,
    building: r.building, room: r.room, floor: r.floor,
    student: { id: r.student_id, name: r.student_name },
    category: r.category, level: r.level, levelSource: r.level_source,
    desc: r.desc, window: r.window,
    worker: r.worker_id ? { id: r.worker_id, name: r.worker_name, team: r.worker_team } : null,
    photosBefore: fromJson(r.photos_before, []),
    photosAfter: fromJson(r.photos_after, []),
    ai: {
      category: r.category, level: r.level,
      confidence: r.ai_confidence, basis: r.ai_basis, fallback: !!r.ai_fallback
    },
    parts: r.parts_name ? {
      name: r.parts_name, amount: r.parts_amount, etaDate: r.parts_eta, status: r.parts_status
    } : null,
    reworkOf: r.rework_of, reworkIndex: r.rework_index, reworkCount: r.rework_count,
    noShowCount: r.no_show_count,
    cancelRequest: r.cancel_req_at ? {
      at: r.cancel_req_at, by: r.cancel_req_by, reason: r.cancel_req_reason
    } : null,
    createdAt: r.created_at, assignedAt: r.assigned_at, arriveAt: r.arrive_at,
    finishAt: r.finish_at, signAt: r.sign_at, closedAt: r.closed_at,
    cancelledAt: r.cancelled_at, statusAt: r.status_at
  };
}

const ORDER_COLUMNS = [
  'status', 'version', 'building', 'room', 'floor', 'student_id', 'student_name',
  'category', 'level', 'level_source', 'desc', 'window',
  'worker_id', 'worker_name', 'worker_team', 'photos_before', 'photos_after',
  'ai_confidence', 'ai_basis', 'ai_fallback',
  'parts_name', 'parts_amount', 'parts_eta', 'parts_status',
  'rework_of', 'rework_index', 'rework_count', 'no_show_count',
  'cancel_req_at', 'cancel_req_by', 'cancel_req_reason',
  'created_at', 'assigned_at', 'arrive_at', 'finish_at', 'sign_at',
  'closed_at', 'cancelled_at', 'status_at'
];

const JSON_COLUMNS = { photos_before: [], photos_after: [] };

function insertOrder(db, o) {
  const cols = ['no'].concat(ORDER_COLUMNS);
  const vals = cols.map(c => {
    let v = c === 'no' ? o.no : o[c];
    if (c in JSON_COLUMNS) v = toJson(v || JSON_COLUMNS[c]);
    return v === undefined ? null : v;
  });
  db.prepare(`INSERT INTO orders(${cols.join(',')}) VALUES (${cols.map(() => '?').join(',')})`).run(...vals);
}

/**
 * 乐观锁更新：只有 version 对得上才写。
 * 返回 false 表示有人抢在前面改了 → 调用方抛 409 VERSION_CONFLICT。
 */
function updateOrderCas(db, no, version, patch) {
  const keys = Object.keys(patch).filter(k => ORDER_COLUMNS.includes(k));
  if (!keys.length) return true;
  const sets = keys.map(k => `${k} = ?`).join(', ');
  const vals = keys.map(k => {
    let v = patch[k];
    if (k in JSON_COLUMNS && v !== null && typeof v !== 'string') v = toJson(v);
    return v === undefined ? null : v;
  });
  const info = db.prepare(`UPDATE orders SET ${sets}, version = version + 1 WHERE no = ? AND version = ?`)
    .run(...vals, no, version);
  return info.changes === 1;
}

function listOrders(db, filter = {}) {
  const where = [];
  const args = [];
  if (filter.status) {
    const arr = Array.isArray(filter.status) ? filter.status : [filter.status];
    where.push(`status IN (${arr.map(() => '?').join(',')})`);
    args.push(...arr);
  }
  if (filter.building) { where.push('building = ?'); args.push(filter.building); }
  if (filter.workerId) { where.push('worker_id = ?'); args.push(filter.workerId); }
  if (filter.studentId) { where.push('student_id = ?'); args.push(filter.studentId); }
  if (filter.reworkOf) { where.push('rework_of = ?'); args.push(filter.reworkOf); }
  const sql = `SELECT * FROM orders ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY created_at DESC`;
  return db.prepare(sql).all(...args).map(rowToOrder);
}

function allOrders(db) { return db.prepare('SELECT * FROM orders').all().map(rowToOrder); }

/* ---------------- 例外 ---------------- */

function addException(db, e) {
  const id = e.id || `EX${fmtDay(parse(e.createdAt) || Date.now())}${String(Math.floor(Math.random() * 9000) + 1000)}`;
  db.prepare(`INSERT OR REPLACE INTO exceptions(id, kind, no, detail, status, created_at)
              VALUES (?,?,?,?,?,?)`).run(id, e.kind, e.no, e.detail, e.status || 'OPEN', e.createdAt);
  return id;
}

function listExceptions(db) {
  return db.prepare('SELECT * FROM exceptions ORDER BY created_at DESC').all().map(r => ({
    id: r.id, kind: r.kind, no: r.no, detail: r.detail, status: r.status, createdAt: r.created_at
  }));
}

/* ---------------- 用户 ---------------- */

function getUser(db, id) { return db.prepare('SELECT * FROM users WHERE id = ?').get(id) || null; }
function usersByRole(db, role) { return db.prepare('SELECT * FROM users WHERE role = ?').all(role); }
function listUsers(db) { return db.prepare('SELECT * FROM users ORDER BY role, id').all(); }
function insertUser(db, u) {
  db.prepare('INSERT OR REPLACE INTO users(id, role, name, building, room, team, phone) VALUES (?,?,?,?,?,?,?)')
    .run(u.id, u.role, u.name, u.building || null, u.room || null, u.team || null, u.phone || null);
}

function wipe(db) {
  for (const t of ['orders', 'events', 'exceptions', 'seq', 'idem']) db.exec(`DELETE FROM ${t};`);
}

module.exports = {
  open, getConfig, setConfig, nowMs, nowStr, nextSeq,
  insertEvent, eventsOf, getOrder, insertOrder, updateOrderCas, listOrders, allOrders, rowToOrder,
  addException, listExceptions,
  getUser, usersByRole, listUsers, insertUser, wipe,
  DEFAULT_CONFIG, ORDER_COLUMNS, ACTIVE, TERMINAL, STATUS
};
