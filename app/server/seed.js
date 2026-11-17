'use strict';
/**
 * 演示种子数据：13 张工单覆盖全部 9 个状态，外加一条返修链（-R1 / -R2）。
 *
 * 时间全部相对"现在"生成 —— 无论哪天启动，计时器都是活的（T1 还剩 164 小时、T2 还剩 6 天 22 小时）。
 * 单号按当天日期生成，看起来像真单：BX20261118001。
 */

const db = require('./db');
const { fmt, fmtDay, fmtDate, makeNo, makeReworkNo, HOUR, DAY } = require('./util');

const USERS = [
  { id: 'S001', role: 'student', name: '张同学', building: 'B7', room: '402', phone: '138****0412' },
  { id: 'S002', role: 'student', name: '陈同学', building: 'B7', room: '311' },
  { id: 'S003', role: 'student', name: '林同学', building: 'B7', room: '508' },
  { id: 'S004', role: 'student', name: '赵同学', building: 'B7', room: '217' },
  { id: 'S006', role: 'student', name: '周同学', building: 'B7', room: '410' },
  { id: 'S007', role: 'student', name: '吴同学', building: 'B8', room: '105' },
  { id: 'S008', role: 'student', name: '郑同学', building: 'B8', room: '310' },
  { id: 'S010', role: 'student', name: '冯同学', building: 'B8', room: '205' },
  { id: 'W0428', role: 'worker', name: '李建国', team: '水电班', building: 'B7' },
  { id: 'W0512', role: 'worker', name: '王海涛', team: '综合班', building: 'B8' },
  { id: 'A001', role: 'admin', name: '王主任', team: '后勤维修部' }
];

const BUILDINGS = [
  { id: 'B7', name: '东区7号楼', floors: [2, 3, 4, 5] },
  { id: 'B8', name: '东区8号楼', floors: [1, 2, 3] }
];

/** 一条种子工单 = 一行 orders + 一串 events（事件顺序即时间顺序） */
function order(o, at, event, actorType, actorId, actorName, note, extra) {
  return {
    at, event, actorType, actorId, actorName,
    manual: actorType === 'system' ? 0 : 1,
    note: note || '', payload: extra || null
  };
}

function build(now) {
  const day = fmtDay(now);
  const T = (msAgo) => fmt(now - msAgo);
const D = (msAhead) => fmtDate(now + msAhead);
  let seq = 0;
  const n = () => makeNo(day, ++seq);

  const o1 = n(); // 待派工 · 安全类紧急
  const o2 = n(); // 已派工 · 没去打卡
  const o3 = n(); // 维修中 · 学生已申请撤销（第四类例外）
  const o4 = n(); // 待配件 · 预算内
  const o5 = n(); // 待验收 · 完工照 0 张（数据对不上）
  const o6 = n(); // 待配件 · 超预算待批
  const o7 = n(); // 返修中 · 第 2 次（反复返修）
  const o8 = n(); // 已完成 · T2 倒计时
  const o9 = n(); // 已关闭
  const o10 = n(); // 已取消
  const o11 = n(); // 已完成 · 超时自动验收（零人工样本）
  const o12 = n(); // 待派工 · 8 号楼

  const orders = [
    {
      no: o1, status: 'PENDING', building: 'B7', room: '402', floor: 4,
      student: ['S001', '张同学'], category: '给排水', level: '紧急',
      desc: '洗手盆下面的软管一直滴水，水桶快接满了', window: '晚上 18:00-22:00',
      photosBefore: ['/img/p-b7-402-pipe.jpg'],
      aiBasis: '命中「滴水/软管」，归给排水；描述提到「快接满了」，按浸水风险算安全类，直接定紧急，不再往下判',
      aiConfidence: 0.94,
      createdAt: T(15 * HOUR),
      events: [order(null, T(15 * HOUR), 'SUBMIT', 'student', 'S001', '张同学', '提交报修')]
    },
    {
      no: o2, status: 'ASSIGNED', building: 'B7', room: '311', floor: 3,
      student: ['S002', '陈同学'], category: '电气', level: '较急',
      desc: '书桌上方的灯管一闪一闪，晚上没法看书写作业', window: '上午 08:00-12:00',
      photosBefore: ['/img/p-b7-311-light.jpg'],
      aiBasis: '命中「灯管」，归电气；提到「没法看书」，影响基本使用，定较急',
      aiConfidence: 0.8, worker: ['W0428', '李建国', '水电班'],
      createdAt: T(16 * HOUR), assignedAt: T(15.8 * HOUR),
      events: [
        order(null, T(16 * HOUR), 'SUBMIT', 'student', 'S002', '陈同学', '提交报修'),
        order('PENDING', T(15.8 * HOUR), 'ASSIGN', 'system', 'system', '派工引擎', '本楼栋当班：李建国')
      ]
    },
    {
      no: o3, status: 'REPAIRING', building: 'B7', room: '508', floor: 5,
      student: ['S003', '林同学'], category: '门窗家具', level: '普通',
      desc: '衣柜门合页松了，门关不上，衣服总是掉出来', window: '全天（08:00-22:00）',
      photosBefore: ['/img/p-b7-508-door.jpg'],
      aiBasis: '命中「衣柜/合页」，归门窗家具；不影响基本生活，定普通',
      aiConfidence: 0.72, worker: ['W0428', '李建国', '水电班'],
      createdAt: T(20 * HOUR), assignedAt: T(19.5 * HOUR), arriveAt: T(2 * HOUR),
      cancelReqAt: T(1 * HOUR), cancelReqBy: 'S003',
      cancelReqReason: '临时离校，这周都不在，先不修了',
      events: [
        order(null, T(20 * HOUR), 'SUBMIT', 'student', 'S003', '林同学', '提交报修'),
        order('PENDING', T(19.5 * HOUR), 'ASSIGN', 'system', 'system', '派工引擎', '本楼栋当班：李建国'),
        order('ASSIGNED', T(2 * HOUR), 'ARRIVE', 'worker', 'W0428', '李建国', '进楼打卡 09:45')
      ]
    },
    {
      no: o4, status: 'PARTS', building: 'B7', room: '217', floor: 2,
      student: ['S004', '赵同学'], category: '给排水', level: '普通',
      desc: '花洒头堵了，水从旁边分叉出来，洗澡只能歪着站', window: '下午 14:00-18:00',
      photosBefore: ['/img/p-b7-217-shower.jpg'],
      aiBasis: '命中「花洒」，归给排水；不影响基本生活，定普通',
      aiConfidence: 0.72, worker: ['W0428', '李建国', '水电班'],
      createdAt: T(26 * HOUR), assignedAt: T(25 * HOUR), arriveAt: T(24 * HOUR),
      parts: ['花洒头', 38, D(2 * DAY), 'ORDERED'],
      events: [
        order(null, T(26 * HOUR), 'SUBMIT', 'student', 'S004', '赵同学', '提交报修'),
        order('PENDING', T(25 * HOUR), 'ASSIGN', 'system', 'system', '派工引擎', '本楼栋当班：李建国'),
        order('ASSIGNED', T(24 * HOUR), 'ARRIVE', 'worker', 'W0428', '李建国', ''),
        order('REPAIRING', T(23.6 * HOUR), 'NEED_PARTS', 'worker', 'W0428', '李建国',
          '缺件：花洒头 38 元，预算内自动下单', { amount: 38, overBudget: false })
      ]
    },
    {
      no: o5, status: 'VERIFYING', building: 'B7', room: '402', floor: 4,
      student: ['S001', '张同学'], category: '电器', level: '普通',
      desc: '空调遥控器没反应，换了电池也不行，开机不了', window: '晚上 18:00-22:00',
      photosBefore: ['/img/p-b7-402-ac.jpg'],
      photosAfter: [], // ← 故意留空：这就是"数据对不上"样本（状态推进了但完工照 0 张）
      aiBasis: '命中「空调」，归电器；不影响基本生活，定普通',
      aiConfidence: 0.76, worker: ['W0428', '李建国', '水电班'],
      createdAt: T(30 * HOUR), assignedAt: T(29 * HOUR), arriveAt: T(4.75 * HOUR),
      finishAt: T(4 * HOUR),
      events: [
        order(null, T(30 * HOUR), 'SUBMIT', 'student', 'S001', '张同学', '提交报修'),
        order('PENDING', T(29 * HOUR), 'ASSIGN', 'system', 'system', '派工引擎', '本楼栋当班：李建国'),
        order('ASSIGNED', T(4.75 * HOUR), 'ARRIVE', 'worker', 'W0428', '李建国', ''),
        order('REPAIRING', T(4 * HOUR), 'FINISH', 'worker', 'W0428', '李建国', '更换遥控器电池仓弹簧')
      ]
    },
    {
      no: o6, status: 'PARTS', building: 'B7', room: '217', floor: 2,
      student: ['S004', '赵同学'], category: '电器', level: '较急',
      desc: '洗衣机甩干的时候整台机器乱跳，门都撞开了', window: '下午 14:00-18:00',
      photosBefore: ['/img/p-b7-217-washer.jpg'],
      aiBasis: '命中「洗衣机」，归电器；提到「门都撞开了」，影响使用且有风险，定较急',
      aiConfidence: 0.8, worker: ['W0428', '李建国', '水电班'],
      createdAt: T(3 * DAY), assignedAt: T(3 * DAY - HOUR), arriveAt: T(2.8 * DAY),
      parts: ['滚筒减震器 ×4', 480, D(4 * DAY), 'PENDING_APPROVAL'],
      events: [
        order(null, T(3 * DAY), 'SUBMIT', 'student', 'S004', '赵同学', '提交报修'),
        order('PENDING', T(3 * DAY - HOUR), 'ASSIGN', 'system', 'system', '派工引擎', '本楼栋当班：李建国'),
        order('ASSIGNED', T(2.8 * DAY), 'ARRIVE', 'worker', 'W0428', '李建国', ''),
        order('REPAIRING', T(2.5 * DAY), 'NEED_PARTS', 'worker', 'W0428', '李建国',
          '缺件：滚筒减震器 ×4，480 元超预算线 200 元，转管理端', { amount: 480, overBudget: true })
      ]
    },
    {
      no: o7, status: 'REWORKING', building: 'B7', room: '410', floor: 4,
      student: ['S006', '周同学'], category: '给排水', level: '紧急', reworkCount: 2,
      desc: '卫生间地漏反水，地面一直有水，怕漏到楼下', window: '全天（08:00-22:00）',
      photosBefore: ['/img/p-b7-410-floor.jpg'],
      aiBasis: '命中「地漏/反水」，归给排水；提到「漏到楼下」，按安全类直接定紧急',
      aiConfidence: 0.94, worker: ['W0428', '李建国', '水电班'],
      createdAt: T(4 * DAY), assignedAt: T(4 * DAY - HOUR), arriveAt: T(3.72 * DAY),
      finishAt: T(3.6 * DAY), signAt: null,
      events: [
        order(null, T(4 * DAY), 'SUBMIT', 'student', 'S006', '周同学', '提交报修'),
        order('PENDING', T(4 * DAY - HOUR), 'ASSIGN', 'system', 'system', '派工引擎', ''),
        order('ASSIGNED', T(3.72 * DAY), 'ARRIVE', 'worker', 'W0428', '李建国', ''),
        order('REPAIRING', T(3.7 * DAY), 'FINISH', 'worker', 'W0428', '李建国', '疏通地漏'),
        order('VERIFYING', T(3.6 * DAY), 'REJECT', 'student', 'S006', '周同学',
          '还是反水，地面还是湿的', { reason: '还是反水，地面还是湿的' })
      ]
    },
    {
      no: makeReworkNo(o7, 1), status: 'REWORKING', building: 'B7', room: '410', floor: 4,
      student: ['S006', '周同学'], category: '给排水', level: '紧急',
      desc: '卫生间地漏反水，地面一直有水，怕漏到楼下', window: '全天（08:00-22:00）',
      photosBefore: ['/img/p-b7-410-floor.jpg'],
      aiBasis: '继承原单依据：命中「地漏/反水」，按安全类定紧急（返修单继承，不重新判）',
      aiConfidence: 0.94, worker: ['W0428', '李建国', '水电班'],
      reworkOf: o7, reworkIndex: 1,
      createdAt: T(3.6 * DAY), assignedAt: T(3.5 * DAY), arriveAt: T(3.22 * DAY),
      finishAt: T(3.2 * DAY),
      events: [
        order(null, T(3.6 * DAY), 'SUBMIT', 'system', 'system', '返修单', `由 ${o7} 生成，继承位置与历史`),
        order('PENDING', T(3.5 * DAY), 'ASSIGN', 'system', 'system', '派工引擎', ''),
        order('ASSIGNED', T(3.22 * DAY), 'ARRIVE', 'worker', 'W0428', '李建国', ''),
        order('REPAIRING', T(3.3 * DAY), 'FINISH', 'worker', 'W0428', '李建国', '重新打胶'),
        order('VERIFYING', T(3.2 * DAY), 'REJECT', 'student', 'S006', '周同学',
          '第二天又渗水了', { reason: '第二天又渗水了' })
      ]
    },
    {
      no: makeReworkNo(o7, 2), status: 'PENDING', building: 'B7', room: '410', floor: 4,
      student: ['S006', '周同学'], category: '给排水', level: '紧急',
      desc: '卫生间地漏反水，地面一直有水，怕漏到楼下', window: '全天（08:00-22:00）',
      photosBefore: ['/img/p-b7-410-floor.jpg'],
      aiBasis: '继承原单依据：命中「地漏/反水」，按安全类定紧急（返修单继承，不重新判）',
      aiConfidence: 0.94,
      reworkOf: o7, reworkIndex: 2,
      createdAt: T(3.2 * DAY),
      events: [
        order(null, T(3.2 * DAY), 'SUBMIT', 'system', 'system', '返修单',
          `由 ${o7} 生成，第 2 次返修 → 进管理端「反复返修」`)
      ]
    },
    {
      no: o8, status: 'DONE', building: 'B7', room: '402', floor: 4,
      student: ['S001', '张同学'], category: '网络', level: '普通',
      desc: '桌子旁边的网口插上没反应，换了根网线也不行', window: '下午 14:00-18:00',
      photosBefore: ['/img/p-b7-402-net.jpg'], photosAfter: ['/img/a-b7-402-net.jpg'],
      aiBasis: '命中「网口/没反应」，归网络；不影响基本生活，定普通',
      aiConfidence: 0.76, worker: ['W0428', '李建国', '水电班'],
      createdAt: T(2 * DAY), assignedAt: T(2 * DAY - HOUR), arriveAt: T(6.8 * HOUR),
      finishAt: T(6 * HOUR), signAt: T(2 * HOUR),
      events: [
        order(null, T(2 * DAY), 'SUBMIT', 'student', 'S001', '张同学', '提交报修'),
        order('PENDING', T(2 * DAY - HOUR), 'ASSIGN', 'system', 'system', '派工引擎', ''),
        order('ASSIGNED', T(6.8 * HOUR), 'ARRIVE', 'worker', 'W0428', '李建国', ''),
        order('REPAIRING', T(6 * HOUR), 'FINISH', 'worker', 'W0428', '李建国', '更换网络面板模块'),
        order('VERIFYING', T(2 * HOUR), 'ACCEPT', 'student', 'S001', '张同学', '学生签字确认')
      ]
    },
    {
      no: o9, status: 'CLOSED', building: 'B8', room: '105', floor: 1,
      student: ['S007', '吴同学'], category: '门窗家具', level: '普通',
      desc: '阳台推拉门的滑轮坏了，推起来很费劲', window: '上午 08:00-12:00',
      photosBefore: ['/img/p-b8-105-slide.jpg'], photosAfter: ['/img/a-b8-105-slide.jpg'],
      aiBasis: '命中「门/滑轮」，归门窗家具；不影响基本生活，定普通',
      aiConfidence: 0.72, worker: ['W0512', '王海涛', '综合班'],
      createdAt: T(12 * DAY), assignedAt: T(12 * DAY - HOUR), arriveAt: T(11.54 * DAY),
      parts: ['推拉门滑轮组', 60, D(-1 * DAY), 'ARRIVED'],
      finishAt: T(11.5 * DAY), signAt: T(11 * DAY), closedAt: T(4 * DAY),
      events: [
        order(null, T(12 * DAY), 'SUBMIT', 'student', 'S007', '吴同学', '提交报修'),
        order('PENDING', T(12 * DAY - HOUR), 'ASSIGN', 'system', 'system', '派工引擎', ''),
        order('ASSIGNED', T(11.9 * DAY), 'ARRIVE', 'worker', 'W0512', '王海涛', ''),
        order('REPAIRING', T(11.8 * DAY), 'NEED_PARTS', 'worker', 'W0512', '王海涛',
          '缺件：推拉门滑轮组 60 元，预算内自动下单', { amount: 60, overBudget: false }),
        // ← 这一条是管理员点"确认到货"产生的：所以这单不算"零人工"，口径要能算得出来
        order('PARTS', T(11.6 * DAY), 'PARTS_ARRIVED', 'admin', 'A001', '王主任', '仓管到货，管理员确认'),
        order('PENDING', T(11.6 * DAY), 'ASSIGN', 'system', 'system', '派工引擎', ''),
        order('ASSIGNED', T(11.54 * DAY), 'ARRIVE', 'worker', 'W0512', '王海涛', ''),
        order('REPAIRING', T(11.5 * DAY), 'FINISH', 'worker', 'W0512', '王海涛', '更换滑轮组'),
        order('VERIFYING', T(11 * DAY), 'ACCEPT', 'student', 'S007', '吴同学', ''),
        order('DONE', T(4 * DAY), 'ARCHIVE', 'system', 'system', '定时任务', '签字满 7 天，自动归档')
      ]
    },
    {
      no: o10, status: 'CANCELLED', building: 'B8', room: '310', floor: 3,
      student: ['S008', '郑同学'], category: '网络', level: '普通',
      desc: '墙上网口没反应，估计是线没接好', window: '全天（08:00-22:00）',
      photosBefore: ['/img/p-b8-310-net.jpg'],
      aiBasis: '命中「网口/没反应」，归网络；不影响基本生活，定普通',
      aiConfidence: 0.76,
      createdAt: T(5 * DAY), cancelledAt: T(4.9 * DAY),
      events: [
        order(null, T(5 * DAY), 'SUBMIT', 'student', 'S008', '郑同学', '提交报修'),
        order('PENDING', T(4.9 * DAY), 'CANCEL', 'student', 'S008', '郑同学', '自己买了路由器，不用修了')
      ]
    },
    {
      no: o11, status: 'DONE', building: 'B8', room: '105', floor: 1,
      student: ['S007', '吴同学'], category: '给排水', level: '普通',
      desc: '洗手池下水慢，水要很久才排完', window: '上午 08:00-12:00',
      photosBefore: ['/img/p-b8-105-sink.jpg'], photosAfter: ['/img/a-b8-105-sink.jpg'],
      aiBasis: '命中「下水」，归给排水；不影响基本生活，定普通',
      aiConfidence: 0.72, worker: ['W0512', '王海涛', '综合班'],
      createdAt: T(9 * DAY), assignedAt: T(9 * DAY - HOUR), arriveAt: T(8.62 * DAY),
      finishAt: T(8.6 * DAY), signAt: T(3 * DAY),
      events: [
        order(null, T(9 * DAY), 'SUBMIT', 'student', 'S007', '吴同学', '提交报修'),
        order('PENDING', T(9 * DAY - HOUR), 'ASSIGN', 'system', 'system', '派工引擎', ''),
        order('ASSIGNED', T(8.62 * DAY), 'ARRIVE', 'worker', 'W0512', '王海涛', ''),
        order('REPAIRING', T(8.6 * DAY), 'FINISH', 'worker', 'W0512', '王海涛', '疏通下水'),
        // 学生 7 天没响应 → 系统自动验收（这一单是"零人工"样本：全程没有管理员碰过）
        order('VERIFYING', T(3 * DAY), 'AUTO_ACCEPT', 'system', 'system', '定时任务', '学生 7 天未响应')
      ]
    },
    {
      no: o12, status: 'PENDING', building: 'B8', room: '205', floor: 2,
      student: ['S010', '冯同学'], category: '网络', level: '较急',
      desc: '房间网口插上没反应，最近要交课程设计，急着用', window: '全天（08:00-22:00）',
      photosBefore: ['/img/p-b8-205-net.jpg'],
      aiBasis: '命中「网口/没反应」，归网络；提到「急着用」，影响使用，定较急',
      aiConfidence: 0.8,
      createdAt: T(7 * HOUR),
      events: [order(null, T(7 * HOUR), 'SUBMIT', 'student', 'S010', '冯同学', '提交报修')]
    }
  ];

  return orders;
}

/** 把 build() 的结果写进库 */
function seed(dbx, opts = {}) {
  const now = opts.now || Date.now();
  db.wipe(dbx);

  for (const u of USERS) db.insertUser(dbx, u);

  const items = build(now);
  for (const it of items) {
    const events = it.events || [];
    db.insertOrder(dbx, {
      no: it.no,
      status: it.status,
      version: events.length || 1,
      building: it.building,
      room: it.room,
      floor: it.floor,
      student_id: it.student[0],
      student_name: it.student[1],
      category: it.category,
      level: it.level,
      level_source: 'ai',
      desc: it.desc,
      window: it.window,
      worker_id: it.worker ? it.worker[0] : null,
      worker_name: it.worker ? it.worker[1] : null,
      worker_team: it.worker ? it.worker[2] : null,
      photos_before: it.photosBefore || [],
      photos_after: it.photosAfter || [],
      ai_confidence: it.aiConfidence,
      ai_basis: it.aiBasis,
      ai_fallback: 0,
      parts_name: it.parts ? it.parts[0] : null,
      parts_amount: it.parts ? it.parts[1] : null,
      parts_eta: it.parts ? it.parts[2] : null,
      parts_status: it.parts ? it.parts[3] : null,
      rework_of: it.reworkOf || null,
      rework_index: it.reworkIndex || null,
      rework_count: it.reworkCount || 0,
      no_show_count: it.noShowCount || 0,
      cancel_req_at: it.cancelReqAt || null,
      cancel_req_by: it.cancelReqBy || null,
      cancel_req_reason: it.cancelReqReason || null,
      created_at: it.createdAt,
      assigned_at: it.assignedAt || null,
      arrive_at: it.arriveAt || null,
      finish_at: it.finishAt || null,
      sign_at: it.signAt || null,
      closed_at: it.closedAt || null,
      cancelled_at: it.cancelledAt || null,
      status_at: events.length ? events[events.length - 1].at : (it.createdAt || null)
    });

    for (const e of events) db.insertEvent(dbx, { no: it.no, ...e });
  }

  // 当日序号跟上，避免新提交的单号撞车
  const day = fmtDay(now);
  const used = items.filter(i => i.no.startsWith(`BX${day}`) && !i.no.includes('-R'))
    .map(i => Number(i.no.slice(-3)));
  const max = used.length ? Math.max(...used) : 0;
  dbx.prepare('INSERT INTO seq(day, n) VALUES (?, ?) ON CONFLICT(day) DO UPDATE SET n = excluded.n').run(day, max);

  return { orders: items.length, users: USERS.length };
}

module.exports = { seed, USERS, BUILDINGS, build };
