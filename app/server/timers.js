'use strict';
/**
 * 定时任务：T1 验收超时（168h）与 T2 归档（7 天）。
 *
 * 两颗表各算各的起点：T1 认 finishAt，T2 认 signAt。
 * 演示时不用等七天 —— /demo/advance 只是把 simOffsetMs 往前挪，读的是同一个口径。
 */

const store = require('./db');
const S = require('./state');
const util = require('./util');
const biz = require('./orders');

const SYSTEM = { type: 'system', id: 'system', name: '定时任务' };

function sweep(db, log = () => {}) {
  const cfg = store.getConfig(db);
  const now = store.nowMs(db);
  const out = { autoAccepted: [], archived: [], errors: [] };

  const verifying = store.listOrders(db, { status: 'VERIFYING' });
  for (const o of verifying) {
    const from = util.parse(o.finishAt);
    if (from == null) continue;
    if (now < from + cfg.verifyHours * util.HOUR) continue;
    try {
      biz.autoAccept(db, SYSTEM, o.no);
      out.autoAccepted.push(o.no);
      log(`T1 到点：${o.no} → 自动验收（manual=0）`);
    } catch (e) {
      out.errors.push({ no: o.no, code: e.code, message: e.message });
    }
  }

  const done = store.listOrders(db, { status: 'DONE' });
  for (const o of done) {
    const from = util.parse(o.signAt);
    if (from == null) continue;
    if (now < from + cfg.archiveDays * util.DAY) continue;
    try {
      biz.archive(db, SYSTEM, o.no);
      out.archived.push(o.no);
      log(`T2 到点：${o.no} → 已关闭`);
    } catch (e) {
      out.errors.push({ no: o.no, code: e.code, message: e.message });
    }
  }

  return out;
}

/** 演示：把服务端时间前移（不改进程时钟） */
function advance(db, { hours = 0, days = 0 } = {}) {
  const cfg = store.getConfig(db);
  const delta = Number(hours) * util.HOUR + Number(days) * util.DAY;
  if (!Number.isFinite(delta) || delta === 0) throw new S.ApiError(422, 'advance 需要 hours 或 days');
  store.setConfig(db, { simOffsetMs: Number(cfg.simOffsetMs || 0) + delta });
  const result = sweep(db);
  return { offsetHours: +(Number(store.getConfig(db).simOffsetMs) / util.HOUR).toFixed(2), ...result, now: store.nowStr(db) };
}

module.exports = { sweep, advance, SYSTEM };
