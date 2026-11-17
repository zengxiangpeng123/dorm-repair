'use strict';
/**
 * 状态机：全项目唯一的状态真相。
 *
 * 规则（SYS-004）：9 个状态、13 条合法转移，其余一律 409。
 * 业务代码里不许出现 order.status = ...，只有这里能算出"下一个状态"。
 */

const STATUS = [
  'PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS', 'VERIFYING',
  'REWORKING', 'DONE', 'CLOSED', 'CANCELLED'
];

const STATUS_LABEL = {
  PENDING: '待派工', ASSIGNED: '已派工', REPAIRING: '维修中', PARTS: '待配件',
  VERIFYING: '待验收', REWORKING: '返修中', DONE: '已完成',
  CLOSED: '已关闭', CANCELLED: '已取消'
};

const TERMINAL = ['CLOSED', 'CANCELLED'];

/** 在办（六种），用于学生端 ACTIVE 列表与看板 */
const ACTIVE = ['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS', 'VERIFYING', 'REWORKING'];

const EVENTS = [
  'SUBMIT', 'ASSIGN', 'ARRIVE', 'NO_SHOW', 'FINISH', 'NEED_PARTS',
  'PARTS_ARRIVED', 'ACCEPT', 'REJECT', 'AUTO_ACCEPT', 'ARCHIVE', 'CANCEL'
];

const EVENT_LABEL = {
  SUBMIT: '提交报修', ASSIGN: '系统派工', ARRIVE: '到门打卡', NO_SHOW: '到访未遇',
  FINISH: '完工提交', NEED_PARTS: '登记缺件', PARTS_ARRIVED: '配件到货',
  ACCEPT: '学生验收', REJECT: '验收不通过', AUTO_ACCEPT: '超时自动验收',
  ARCHIVE: '系统归档', CANCEL: '学生撤销',
  CANCEL_REQUEST: '申请撤销',
  ORIGIN_CLOSED: '原单关闭（级联）'
};

/** 13 条合法转移。NEW 不是状态，是"还没有工单"这个入口 */
const TRANSITIONS = {
  NEW: { SUBMIT: 'PENDING' },
  PENDING: { ASSIGN: 'ASSIGNED', CANCEL: 'CANCELLED' },
  ASSIGNED: { ARRIVE: 'REPAIRING', NO_SHOW: 'PENDING', CANCEL: 'CANCELLED' },
  REPAIRING: { FINISH: 'VERIFYING', NEED_PARTS: 'PARTS' },
  PARTS: { PARTS_ARRIVED: 'PENDING' },
  VERIFYING: { ACCEPT: 'DONE', REJECT: 'REWORKING', AUTO_ACCEPT: 'DONE' },
  REWORKING: {},
  DONE: { ARCHIVE: 'CLOSED' },
  CLOSED: {},
  CANCELLED: {}
};

/**
 * 级联副作用（不占 13 条转移名额，SYS-003 §2.3）：
 * 返修单被验收通过时，原单从「返修中」直接关闭。
 */
const CASCADES = {
  ORIGIN_CLOSED: { from: 'REWORKING', to: 'CLOSED', trigger: 'system' }
};

/** 谁能发这个事件。system 只能由服务端内部调用，不对客户端开放 */
const TRIGGER = {
  SUBMIT: ['student'],
  ASSIGN: ['system'],
  ARRIVE: ['worker'],
  NO_SHOW: ['worker'],
  FINISH: ['worker'],
  NEED_PARTS: ['worker'],
  PARTS_ARRIVED: ['system', 'admin'],
  ACCEPT: ['student'],
  REJECT: ['student'],
  AUTO_ACCEPT: ['system'],
  ARCHIVE: ['system'],
  CANCEL: ['student']
};

class ApiError extends Error {
  constructor(code, message, extra) {
    super(message);
    this.code = code;          // HTTP 码：400/401/403/404/409/422/500/501
    this.extra = extra || {};  // 409 时带 from / event / allowed / reason
  }
}

/** 纯函数：只看表。非法转移抛 409，并且带上 allowed，前端据此画按钮 */
function next(from, event) {
  const row = TRANSITIONS[from];
  if (!row) throw new ApiError(409, `未知状态 ${from}`, { from, event, allowed: [] });
  if (!Object.prototype.hasOwnProperty.call(row, event)) {
    throw new ApiError(409, `状态「${STATUS_LABEL[from] || from}」不接受事件「${EVENT_LABEL[event] || event}」`, {
      from, event, allowed: Object.keys(row)
    });
  }
  return row[event];
}

function can(from, event) {
  const row = TRANSITIONS[from];
  return !!(row && Object.prototype.hasOwnProperty.call(row, event));
}

/** 级联：同样是查表，不是业务代码里手写 if */
function nextCascade(from, name) {
  const c = CASCADES[name];
  if (!c) throw new ApiError(409, `未定义的级联 ${name}`, { from, event: name, allowed: [] });
  if (c.from !== from) {
    throw new ApiError(409, `级联「${name}」不能从「${STATUS_LABEL[from] || from}」执行`, {
      from, event: name, allowed: []
    });
  }
  return c.to;
}

function allowedEvents(from) {
  return Object.keys(TRANSITIONS[from] || {});
}

function isTerminal(status) {
  return TERMINAL.includes(status);
}

/**
 * 三种写法都收：'worker' / 'worker:W0428' / { type:'worker', id:'W0428' }
 * → 一律化简成角色字符串。
 */
function actorType(actor) {
  if (actor && typeof actor === 'object') return String(actor.type || actor.role || 'system');
  return String(actor || '').split(':')[0] || 'system';
}

/** 触发方校验：谁也不能替别人发事件 */
function assertCanTrigger(event, actor) {
  const kinds = TRIGGER[event] || [];
  const kind = actorType(actor);
  if (!kinds.includes(kind)) {
    throw new ApiError(403, `角色 ${kind} 无权触发「${EVENT_LABEL[event] || event}」`, {
      event, actor: kind, allowed: kinds
    });
  }
}

/** 全部 10×12=120 种组合，给自检台用 */
function matrix() {
  const rows = ['NEW'].concat(STATUS).map(from => {
    const cells = EVENTS.map(event => {
      try { return { event, to: next(from, event), legal: true }; }
      catch (e) { return { event, to: null, legal: false }; }
    });
    return { from, cells, legal: cells.filter(c => c.legal).length };
  });
  const legal = rows.reduce((s, r) => s + r.legal, 0);
  return { rows, total: rows.length * EVENTS.length, legal };
}

module.exports = {
  STATUS, STATUS_LABEL, TERMINAL, ACTIVE, EVENTS, EVENT_LABEL,
  TRANSITIONS, TRIGGER, CASCADES, ApiError,
  next, can, allowedEvents, isTerminal, actorType, assertCanTrigger, nextCascade, matrix
};
