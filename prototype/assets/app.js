/* ============================================================
 * 极薄的运行层：状态机（表驱动）+ 数据存储 + 计时器 + 小工具
 * 口径来源：SYS-004（9 状态 / 13 转移 / 非法一律 409）
 * 页面只管画，改状态一律走 App.fire()，不允许直接写 status。
 * ============================================================ */

window.App = (function () {
  'use strict';

  /* ---------------- 状态与事件（9 + 12） ---------------- */
  const STATUS = ['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS', 'VERIFYING', 'REWORKING', 'DONE', 'CLOSED', 'CANCELLED'];

  const STATUS_LABEL = {
    NEW: '（新建）', PENDING: '待派工', ASSIGNED: '已派工', REPAIRING: '维修中', PARTS: '待配件',
    VERIFYING: '待验收', REWORKING: '返修中', DONE: '已完成', CLOSED: '已关闭', CANCELLED: '已取消'
  };

  const EVENT_LABEL = {
    SUBMIT: '提交报修', ASSIGN: '系统派工', ARRIVE: '到门打卡', NO_SHOW: '到访未遇',
    FINISH: '完工提交', NEED_PARTS: '登记缺件', PARTS_ARRIVED: '配件到货',
    ACCEPT: '学生验收', REJECT: '验收不通过', AUTO_ACCEPT: '超时自动验收',
    ARCHIVE: '系统归档', CANCEL: '学生撤销'
  };

  /* ---------------- 转移表：13 条合法转移 ---------------- */
  /* NEW 不是状态，是"还没有工单"这个入口；SUBMIT 是第 1 条转移 */
  const TRANSITIONS = {
    NEW:       { SUBMIT: 'PENDING' },
    PENDING:   { ASSIGN: 'ASSIGNED', CANCEL: 'CANCELLED' },
    ASSIGNED:  { ARRIVE: 'REPAIRING', 'NO_SHOW': 'PENDING', CANCEL: 'CANCELLED' },
    REPAIRING: { FINISH: 'VERIFYING', 'NEED_PARTS': 'PARTS' },
    PARTS:     { 'PARTS_ARRIVED': 'PENDING' },
    VERIFYING: { ACCEPT: 'DONE', REJECT: 'REWORKING', 'AUTO_ACCEPT': 'DONE' },
    REWORKING: {},
    DONE:      { ARCHIVE: 'CLOSED' },
    CLOSED:    {},
    CANCELLED: {}
  };

  /* 每条转移的触发方（谁有权发这个事件） */
  const TRIGGER = {
    SUBMIT: ['student'], ASSIGN: ['system'], ARRIVE: ['worker'], 'NO_SHOW': ['worker'],
    FINISH: ['worker'], 'NEED_PARTS': ['worker'], 'PARTS_ARRIVED': ['system', 'admin'],
    ACCEPT: ['student'], REJECT: ['student'], 'AUTO_ACCEPT': ['system'],
    ARCHIVE: ['system'], CANCEL: ['student']
  };

  const HOUR = 3600 * 1000, DAY = 24 * HOUR;

  /* ---------------- 409 ---------------- */
  function Reject(code, message, extra) {
    const e = new Error(message);
    e.code = code; e.extra = extra || {};
    return e;
  }

  /* 纯函数：不看数据，只看表。非法转移一律抛 409 */
  function next(from, event) {
    const row = TRANSITIONS[from];
    if (!row) throw Reject(409, `未知状态 ${from}`);
    if (!Object.prototype.hasOwnProperty.call(row, event)) {
      const allowed = Object.keys(row);
      throw Reject(409, `状态「${STATUS_LABEL[from] || from}」不接受事件「${EVENT_LABEL[event] || event}」`,
        { from, event, allowed });
    }
    return row[event];
  }

  function can(from, event) {
    const row = TRANSITIONS[from];
    return !!(row && Object.prototype.hasOwnProperty.call(row, event));
  }

  function allowedEvents(from) { return Object.keys(TRANSITIONS[from] || {}); }

  /* ---------------- 存储（localStorage + seed） ---------------- */
  const KEY = 'dorm-repair-prototype-v5';

  function clone(o) { return JSON.parse(JSON.stringify(o)); }

  function fresh() {
    const d = clone(window.SEED);
    d.simNow = null;        // 演示时间加速：设成 ISO 字符串即"快进"
    d.session = { role: 'student', name: '张同学', workerId: 'W0428', isRework: false };
    return d;
  }

  function load() {
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) { /* 隐私模式等，忽略 */ }
    const d = fresh();
    save(d);
    return d;
  }

  let DB = null;
  function db() { if (!DB) DB = load(); return DB; }
  function save(d) {
    DB = d || DB;
    try { localStorage.setItem(KEY, JSON.stringify(DB)); } catch (e) { }
  }
  function reset() { DB = fresh(); save(DB); tick(true); return DB; }

  /* ---------------- 时间 ---------------- */
  function now() {
    const n = db().simNow;
    return n ? new Date(n) : new Date();
  }
  function advance(ms) {
    db().simNow = new Date(now().getTime() + ms).toISOString();
    save();
    tick(true);
  }
  function setNow(iso) { db().simNow = iso || null; save(); tick(true); }
  function realToday() { return db().simNow ? now().toISOString().slice(0, 10) : new Date().toISOString().slice(0, 10); }

  function parse(t) {
    if (!t) return null;
    // '2026-11-17 09:12' 这类本地时间写法
    const s = String(t).replace(' ', 'T');
    const d = new Date(s.length <= 16 ? s + ':00' : s);
    return isNaN(d.getTime()) ? new Date(t) : d;
  }
  function fmt(t) {
    if (!t) return '';
    if (typeof t === 'string') return t;
    const p = n => String(n).padStart(2, '0');
    return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())} ${p(t.getHours())}:${p(t.getMinutes())}`;
  }
  function fmtShort(t) { const s = fmt(t); return s ? s.slice(5) : ''; }
  function human(ms) {
    if (ms <= 0) return '已超时';
    const h = Math.floor(ms / HOUR), m = Math.floor((ms % HOUR) / 60000);
    if (h >= 24) { const d = Math.floor(h / 24); return `${d} 天 ${h % 24} 小时`; }
    if (h > 0) return `${h} 小时 ${m} 分`;
    return `${Math.max(1, m)} 分钟`;
  }

  /* ---------------- 计时器 ---------------- */
  /* T1：待验收超时。起点 finishAt，满 168 小时系统自动验收 */
  function t1(o) {
    if (o.status !== 'VERIFYING') return null;
    const from = parse(o.finishAt || o.statusAt);
    if (!from) return null;
    const deadline = new Date(from.getTime() + db().meta.verifyHours * HOUR);
    return { from, deadline, left: deadline - now(), total: db().meta.verifyHours * HOUR };
  }
  /* T2：已完成归档。起点 signAt，满 7 天转已关闭（只读） */
  function t2(o) {
    if (o.status !== 'DONE') return null;
    const from = parse(o.signAt || o.statusAt);
    if (!from) return null;
    const deadline = new Date(from.getTime() + db().meta.archiveDays * DAY);
    return { from, deadline, left: deadline - now(), total: db().meta.archiveDays * DAY };
  }

  /* 把到点的自动流转补上：VERIFYING 超时 → DONE；DONE 满 7 天 → CLOSED */
  function tick(silent) {
    const list = db().orders;
    let changed = false;
    list.forEach(o => {
      const a = t1(o);
      if (a && a.left <= 0) {
        try { fire(o.no, 'AUTO_ACCEPT', 'system', '待验收满 168 小时，系统自动确认', { silent: true }); changed = true; } catch (e) { }
      }
      const b = t2(o);
      if (b && b.left <= 0) {
        try { fire(o.no, 'ARCHIVE', 'system', '已完成满 7 天，系统归档', { silent: true }); changed = true; } catch (e) { }
      }
    });
    if (changed && !silent) save();
    return changed;
  }

  /* ---------------- 查询 ---------------- */
  function orders(filter) {
    let list = db().orders.slice();
    if (filter && filter.status) {
      const s = [].concat(filter.status);
      list = list.filter(o => s.indexOf(o.status) >= 0);
    }
    if (filter && filter.building) list = list.filter(o => o.building === filter.building);
    if (filter && filter.workerId) list = list.filter(o => o.workerId === filter.workerId);
    return list;
  }
  function order(no) { return db().orders.find(o => o.no === no) || null; }
  function worker(id) { return db().workers.find(w => w.id === id) || null; }
  function building(id) { return db().buildings.find(b => b.id === id) || null; }
  function roomLabel(o) { const b = building(o.building); return `${b ? b.name : o.building} ${o.room}`; }

  /* ---------------- 事件 ---------------- */
  function actorLabel(actor) {
    const [kind, name] = String(actor).split(':');
    return kind === 'system' ? '系统' : (kind === 'worker' ? `${name}（师傅）` : kind === 'admin' ? `${name}（管理端）` : `${name}（学生）`);
  }
  function actorKind(actor) { return String(actor).split(':')[0]; }

  /**
   * 改状态的唯一入口。
   * @returns {ok:true, from, to, order} 或抛 409（code=409）
   */
  function fire(no, event, actor, note, opt) {
    opt = opt || {};
    const o = order(no);
    if (!o) throw Reject(404, `工单 ${no} 不存在`);

    // 触发方校验
    const kinds = TRIGGER[event] || [];
    const kind = actorKind(actor);
    if (kinds.length && kinds.indexOf(kind) < 0) {
      throw Reject(409, `事件「${EVENT_LABEL[event]}」只能由 ${kinds.map(k => ({ system: '系统', student: '学生', worker: '师傅', admin: '管理员' }[k])).join('/')} 触发`);
    }
    // 单级 ARRIVE：必须属于本单师傅
    if (event === 'ARRIVE' && o.workerId && actorKind(actor) === 'worker' && actor.split(':')[1] !== (worker(o.workerId) || {}).name) {
      throw Reject(409, '该工单不属于此师傅，不能打卡');
    }
    // 撤销只能到进楼打卡之前
    if (event === 'CANCEL' && o.arriveAt) {
      throw Reject(409, '师傅已进楼，不能撤销；请提交撤销申请，由管理端处理');
    }

    const to = next(o.status, event);          // 非法转移在这里抛 409
    const from = o.status;
    const at = fmt(now());

    o.status = to;
    o.statusAt = at;
    (o.events = o.events || []).push({
      at, event, actor,
      manual: kind === 'system' ? 0 : 1,
      note: note || ''
    });

    // 各事件的副作用
    if (event === 'ASSIGN') o.assignedAt = at;
    if (event === 'ARRIVE') o.arriveAt = at;
    if (event === 'NO_SHOW') { o.workerId = null; o.assignedAt = null; }
    if (event === 'FINISH') o.finishAt = at;
    if (event === 'ACCEPT' || event === 'AUTO_ACCEPT') o.signAt = at;
    if (event === 'ARCHIVE') o.closedAt = at;
    if (event === 'CANCEL') { o.cancelledAt = at; o.workerId = null; }
    if (event === 'REJECT') {
      o.reworkCount = (o.reworkCount || 0) + 1;
      // 返修单是新单：原单留 RETWORKING，新单 -R{n} 入队
      const idx = o.reworkCount;
      const childNo = `${o.no}-R${idx}`;
      if (!order(childNo)) {
        db().orders.push({
          no: childNo, status: 'PENDING', building: o.building, room: o.room,
          category: o.category,
          level: o.level === '普通' ? '较急' : '紧急',   // 紧急度升一档
          desc: o.desc + '（返修单，继承原单照片与位置）',
          photosBefore: o.photosBefore || 1, window: o.window || '全天',
          createdAt: at, reworkOf: o.no, reworkIndex: idx,
          aiConfidence: o.aiConfidence, aiBasis: `继承原单 AI 依据，紧急度升一档：${o.level} → ${o.level === '普通' ? '较急' : '紧急'}`,
          aiCategory: o.category, aiLevel: o.level === '普通' ? '较急' : '紧急',
          workerId: null, manual: 1,
          events: [{ at, event: 'SUBMIT', actor: 'system', manual: 1, note: `由 ${o.no} 的 REJECT 生成，rework_of=${o.no}` }]
        });
      }
    }
    if (event === 'PARTS_ARRIVED') o.parts = Object.assign({ arrivedAt: at }, o.parts || {});

    save();
    if (!opt.silent) tick();
    return { ok: true, from, to, order: o };
  }

  /* ---------------- 新建工单（SUBMIT 是第 1 条转移，照样走表） ---------------- */
  function nextNo() {
    const day = fmt(now()).slice(0, 10).replace(/-/g, '');
    const prefix = 'BX' + day;
    const used = db().orders
      .map(o => o.no)
      .filter(no => no.indexOf(prefix) === 0)
      .map(no => parseInt(no.slice(prefix.length, prefix.length + 3), 10) || 0);
    const n = (used.length ? Math.max.apply(null, used) : 0) + 1;
    return prefix + String(n).padStart(3, '0');
  }

  /**
   * 学生提交报修。前置条件写死在这里：照片 ≥1、描述 ≥4 字，缺一不可。
   * 紧急程度不由学生填，由 AI 定级（演示里是关键词规则）。
   */
  function create(payload) {
    const p = payload || {};
    if (!(p.photosBefore > 0)) throw Reject(400, '现场照片至少 1 张');
    const desc = String(p.desc || '').trim();
    if (desc.length < 4) throw Reject(400, '描述至少 4 个字');

    const to = next('NEW', 'SUBMIT');        // 走转移表，不在页面里写 PENDING
    const at = fmt(now());
    const no = nextNo();
    const ai = p.ai || judge(desc, p.category);

    const o = {
      no, status: to, building: p.building || 'B7', room: p.room || '402',
      category: ai.category,
      level: ai.level,
      desc,
      photosBefore: p.photosBefore, window: p.window || '全天',
      createdAt: at,
      aiConfidence: ai.confidence, aiBasis: ai.basis,
      aiCategory: ai.category, aiLevel: ai.level,
      workerId: null, manual: 1,
      events: [{
        at, event: 'SUBMIT', actor: 'student:' + (p.studentName || '张同学'), manual: 1,
        note: `照片 ${p.photosBefore} 张、描述 ${desc.length} 字，时段 ${p.window || '全天'}`
      }]
    };
    db().orders.unshift(o);
    save();
    return o;
  }

  /* 演示用"AI 定级"：五类三档 + 安全类优先 + 低置信保守升一档 + 依据留痕 */
  const RULES = [
    { cat: '电气', words: ['冒烟', '焦味', '打火', '漏电', '触电', '短路'], level: '紧急', safety: true },
    { cat: '给排水', words: ['爆管', '喷水', '漏水到楼下', '水漫', '快接满', '接满了', '快满', '漫出来', '淹'], level: '紧急', safety: true },
    { cat: '给排水', words: ['滴水', '渗水', '返水', '堵', '水龙头', '地漏', '软管'], level: '较急', safety: false },
    { cat: '电气', words: ['插座', '没电', '跳闸', '灯', '开关'], level: '较急', safety: false },
    { cat: '电器', words: ['空调', '洗衣机', '热水器', '冰箱', '不制冷', '晃动'], level: '普通', safety: false },
    { cat: '门窗家具', words: ['门', '窗', '衣柜', '合页', '锁', '床板', '抽屉'], level: '普通', safety: false },
    { cat: '网络', words: ['网', '网口', 'wifi', 'WiFi', '断网'], level: '普通', safety: false }
  ];
  function judge(desc, fallbackCat) {
    let hit = null, safe = false;
    RULES.forEach(r => {
      if (hit) return;
      if (r.words.some(w => desc.indexOf(w) >= 0)) { hit = r; safe = r.safety; }
    });
    const category = fallbackCat || (hit ? hit.cat : '给排水');
    let level = hit ? hit.level : '普通';
    const confidence = hit ? (hit.words.length >= 4 ? 0.96 : 0.88) : 0.62;
    // 置信度低于 0.7 → 保守升一档，绝不因为不确定就压成普通
    let basis = hit
      ? `命中关键词「${hit.words.filter(w => desc.indexOf(w) >= 0).join('/')}」，归${category}`
      : '未命中关键词，按保守策略定级';
    if (confidence < 0.7) {
      const up = { '普通': '较急', '较急': '紧急', '紧急': '紧急' };
      basis += `；置信度 ${confidence.toFixed(2)} < 0.70，保守升一档：${level} → ${up[level]}`;
      level = up[level];
    }
    if (safe) basis += '；命中安全类，直接定紧急，不再往下判';
    return { category, level, confidence, basis };
  }

  /* ---------------- 进楼打卡：一次点击 → N 条单级 ARRIVE ---------------- */
  function arriveBatch(buildingId, workerId, workerName) {
    const list = db().orders.filter(o =>
      o.building === buildingId && o.workerId === workerId && o.status === 'ASSIGNED');
    const done = [], failed = [];
    list.forEach(o => {
      try {
        fire(o.no, 'ARRIVE', 'worker:' + workerName, '进楼打卡（本批同时到场）', { silent: true });
        done.push(o.no);
      } catch (e) { failed.push({ no: o.no, msg: e.message }); }
    });
    save();
    return { done, failed };
  }

  /* ---------------- 自动派工（系统动作，可以手动点一下演示） ---------------- */
  function assignPending(buildingId) {
    const list = db().orders.filter(o => o.status === 'PENDING' && (!buildingId || o.building === buildingId));
    const done = [];
    list.forEach(o => {
      const w = db().workers.find(x => (x.buildings || []).indexOf(o.building) >= 0);
      if (!w) return;
      try {
        fire(o.no, 'ASSIGN', 'system', `本栋当班师傅：${w.name}`, { silent: true });
        o.workerId = w.id; save();
        done.push(o.no);
      } catch (e) { }
    });
    return done;
  }

  /* ---------------- 指标 ---------------- */
  function zeroManualRate() {
    const finished = db().orders.filter(o => o.status === 'DONE' || o.status === 'CLOSED');
    if (!finished.length) return { rate: 0, zero: 0, total: 0 };
    const zero = finished.filter(o => (o.events || []).every(e => e.manual === 0)).length;
    return { rate: zero / finished.length, zero, total: finished.length };
  }
  function exceptions() {
    const out = [];
    db().orders.forEach(o => {
      // ① 数据对不上：待验收/已完成但完工照为 0
      if ((o.status === 'VERIFYING' || o.status === 'DONE' || o.status === 'CLOSED') && !(o.photosAfter > 0)) {
        out.push({ kind: '数据对不上', no: o.no, detail: `完工照 0 张，但状态已是「${STATUS_LABEL[o.status]}」`, order: o });
      }
      // ② 超预算采购
      if (o.parts && o.parts.amount > db().meta.budget && o.status !== 'CLOSED' && o.status !== 'CANCELLED') {
        out.push({ kind: '超预算采购', no: o.no, detail: `${o.parts.name} ${o.parts.amount} 元 > 预算线 ${db().meta.budget} 元`, order: o });
      }
      // ③ 同一单反复返修
      if ((o.reworkCount || 0) >= 2) {
        out.push({ kind: '反复返修', no: o.no, detail: `已返修 ${o.reworkCount} 次，需人工判断`, order: o });
      }
      // ④ 学生申请撤销（唯一由管理员决定、且能改状态的入口）
      if (o.cancelRequest && o.status === 'REPAIRING') {
        out.push({ kind: '学生申请撤销', no: o.no, detail: o.cancelRequest.reason || '师傅已进楼，学生申请作废或补偿', order: o });
      }
    });
    return out;
  }

  /* ---------------- 小工具 ---------------- */
  function el(sel, root) { return (root || document).querySelector(sel); }
  function els(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function pill(status) { return `<span class="pill" data-status="${status}">${STATUS_LABEL[status] || status}</span>`; }
  /* 紧急程度用的是另一套色板，别和状态胶囊混用 */
  function levelPill(level) {
    const tone = { '紧急': 'danger', '较急': 'warn', '普通': 'muted' }[level] || 'muted';
    return `<span class="pill" data-tone="${tone}">${level}</span>`;
  }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }
  function qs(name, fallback) {
    const m = new RegExp('[?&]' + name + '=([^&]*)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : fallback;
  }
  function go(url) { location.href = url; }

  let toastTimer = null;
  function toast(msg, tone) {
    let t = document.querySelector('.toast');
    if (!t) {
      t = document.createElement('div');
      t.className = 'toast';
      document.body.appendChild(t);
    }
    t.textContent = msg;
    t.setAttribute('data-tone', tone || '');
    t.classList.add('is-on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove('is-on'), 2200);
  }

  return {
    STATUS, STATUS_LABEL, EVENT_LABEL, TRANSITIONS, TRIGGER,
    toast,
    next, can, allowedEvents, Reject,
    db, save, reset, load,
    now, advance, setNow, fmt, fmtShort, human, parse, realToday,
    t1, t2, tick,
    orders, order, worker, building, roomLabel,
    fire, create, judge, nextNo, arriveBatch, assignPending, actorLabel, actorKind,
    zeroManualRate, exceptions,
    el, els, pill, levelPill, esc, qs, go
  };
})();
