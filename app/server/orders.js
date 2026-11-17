'use strict';
/**
 * 业务层：12 条命令 + 查询。
 *
 * 铁律（SYS-003 第八章）：
 *   1. 所有状态变更只走 state.next() 查表，业务代码里没有 status 赋值
 *   2. 一次转移 + 它的事件 + 它的级联在同一个事务里
 *   3. manual 由 actorType 推导，不接受客户端传入
 *   4. 时间只由服务端生成
 */

const store = require('./db');
const S = require('./state');
const util = require('./util');
const { classify } = require('./ai');
const { ApiError } = S;

const WINDOWS = [
  '全天（08:00-22:00）',
  '上午 08:00-12:00',
  '下午 14:00-18:00',
  '晚上 18:00-22:00'
];
const NO_SHOW_REASONS = {
  KNOCK_NO_ANSWER: '敲门没人应',
  CALL_UNREACHABLE: '电话打不通',
  STUDENT_NOT_IN: '学生说不在寝室',
  DOOR_LOCKED: '门反锁进不去'
};
const FINISH_TAGS = ['更换软管', '重新打胶', '紧固螺丝', '疏通管道', '更换阀芯', '更换面板模块', '调节校正', '清洁现场'];
const CANCEL_REASONS = ['不需要修了', '重复报修', '自己已经修好', '信息填错'];
const CANCEL_KIND = {
  MISMATCH: '数据对不上',
  OVER_BUDGET: '超预算采购',
  REPEAT_REWORK: '反复返修',
  CANCEL_REQUEST: '学生申请撤销'
};

/* ------------------------------------------------------------------ */
/* 基础设施                                                            */
/* ------------------------------------------------------------------ */

function withTx(db, fn) {
  db.exec('BEGIN');
  try {
    const r = fn();
    db.exec('COMMIT');
    return r;
  } catch (e) {
    try { db.exec('ROLLBACK'); } catch (_) { /* 已经回滚过 */ }
    throw e;
  }
}

function load(db, no) {
  const o = store.getOrder(db, no);
  if (!o) throw new ApiError(404, `工单 ${no || '(空)'} 不存在`);
  return o;
}

function checkVersion(o, payload, event) {
  if (!payload || payload.version == null) return;
  if (Number(payload.version) !== o.version) {
    throw new ApiError(409, '状态已变化，请刷新', {
      reason: 'VERSION_CONFLICT',
      from: o.status, event, allowed: S.allowedEvents(o.status),
      currentVersion: o.version
    });
  }
}

function ownCheck(order, actor, what) {
  if (actor.type === 'worker' && order.worker && order.worker.id !== actor.id) {
    throw new ApiError(403, `该工单不属于此师傅（当前师傅 ${order.worker.name}）`, { no: order.no, actor: actor.id });
  }
  if (actor.type === 'student' && order.student && order.student.id !== actor.id) {
    throw new ApiError(403, `这不是你报修的工单（${what || '只能操作本人的单'}）`, { no: order.no, actor: actor.id });
  }
}

/** 唯一的状态写入点：查表 → 断言触发方 → 乐观锁 → 写事件 */
function apply(db, { order, event, actor, note, payload, patch }) {
  S.assertCanTrigger(event, actor);
  const to = S.next(order.status, event);           // 非法转移在这里 409
  const now = store.nowStr(db);
  const next = Object.assign({ status: to, status_at: now }, patch || {});
  if (!store.updateOrderCas(db, order.no, order.version, next)) {
    throw new ApiError(409, '状态已变化，请刷新', {
      reason: 'VERSION_CONFLICT',
      from: order.status, event, allowed: S.allowedEvents(order.status)
    });
  }
  store.insertEvent(db, {
    no: order.no, at: now, event,
    actorType: actor.type, actorId: actor.id, actorName: actor.name,
    manual: actor.type === 'system' ? 0 : 1,
    note: note || '', payload: payload || null
  });
  return { to, now };
}

function photos(arr) {
  if (!Array.isArray(arr)) return [];
  return arr.filter(x => typeof x === 'string' && x.trim()).slice(0, 3);
}

/* ------------------------------------------------------------------ */
/* 12 条命令                                                          */
/* ------------------------------------------------------------------ */

/** 1 SUBMIT 建单 */
function submit(db, actor, body = {}) {
  S.assertCanTrigger('SUBMIT', actor);
  const desc = String(body.desc || '').trim();
  const before = photos(body.photoUrls);
  if (!before.length) throw new ApiError(422, '现场照片至少 1 张');
  if (desc.length < 4) throw new ApiError(422, '描述至少 4 个字');
  const win = body.window && WINDOWS.includes(body.window) ? body.window : WINDOWS[0];

  const me = store.getUser(db, actor.id) || {};
  const building = body.building || me.building;
  const room = body.room || me.room;
  if (!building || !room) throw new ApiError(422, '账号没有绑定楼栋/寝室，不能报修');

  const ai = classify(desc, null);
  const nowMs = store.nowMs(db);
  const day = util.fmtDay(nowMs);

  return withTx(db, () => {
    const no = util.makeNo(day, store.nextSeq(db, day));
    store.insertOrder(db, {
      no,
      status: S.next('NEW', 'SUBMIT'),
      version: 1,
      building, room,
      floor: Number(String(room)[0]) || null,
      student_id: actor.id, student_name: actor.name,
      category: ai.category, level: ai.level, level_source: 'ai',
      desc: desc.slice(0, 500), window: win,
      photos_before: before, photos_after: [],
      ai_confidence: ai.confidence, ai_basis: ai.basis, ai_fallback: ai.fallback ? 1 : 0,
      created_at: util.fmt(nowMs), status_at: util.fmt(nowMs)
    });
    store.insertEvent(db, {
      no, at: util.fmt(nowMs), event: 'SUBMIT',
      actorType: actor.type, actorId: actor.id, actorName: actor.name, manual: 1,
      note: '学生提交报修', payload: { window: win, photos: before.length }
    });
    return detail(db, no);
  });
}

/** 2 ASSIGN 派工 */
function assign(db, actor, no) {
  S.assertCanTrigger('ASSIGN', actor);
  const o = load(db, no);
  const worker = store.usersByRole(db, 'worker').find(w => w.building === o.building);
  if (!worker) return { assigned: false, reason: 'NO_WORKER_ON_DUTY', no: o.no };
  if (o.status !== 'PENDING') {
    // 状态不对是 409，交给状态机说
    S.next(o.status, 'ASSIGN');
  }
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'ASSIGN', actor,
      note: `派给 ${worker.name}（${worker.team}）`,
      payload: { workerId: worker.id },
      patch: {
        worker_id: worker.id, worker_name: worker.name, worker_team: worker.team,
        assigned_at: store.nowStr(db)
      }
    });
    return { assigned: true, no: o.no, worker: { id: worker.id, name: worker.name }, version: o.version + 1 };
  });
}

/** 2b 批量派工（逐单独立事务，允许部分成功） */
function assignBuilding(db, actor, buildingId) {
  S.assertCanTrigger('ASSIGN', actor);
  const pend = store.listOrders(db, { status: 'PENDING', building: buildingId });
  const ok = [], failed = [];
  for (const o of pend) {
    try {
      const r = assign(db, actor, o.no);
      if (r.assigned) ok.push(o.no);
      else failed.push({ no: o.no, reason: r.reason });
    } catch (e) {
      failed.push({ no: o.no, code: e.code || 500, message: e.message });
    }
  }
  return { ok, failed };
}

/** 3 ARRIVE 单级打卡 */
function arrive(db, actor, no) {
  S.assertCanTrigger('ARRIVE', actor);
  const o = load(db, no);
  ownCheck(o, actor, '非本单师傅不能打卡');
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'ARRIVE', actor,
      note: '师傅到门打卡', payload: null,
      patch: { arrive_at: store.nowStr(db) }
    });
    return { no: o.no, status: S.next(o.status, 'ARRIVE'), arriveAt: store.getOrder(db, no).arriveAt };
  });
}

/**
 * 3b 楼栋打卡：界面上一个按钮，背后 N 条单级 ARRIVE（SYS-003 3.3）。
 * 逐单独立事务，一单失败不影响其他单，部分成功如实返回。
 */
function arriveBuilding(db, actor, buildingId) {
  S.assertCanTrigger('ARRIVE', actor);
  const mine = store.listOrders(db, { status: ['ASSIGNED', 'REPAIRING'], building: buildingId, workerId: actor.id });
  const ok = [], failed = [], skipped = [];
  for (const o of mine) {
    if (o.status === 'REPAIRING') { skipped.push({ no: o.no, reason: 'ALREADY_REPAIRING' }); continue; }
    try {
      const r = arrive(db, actor, o.no);
      ok.push({ no: o.no, status: r.status });
    } catch (e) {
      failed.push({ no: o.no, code: e.code || 500, message: e.message });
    }
  }
  return { arrivedAt: store.nowStr(db), buildingId, ok, failed, skipped };
}

/** 4 NO_SHOW 到访未遇 */
function noShow(db, actor, no, body = {}) {
  S.assertCanTrigger('NO_SHOW', actor);
  const o = load(db, no);
  ownCheck(o, actor);
  const reason = body.reason;
  if (!NO_SHOW_REASONS[reason]) throw new ApiError(422, `未遇原因必须是 ${Object.keys(NO_SHOW_REASONS).join('/')} 之一`);
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'NO_SHOW', actor,
      note: `到访未遇：${NO_SHOW_REASONS[reason]}${body.note ? '；' + body.note : ''}`,
      payload: { reason, note: body.note || '' },
      patch: {
        worker_id: null, worker_name: null, worker_team: null,
        no_show_count: o.noShowCount + 1
      }
    });
    return { no: o.no, status: 'PENDING', reason };
  });
}

/** 5 FINISH 完工 */
function finish(db, actor, no, body = {}) {
  S.assertCanTrigger('FINISH', actor);
  const o = load(db, no);
  ownCheck(o, actor);
  const after = photos(body.photoUrls);
  if (!after.length) throw new ApiError(422, '完工照至少 1 张');
  const tags = Array.isArray(body.noteTags) ? body.noteTags.filter(t => FINISH_TAGS.includes(t)) : [];
  if (!tags.length) throw new ApiError(422, '至少选一个处理说明（免打字）');
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'FINISH', actor,
      note: tags.join(' / ') + (body.note ? `；${body.note}` : ''),
      payload: { tags, note: body.note || '' },
      patch: { photos_after: after, finish_at: store.nowStr(db) }
    });
    return { no: o.no, status: 'VERIFYING', finishAt: store.getOrder(db, no).finishAt };
  });
}

/** 6 NEED_PARTS 报缺件：预算判断不改状态 */
function needParts(db, actor, no, body = {}) {
  S.assertCanTrigger('NEED_PARTS', actor);
  const o = load(db, no);
  ownCheck(o, actor);
  const name = String(body.name || '').trim();
  const amount = Number(body.amount);
  const etaDate = String(body.etaDate || '').trim();
  if (!name) throw new ApiError(422, '缺件名称必填');
  if (!Number.isFinite(amount) || amount <= 0) throw new ApiError(422, '金额必填且大于 0');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(etaDate)) throw new ApiError(422, '到货日期必填（yyyy-MM-dd）');

  const cfg = store.getConfig(db);
  const overBudget = amount > Number(cfg.budgetLine);
  const partsStatus = overBudget ? 'PENDING_APPROVAL' : 'ORDERED';

  return withTx(db, () => {
    apply(db, {
      order: o, event: 'NEED_PARTS', actor,
      note: `缺件：${name} ${amount} 元${overBudget ? `，超预算线 ${cfg.budgetLine} 元，转管理端` : '，预算内自动下单'}`,
      payload: { name, amount, etaDate, overBudget },
      patch: { parts_name: name, parts_amount: amount, parts_eta: etaDate, parts_status: partsStatus }
    });
    return { no: o.no, status: 'PARTS', overBudget, parts: { name, amount, etaDate, status: partsStatus, overBudget } };
  });
}

/** 7 PARTS_ARRIVED 配件到货：回队列，保留原紧急度 */
function partsArrived(db, actor, no) {
  S.assertCanTrigger('PARTS_ARRIVED', actor);
  const o = load(db, no);
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'PARTS_ARRIVED', actor,
      note: `配件到货（${o.parts ? o.parts.name : '—'}），重新排队，紧急度保留「${o.level}」`,
      payload: o.parts,
      patch: { parts_status: 'ARRIVED', worker_id: null, worker_name: null, worker_team: null }
    });
    return { no: o.no, status: 'PENDING', level: o.level };
  });
}

/** 8 ACCEPT 验收通过（返修单则级联关闭原单） */
function accept(db, actor, no) {
  S.assertCanTrigger('ACCEPT', actor);
  const o = load(db, no);
  ownCheck(o, actor, '只有报修人能验收');
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'ACCEPT', actor,
      note: '学生确认修好并签字', payload: null,
      patch: { sign_at: store.nowStr(db) }
    });
    let closedOrigin = null;
    if (o.reworkOf) {
      const origin = store.getOrder(db, o.reworkOf);
      if (origin && origin.status === 'REWORKING') {
        const to = S.nextCascade(origin.status, 'ORIGIN_CLOSED');
        const now = store.nowStr(db);
        store.updateOrderCas(db, origin.no, origin.version, { status: to, status_at: now, closed_at: now });
        // 记在学生头上：这条关闭虽然不是转移表里的一条边，但确实是人的动作引起的，
        // 记成 system 会让"零人工"虚高。
        store.insertEvent(db, {
          no: origin.no, at: now, event: 'ORIGIN_CLOSED',
          actorType: actor.type, actorId: actor.id, actorName: actor.name, manual: 1,
          note: `返修单 ${o.no} 验收通过，原单关闭（级联，不占 13 条转移）`, payload: { byRework: o.no }
        });
        closedOrigin = origin.no;
      }
    }
    return { no: o.no, status: 'DONE', closedOrigin };
  });
}

/** 9 REJECT 验收不通过：生成 -R{n}，紧急度升一档 */
function reject(db, actor, no, body = {}) {
  S.assertCanTrigger('REJECT', actor);
  const o = load(db, no);
  ownCheck(o, actor, '只有报修人能驳回');
  const reason = String(body.reason || '').trim();
  if (reason.length < 2) throw new ApiError(422, '请写一句哪儿还没修好');

  return withTx(db, () => {
    apply(db, {
      order: o, event: 'REJECT', actor,
      note: `验收不通过：${reason}`, payload: { reason }
    });

    const base = o.reworkOf || o.no;
    const chain = store.listOrders(db, { reworkOf: base });
    const index = chain.reduce((m, x) => Math.max(m, x.reworkIndex || 0), o.reworkOf ? (o.reworkIndex || 0) : 0) + 1;
    const newNo = util.makeReworkNo(base, index);
    const levelIdx = ['普通', '较急', '紧急'].indexOf(o.level);
    const newLevel = ['普通', '较急', '紧急'][Math.min(levelIdx + 1, 2)];
    const now = store.nowStr(db);

    store.insertOrder(db, {
      no: newNo,
      status: S.next('NEW', 'SUBMIT'),
      version: 1,
      building: o.building, room: o.room, floor: o.floor,
      student_id: o.student.id, student_name: o.student.name,
      category: o.category, level: newLevel, level_source: 'ai',
      desc: o.desc, window: o.window,
      photos_before: o.photosBefore, photos_after: [],
      ai_confidence: o.ai.confidence,
      ai_basis: `继承原单依据（返修单不重新判）：${o.ai.basis || ''}`,
      ai_fallback: 0,
      rework_of: base, rework_index: index,
      created_at: now, status_at: now
    });
    store.insertEvent(db, {
      no: newNo, at: now, event: 'SUBMIT',
      actorType: 'system', actorId: 'system', actorName: '返修单', manual: 0,
      note: `由 ${o.no} 第 ${index} 次返修生成，紧急度升一档（${o.level}→${newLevel}）`,
      payload: { reworkOf: base, reworkIndex: index, levelFrom: o.level, levelTo: newLevel }
    });

    // 原单返修次数 +1（返修链统一记在根单上）。
    // 注意必须重新读一遍：apply() 已经把 version 加了 1，拿旧对象去 CAS 会静默失败。
    const root = store.getOrder(db, base);
    if (root) {
      store.updateOrderCas(db, root.no, root.version, { rework_count: (root.reworkCount || 0) + 1 });
    }

    return { no: o.no, status: 'REWORKING', reworkOrder: newNo, reworkNo: newNo, reworkIndex: index };
  });
}

/** 10 AUTO_ACCEPT 超时自动验收（系统） */
function autoAccept(db, actor, no) {
  S.assertCanTrigger('AUTO_ACCEPT', actor);
  const o = load(db, no);
  const cfg = store.getConfig(db);
  const start = util.parse(o.finishAt);
  const deadline = start ? start + cfg.verifyHours * util.HOUR : null;
  if (o.status !== 'VERIFYING') S.next(o.status, 'AUTO_ACCEPT');
  if (deadline == null || store.nowMs(db) < deadline) {
    throw new ApiError(422, `还没到自动验收时间（还差 ${util.human(deadline - store.nowMs(db))}）`, {
      deadline: util.fmt(deadline)
    });
  }
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'AUTO_ACCEPT', actor,
      note: `学生 ${cfg.verifyHours} 小时未响应，系统自动验收`,
      payload: { hours: cfg.verifyHours },
      patch: { sign_at: store.nowStr(db) }
    });
    return { no: o.no, status: 'DONE', manual: 0 };
  });
}

/** 11 ARCHIVE 归档（系统） */
function archive(db, actor, no) {
  S.assertCanTrigger('ARCHIVE', actor);
  const o = load(db, no);
  const cfg = store.getConfig(db);
  const start = util.parse(o.signAt);
  const deadline = start ? start + cfg.archiveDays * util.DAY : null;
  if (o.status !== 'DONE') S.next(o.status, 'ARCHIVE');
  if (deadline == null || store.nowMs(db) < deadline) {
    throw new ApiError(422, `还没到归档时间（还差 ${util.human(deadline - store.nowMs(db))}）`, {
      deadline: util.fmt(deadline)
    });
  }
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'ARCHIVE', actor,
      note: `签字满 ${cfg.archiveDays} 天，自动归档（转只读）`,
      payload: { days: cfg.archiveDays },
      patch: { closed_at: store.nowStr(db) }
    });
    return { no: o.no, status: 'CLOSED' };
  });
}

/** 12 CANCEL 撤销 */
function cancel(db, actor, no) {
  S.assertCanTrigger('CANCEL', actor);
  const o = load(db, no);
  ownCheck(o, actor, '只能撤自己的单');
  if (o.arriveAt) {
    throw new ApiError(409, '师傅已进楼，不能撤销；请提交撤销申请', {
      from: o.status, event: 'CANCEL', allowed: [], reason: 'ARRIVED'
    });
  }
  return withTx(db, () => {
    apply(db, {
      order: o, event: 'CANCEL', actor,
      note: '学生撤销报修', payload: null,
      patch: {
        worker_id: null, worker_name: null, worker_team: null,
        cancelled_at: store.nowStr(db)
      }
    });
    return { no: o.no, status: 'CANCELLED' };
  });
}

/** 非转移：申请撤销（只进例外清单，不改状态） */
function cancelRequest(db, actor, no, body = {}) {
  if (actor.type !== 'student') throw new ApiError(403, '只有学生可以申请撤销');
  const o = load(db, no);
  ownCheck(o, actor, '只能给自己的单申请撤销');
  if (!['REPAIRING', 'PARTS'].includes(o.status)) {
    throw new ApiError(409, `当前状态「${o.statusLabel}」不需要申请撤销（进楼前可直接撤）`, {
      from: o.status, event: 'CANCEL', allowed: S.allowedEvents(o.status)
    });
  }
  const reason = String(body.reason || '').trim();
  if (reason.length < 2) throw new ApiError(422, '请写一句为什么撤销');
  return withTx(db, () => {
    store.updateOrderCas(db, o.no, o.version, {
      cancel_req_at: store.nowStr(db), cancel_req_by: actor.id, cancel_req_reason: reason
    });
    store.insertEvent(db, {
      no: o.no, at: store.nowStr(db), event: 'CANCEL_REQUEST',
      actorType: actor.type, actorId: actor.id, actorName: actor.name, manual: 1,
      note: `申请撤销：${reason}（等管理员处理）`, payload: { reason }
    });
    return { no: o.no, status: o.status, pending: true };
  });
}

/** 非转移：管理员作废 —— SYS-004 没有这条边，等变更单 #001 */
function adminCancel() {
  throw new ApiError(501, '「维修中 → 已取消」不在 SYS-004 的 13 条转移里，需变更单 #001 批准后才开放', {
    changeRequest: 'CR-001'
  });
}

/* ------------------------------------------------------------------ */
/* 计时器与 DTO                                                        */
/* ------------------------------------------------------------------ */

function timers(db, o) {
  const cfg = store.getConfig(db);
  const now = store.nowMs(db);
  let t1 = null, t2 = null;

  if (o.status === 'VERIFYING') {
    const from = util.parse(o.finishAt);
    if (from) {
      const deadline = from + cfg.verifyHours * util.HOUR;
      t1 = { from: o.finishAt, deadline: util.fmt(deadline), leftMs: deadline - now, total: cfg.verifyHours * util.HOUR, label: '验收超时' };
    }
  }
  if (o.status === 'DONE') {
    const from = util.parse(o.signAt);
    if (from) {
      const deadline = from + cfg.archiveDays * util.DAY;
      t2 = { from: o.signAt, deadline: util.fmt(deadline), leftMs: deadline - now, total: cfg.archiveDays * util.DAY, label: '归档倒计时' };
    }
  }
  const stayed = util.parse(o.statusAt) ? now - util.parse(o.statusAt) : null;
  return { t1, t2, stayedMs: stayed };
}

function dto(db, o, opts = {}) {
  const out = Object.assign({}, o, { timers: timers(db, o) });
  if (opts.events) out.events = store.eventsOf(db, o.no);
  if (opts.shortDesc) out.desc = o.desc.length > 40 ? o.desc.slice(0, 40) + '…' : o.desc;
  return out;
}

function detail(db, no) {
  const o = load(db, no);
  const d = dto(db, o, { events: true });
  // 返修链：这一单的根单与全部返修单
  const root = o.reworkOf || o.no;
  d.chain = [root].concat(store.listOrders(db, { reworkOf: root }).map(x => x.no)).sort();
  return d;
}

/* ------------------------------------------------------------------ */
/* 查询                                                                */
/* ------------------------------------------------------------------ */

function myOrders(db, actor, { status = 'ACTIVE', page = 1, pageSize = 20 } = {}) {
  let filter;
  if (status === 'ACTIVE') filter = S.ACTIVE;
  else if (status === 'DONE') filter = ['DONE', 'CLOSED'];
  else filter = S.STATUS;
  const all = store.listOrders(db, { status: filter, studentId: actor.id });
  return pageOf(all.map(o => dto(db, o, { shortDesc: true })), page, pageSize);
}

function pageOf(list, page, pageSize) {
  const p = Math.max(1, Number(page) || 1);
  const size = Math.max(1, Number(pageSize) || 20);
  return { items: list.slice((p - 1) * size, p * size), page: p, pageSize: size, total: list.length };
}

function workerTodo(db, actor) {
  const mine = store.listOrders(db, { workerId: actor.id });
  const active = mine.filter(o => ['ASSIGNED', 'REPAIRING', 'PARTS'].includes(o.status));
  const byBuilding = new Map();
  for (const o of active) {
    if (!byBuilding.has(o.building)) byBuilding.set(o.building, []);
    byBuilding.get(o.building).push(o);
  }
  const buildings = [...byBuilding.entries()].map(([bid, orders]) => {
    const floors = [...new Set(orders.map(o => o.floor))].sort((a, b) => b - a);
    return {
      buildingId: bid,
      buildingName: bid === 'B7' ? '东区7号楼' : bid === 'B8' ? '东区8号楼' : bid,
      arrived: orders.some(o => o.status === 'REPAIRING'),
      orders: orders
        .sort((a, b) => (b.floor - a.floor) || String(a.createdAt).localeCompare(String(b.createdAt)))
        .map(o => dto(db, o, { shortDesc: true })),
      floors: floors.map(f => ({ floor: f, count: orders.filter(o => o.floor === f).length })),
      progress: {
        done: orders.filter(o => o.status === 'REPAIRING').length,
        total: orders.length
      }
    };
  }).sort((a, b) => b.progress.total - a.progress.total);

  return {
    buildings,
    summary: {
      todo: active.filter(o => o.status === 'ASSIGNED').length,
      repairing: active.filter(o => o.status === 'REPAIRING').length,
      parts: active.filter(o => o.status === 'PARTS').length
    }
  };
}

function buildingBoard(db, actor, buildingId) {
  const all = store.listOrders(db, { status: S.ACTIVE, building: buildingId });
  const mine = all.filter(o => !o.worker || o.worker.id === actor.id || o.status === 'PENDING');
  const floors = [...new Set(mine.map(o => o.floor))].sort((a, b) => b - a);
  return {
    buildingId,
    buildingName: buildingId === 'B7' ? '东区7号楼' : buildingId === 'B8' ? '东区8号楼' : buildingId,
    floors: floors.map(f => ({
      floor: f,
      orders: mine.filter(o => o.floor === f)
        .sort((a, b) => String(a.room).localeCompare(String(b.room)))
        .map(o => dto(db, o, { shortDesc: true }))
    })),
    emptyFloors: (buildingId === 'B7' ? [5, 4, 3, 2] : [3, 2, 1]).filter(f => !floors.includes(f))
  };
}

/** 管理端例外清单：前三类由规则实时派生，第四类来自 cancel_request */
function exceptions(db, kind = 'ALL') {
  const cfg = store.getConfig(db);
  const all = store.allOrders(db);
  const items = [];

  const push = (o, k, detail, createdAt) => items.push({
    id: `EX-${k}-${o.no}`, kind: k, kindLabel: CANCEL_KIND[k], no: o.no,
    building: o.building, room: o.room, status: o.status, statusLabel: o.statusLabel,
    detail, createdAt
  });

  for (const o of all) {
    if (['VERIFYING', 'DONE', 'CLOSED'].includes(o.status) && o.photosAfter.length === 0) {
      push(o, 'MISMATCH', `状态已是「${o.statusLabel}」，完工照 0 张（照片没落库）`, o.finishAt || o.statusAt);
    }
    if (o.parts && Number(o.parts.amount) > Number(cfg.budgetLine) && o.status === 'PARTS') {
      push(o, 'OVER_BUDGET', `${o.parts.name}　${o.parts.amount} 元 > 预算线 ${cfg.budgetLine} 元`, o.statusAt);
    }
    if ((o.reworkCount || 0) >= 2 && !o.reworkOf) {
      push(o, 'REPEAT_REWORK', `同一原单第 ${o.reworkCount} 次返修，建议查根因`, o.statusAt);
    }
    if (o.cancelRequest) {
      push(o, 'CANCEL_REQUEST', `学生申请撤销：${o.cancelRequest.reason}`, o.cancelRequest.at);
    }
  }

  items.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
  const counts = { MISMATCH: 0, OVER_BUDGET: 0, REPEAT_REWORK: 0, CANCEL_REQUEST: 0 };
  for (const it of items) counts[it.kind]++;
  const filtered = kind === 'ALL' ? items : items.filter(i => i.kind === kind);
  return { items: filtered, counts, total: items.length };
}

function dashboard(db) {
  const all = store.allOrders(db);
  const active = all.filter(o => S.ACTIVE.includes(o.status));
  const finished = all.filter(o => ['DONE', 'CLOSED'].includes(o.status));

  // 平均修复时长：到场 → 完工
  const fixed = all.filter(o => o.arriveAt && o.finishAt);
  const avgFixMinutes = fixed.length
    ? Math.round(fixed.reduce((s, o) => s + (util.parse(o.finishAt) - util.parse(o.arriveAt)) / 60000, 0) / fixed.length)
    : null;

  const reworkOrders = all.filter(o => o.reworkOf);
  // 返修率：生成过返修单的次数 ÷ 全部工单（含返修单本身），全周期口径
  const reworkRate = all.length ? +(reworkOrders.length / all.length).toFixed(2) : 0;

  // 零人工完成率（口径 A，实现采用）：已完成/已关闭里，没有任何 admin 事件的
  const noAdmin = finished.filter(o => !store.eventsOf(db, o.no).some(e => e.actorType === 'admin'));
  // 严格字面口径（全部事件 manual === 0）—— 因为 SUBMIT 必然是 manual=1，这个数结构性为 0
  const strictZero = finished.filter(o => store.eventsOf(db, o.no).every(e => e.manual === 0));

  const byBuilding = [...new Set(all.map(o => o.building))].sort().map(bid => {
    const inB = all.filter(o => o.building === bid);
    return {
      buildingId: bid,
      buildingName: bid === 'B7' ? '东区7号楼' : bid === 'B8' ? '东区8号楼' : bid,
      active: inB.filter(o => S.ACTIVE.includes(o.status)).length,
      done: inB.filter(o => ['DONE', 'CLOSED'].includes(o.status)).length,
      exceptions: exceptions(db).items.filter(i => i.building === bid).length
    };
  });

  return {
    active: active.length,
    pendingArchive: all.filter(o => o.status === 'DONE').length,
    exceptionTotal: exceptions(db).total,
    avgFixMinutes,
    reworkRate,
    zeroManual: {
      zero: noAdmin.length,
      total: finished.length,
      rate: finished.length ? +(noAdmin.length / finished.length).toFixed(2) : 0,
      basis: '已完成/已关闭里，全程没有管理员事件的'
    },
    strictZero: { zero: strictZero.length, total: finished.length, rate: 0, note: '全部事件 manual=0（含 SUBMIT 必为 1，故结构性为 0）' },
    byBuilding,
    timers: all.filter(o => o.status === 'VERIFYING' || o.status === 'DONE')
      .map(o => ({ no: o.no, room: o.room, building: o.building, status: o.status, ...timers(db, o) }))
      .filter(x => x.t1 || x.t2)
  };
}

function meta(db) {
  const cfg = store.getConfig(db);
  const workers = store.usersByRole(db, 'worker').map(w => ({ id: w.id, name: w.name, team: w.team, building: w.building }));
  return {
    brand: '宿舍维修',
    version: 'V5.0',
    statuses: S.STATUS.map(s => ({ code: s, label: S.STATUS_LABEL[s], terminal: S.TERMINAL.includes(s) })),
    events: S.EVENTS.map(e => ({ code: e, label: S.EVENT_LABEL[e], trigger: S.TRIGGER[e] })),
    transitions: Object.entries(S.TRANSITIONS).flatMap(([from, row]) =>
      Object.entries(row).map(([event, to]) => ({ from, event, to }))),
    categories: ['给排水', '电气', '电器', '门窗家具', '网络'],
    levels: ['普通', '较急', '紧急'],
    windows: WINDOWS,
    noShowReasons: Object.entries(NO_SHOW_REASONS).map(([code, label]) => ({ code, label })),
    finishTags: FINISH_TAGS,
    cancelReasons: CANCEL_REASONS,
    exceptionKinds: Object.entries(CANCEL_KIND).map(([code, label]) => ({ code, label })),
    buildings: [
      { id: 'B7', name: '东区7号楼', floors: [2, 3, 4, 5] },
      { id: 'B8', name: '东区8号楼', floors: [1, 2, 3] }
    ],
    workers,
    accounts: store.listUsers(db).map(u => ({
      id: u.id, role: u.role, name: u.name, building: u.building, room: u.room, team: u.team
    })),
    budgetLine: Number(cfg.budgetLine),
    verifyHours: Number(cfg.verifyHours),
    archiveDays: Number(cfg.archiveDays)
  };
}

module.exports = {
  submit, assign, assignBuilding, arrive, arriveBuilding, noShow, finish, needParts,
  partsArrived, accept, reject, autoAccept, archive, cancel, cancelRequest, adminCancel,
  detail, myOrders, workerTodo, buildingBoard, exceptions, dashboard, meta, timers, dto,
  withTx, load, WINDOWS, NO_SHOW_REASONS, FINISH_TAGS, CANCEL_KIND
};
