'use strict';
/**
 * 接口层测试（真 HTTP，不 mock）。
 *
 *   node --no-warnings --test app/server/tests/
 *   node --no-warnings app/server/tests/api.test.js
 *
 * 覆盖：鉴权/越权、一条链走完、非法转移 409（且状态不变）、乐观锁、
 * 撤销边界、返修 -R1 与原单级联关闭、缺件与预算、楼栋批量打卡、两颗计时器、501。
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, snapshot } = require('./helper');

/* ---------------- 每个用例自己起一个干净服务 ---------------- */

test('A. 元信息与鉴权', async (t) => {
  const env = await boot();
  t.after(() => env.close());

  await t.test('health 200', async () => {
    const r = await env.call('GET', '/health');
    assert.equal(r.status, 200);
    assert.equal(r.data.ok, true);
  });

  await t.test('没 token → 401', async () => {
    const r = await env.call('GET', '/admin/dashboard');
    assert.equal(r.status, 401);
    assert.equal(r.error.code, 401);
  });

  await t.test('学生调管理端接口 → 403，且带 allow', async () => {
    await env.login('student');
    const r = await env.call('GET', '/admin/dashboard', { role: 'student' });
    assert.equal(r.status, 403);
    assert.deepEqual(r.error.allow, ['admin']);
  });

  await t.test('meta 给前端的东西齐全', async () => {
    const r = await env.call('GET', '/meta');
    assert.equal(r.data.statuses.length, 9);
    assert.equal(r.data.events.length, 12);
    assert.equal(r.data.windows.length, 4);
    assert.equal(r.data.budgetLine, 200);
    assert.equal(r.data.verifyHours, 168);
    assert.equal(r.data.archiveDays, 7);
    assert.ok(r.data.accounts.length >= 8);
  });

  await t.test('状态矩阵 10 行 × 12 事件 / 13 条合法', async () => {
    const r = await env.call('GET', '/state/matrix');
    assert.equal(r.data.rows.length, 10);
    assert.equal(r.data.total, 120);
    assert.equal(r.data.legal, 13);
  });
});

test('B. 一条链走完：提交 → 派工 → 打卡 → 完工 → 验收 → 归档', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  const me = await env.login('student');

  const submit = await env.call('POST', '/orders', {
    body: { desc: '卫生间的灯一直闪，换了灯泡也没用', photoUrls: ['/img/x.jpg'], window: '上午 08:00-12:00' }
  });
  assert.equal(submit.status, 201, JSON.stringify(submit.body));
  const no = submit.data.no;
  assert.match(no, /^BX\d{11}$/);
  assert.equal(submit.data.status, 'PENDING');
  assert.equal(submit.data.building, me.user.building);
  assert.equal(submit.data.window, '上午 08:00-12:00');
  assert.ok(submit.data.ai.basis.length > 4, 'AI 依据必须落库，事后能解释');
  assert.equal(submit.data.events.length, 1);
  assert.equal(submit.data.events[0].manual, 1);

  await env.login('worker');
  const assign = await env.call('POST', `/orders/${no}/assign`, { system: true });
  assert.equal(assign.status, 200);
  assert.equal(assign.data.worker.name.length > 0, true);

  const arrive = await env.call('POST', `/orders/${no}/arrive`, { role: 'worker' });
  assert.equal(arrive.data.status, 'REPAIRING');

  const early = await env.call('POST', `/orders/${no}/finish`, { role: 'worker', body: { photoUrls: [] } });
  assert.equal(early.status, 422, '没完工照不能完工');

  const finish = await env.call('POST', `/orders/${no}/finish`, {
    role: 'worker', body: { photoUrls: ['/img/a.jpg'], noteTags: ['更换软管'] }
  });
  assert.equal(finish.data.status, 'VERIFYING');

  const detail = await env.call('GET', `/orders/${no}`, { role: 'student' });
  assert.equal(detail.data.timers.t1.label, '验收超时');
  assert.equal(detail.data.timers.t1.total, 168 * 3600 * 1000);
  assert.equal(detail.data.timers.t2, null, '待验收时不该有归档表');
  assert.ok(detail.data.timers.t1.leftMs > 0 && detail.data.timers.t1.leftMs <= 168 * 3600 * 1000);

  const accept = await env.call('POST', `/orders/${no}/accept`, { role: 'student' });
  assert.equal(accept.data.status, 'DONE');

  const after = await env.call('GET', `/orders/${no}`, { role: 'student' });
  assert.equal(after.data.timers.t1, null, '签字之后验收表就该没了');
  assert.equal(after.data.timers.t2.total, 7 * 24 * 3600 * 1000);

  // 未满 7 天不许归档
  const tooEarly = await env.call('POST', `/orders/${no}/archive`, { system: true });
  assert.equal(tooEarly.status, 422);

  // 快进 8 天，让定时器自己归档
  const adv = await env.call('POST', '/demo/advance', { body: { days: 8 } });
  assert.equal(adv.status, 200);
  const closed = await env.call('GET', `/orders/${no}`, { role: 'student' });
  assert.equal(closed.data.status, 'CLOSED');
  const last = closed.data.events[closed.data.events.length - 1];
  assert.equal(last.event, 'ARCHIVE');
  assert.equal(last.manual, 0, '系统归档不算人工');
});

test('C. 非法转移一律 409，而且被拒之后一个字都没改', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('student');
  await env.login('worker');

  const pend = env.db.prepare("SELECT no FROM orders WHERE status='PENDING' AND worker_id IS NULL LIMIT 1").get();
  const before = snapshot(env.db);

  // 待派工的单：不能打卡、不能完工、不能验收
  const a = await env.call('POST', `/orders/${pend.no}/arrive`, { role: 'worker' });
  const b = await env.call('POST', `/orders/${pend.no}/finish`, { role: 'worker', body: { photoUrls: ['/x.jpg'], noteTags: ['疏通管道'] } });
  const c = await env.call('POST', `/orders/${pend.no}/accept`, { role: 'student' });

  for (const r of [a, b, c]) {
    assert.equal(r.status, 409);
    assert.equal(r.error.code, 409);
    assert.ok(r.error.allowed !== undefined, '409 必须告诉前端允许哪些事件');
  }
  assert.equal(snapshot(env.db), before, '被拒绝的请求不许留下任何痕迹');

  // 终态不可再动
  const closed = env.db.prepare("SELECT no FROM orders WHERE status='CLOSED' LIMIT 1").get();
  const d = await env.call('POST', `/orders/${closed.no}/archive`, { system: true });
  assert.equal(d.status, 409);
  assert.equal(snapshot(env.db), before);
});

test('D. 乐观锁：version 过期 → 409 VERSION_CONFLICT', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('student');

  const s = await env.call('POST', '/orders', {
    body: { desc: '门锁卡住拧不动', photoUrls: ['/i.jpg'] }
  });
  const no = s.data.no;
  const v = s.data.version;

  await env.call('POST', `/orders/${no}/assign`, { system: true });

  const stale = await env.call('POST', `/orders/${no}/cancel`, { role: 'student', body: { version: v } });
  assert.equal(stale.status, 409);
  assert.equal(stale.error.reason, 'VERSION_CONFLICT');

  const fresh = await env.call('POST', `/orders/${no}/cancel`, { role: 'student', body: { version: v + 1 } });
  assert.equal(fresh.status, 200);
  assert.equal(fresh.data.status, 'CANCELLED');
});

test('E. 撤销边界：进楼前能撤，进楼后只能申请', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('student');
  await env.login('worker');

  const mk = async (desc) => {
    const r = await env.call('POST', '/orders', { role: 'student', body: { desc, photoUrls: ['/i.jpg'] } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    await env.call('POST', `/orders/${r.data.no}/assign`, { system: true });
    return r.data.no;
  };

  // 已派工但师傅没打卡 → 学生直接撤
  const a = await mk('水龙头关不紧一直滴水');
  const ra = await env.call('POST', `/orders/${a}/cancel`, { role: 'student' });
  assert.equal(ra.data.status, 'CANCELLED', '未打卡可直接撤销');

  // 已进楼 → 撤销 409，但可以提交申请（202）
  const b = await mk('插座冒烟有糊味，不敢用了');
  await env.call('POST', `/orders/${b}/arrive`, { role: 'worker' });
  const rb = await env.call('POST', `/orders/${b}/cancel`, { role: 'student' });
  assert.equal(rb.status, 409);
  assert.equal(rb.error.reason, 'ARRIVED');

  const req = await env.call('POST', `/orders/${b}/cancel-request`, { role: 'student', body: { reason: '临时离校，先不修了' } });
  assert.equal(req.status, 202);
  assert.equal(req.data.pending, true);

  const d = await env.call('GET', `/orders/${b}`, { role: 'student' });
  assert.equal(d.data.status, 'REPAIRING', '申请撤销不许改状态');
  assert.equal(d.data.cancelRequest.reason, '临时离校，先不修了');

  // 管理端看得见，但点不动（501）
  await env.login('admin');
  const ex = await env.call('GET', '/admin/exceptions?kind=CANCEL_REQUEST', { role: 'admin' });
  assert.ok(ex.data.items.some(i => i.no === b), '申请撤销要进例外清单');
  const won = await env.call('POST', `/orders/${b}/admin-cancel`, { role: 'admin' });
  assert.equal(won.status, 501);
  assert.equal(won.error.changeRequest, 'CR-001');
});

test('F. 返修：-R1 新建、紧急度升一档、验收通过后原单级联关闭', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('student');
  await env.login('worker');

  const mk = async (desc) => {
    const r = await env.call('POST', '/orders', { role: 'student', body: { desc, photoUrls: ['/i.jpg'] } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    const no = r.data.no;
    await env.call('POST', `/orders/${no}/assign`, { system: true });
    await env.call('POST', `/orders/${no}/arrive`, { role: 'worker' });
    await env.call('POST', `/orders/${no}/finish`, { role: 'worker', body: { photoUrls: ['/a.jpg'], noteTags: ['重新打胶'] } });
    return no;
  };

  const no = await mk('洗手台下面漏水，柜子里全是水');
  const orig = (await env.call('GET', `/orders/${no}`, { role: 'student' })).data;
  assert.equal(orig.level, '紧急', '描述里有浸水风险，AI 应该定紧急');

  const rej = await env.call('POST', `/orders/${no}/reject`, { role: 'student', body: { reason: '还是漏，没修好' } });
  assert.equal(rej.status, 200);
  const r1 = rej.data.reworkNo;
  assert.equal(r1, `${no}-R1`);

  const o1 = (await env.call('GET', `/orders/${no}`, { role: 'student' })).data;
  assert.equal(o1.status, 'REWORKING');
  assert.equal(o1.reworkCount, 1);

  const n1 = (await env.call('GET', `/orders/${r1}`, { role: 'student' })).data;
  assert.equal(n1.status, 'PENDING');
  assert.equal(n1.reworkOf, no);
  assert.equal(n1.level, '紧急', '已经紧急就封顶，不能升到没这档');
  assert.deepEqual(n1.chain, [no, r1].sort());
  assert.ok(n1.photosBefore.length >= 1);

  // 返修单走完 → 原单自动关闭（级联，不占 13 条转移的名额）
  await env.call('POST', `/orders/${r1}/assign`, { system: true });
  await env.call('POST', `/orders/${r1}/arrive`, { role: 'worker' });
  await env.call('POST', `/orders/${r1}/finish`, { role: 'worker', body: { photoUrls: ['/a2.jpg'], noteTags: ['更换阀芯'] } });
  await env.call('POST', `/orders/${r1}/accept`, { role: 'student' });

  const o2 = (await env.call('GET', `/orders/${no}`, { role: 'student' })).data;
  assert.equal(o2.status, 'CLOSED', '返修单验收通过，原单要关掉');
  const cascade = o2.events.find(e => e.event === 'ORIGIN_CLOSED');
  assert.ok(cascade, '级联关闭要留事件，不能悄悄改');
  assert.equal(cascade.manual, 1, '这条是学生验收带出来的');
});

test('G. 缺件：预算内自动过、超预算进例外；到货回队列保留原定级', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('student');
  await env.login('worker');

  const mk = async (desc) => {
    const r = await env.call('POST', '/orders', { role: 'student', body: { desc, photoUrls: ['/i.jpg'] } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    const no = r.data.no;
    await env.call('POST', `/orders/${no}/assign`, { system: true });
    await env.call('POST', `/orders/${no}/arrive`, { role: 'worker' });
    return no;
  };

  const cheap = await mk('窗户把手断了，关不严');
  const lv = (await env.call('GET', `/orders/${cheap}`, { role: 'student' })).data.level;
  const p1 = await env.call('POST', `/orders/${cheap}/need-parts`, {
    role: 'worker', body: { name: '窗户把手', amount: 25, etaDate: '2026-11-25' }
  });
  assert.equal(p1.data.status, 'PARTS');
  assert.equal(p1.data.overBudget, false);

  const back = await env.call('POST', `/orders/${cheap}/parts-arrived`, { system: true });
  assert.equal(back.data.status, 'PENDING');
  const o = (await env.call('GET', `/orders/${cheap}`, { role: 'student' })).data;
  assert.equal(o.level, lv, '回队列不许降级');

  const pricey = await mk('空调外机不转，怀疑压缩机坏了');
  const p2 = await env.call('POST', `/orders/${pricey}/need-parts`, {
    role: 'worker', body: { name: '压缩机', amount: 480, etaDate: '2026-11-28' }
  });
  assert.equal(p2.data.overBudget, true);

  await env.login('admin');
  const ex = await env.call('GET', '/admin/exceptions?kind=OVER_BUDGET', { role: 'admin' });
  assert.ok(ex.data.items.some(i => i.no === pricey), '超预算要进例外清单');

  // 一次花超预算，这笔账就得留到归档：配件到了也还挂在清单上
  const arrived = await env.call('POST', `/orders/${pricey}/parts-arrived`, { system: true });
  assert.equal(arrived.data.status, 'PENDING');
  const ex2 = await env.call('GET', '/admin/exceptions?kind=OVER_BUDGET', { role: 'admin' });
  const row = ex2.data.items.find(i => i.no === pricey);
  assert.ok(row, '配件到了也不能把超预算记录一起消掉（事后抽查要能对上账）');
  assert.match(row.detail, /配件已到/);
});

test('H. 楼栋打卡：一次点击 N 条单级 ARRIVE，已进楼的跳过', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('worker');
  const me = await env.login('worker');

  const r = await env.call('POST', `/buildings/${me.user.building}/arrive`, { role: 'worker' });
  assert.equal(r.status, 200);
  assert.ok(r.data.ok.length >= 1, '本楼至少有一单被打卡');
  for (const item of r.data.ok) {
    const d = await env.call('GET', `/orders/${item.no}`, { role: 'worker' });
    assert.equal(d.data.status, 'REPAIRING');
    assert.ok(d.data.arriveAt, '每张单都有自己的到场时间');
    const ev = d.data.events.filter(e => e.event === 'ARRIVE');
    assert.equal(ev.length, 1, '单级 ARRIVE，不是一次事件挂 N 单');
  }
  assert.ok(r.data.skipped.length >= 0);

  // 再点一次：已经维修中的进 skipped，不会重复打卡
  const again = await env.call('POST', `/buildings/${me.user.building}/arrive`, { role: 'worker' });
  assert.equal(again.data.ok.length, 0);
  assert.ok(again.data.skipped.length >= 1);
  assert.ok(again.data.skipped.every(s => s.reason === 'ALREADY_REPAIRING'));
});

test('I. 两颗计时器互不干扰', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('student');

  const verifying = env.db.prepare("SELECT no FROM orders WHERE status='VERIFYING'").all();
  const done = env.db.prepare("SELECT no FROM orders WHERE status='DONE'").all();
  for (const r of verifying) {
    const d = (await env.call('GET', `/orders/${r.no}`, { role: 'student' })).data;
    assert.ok(d.timers.t1, `${r.no} 应有 T1`);
    assert.equal(d.timers.t2, null, `${r.no} 不该有 T2`);
  }
  for (const r of done) {
    const d = (await env.call('GET', `/orders/${r.no}`, { role: 'student' })).data;
    assert.ok(d.timers.t2, `${r.no} 应有 T2`);
    assert.equal(d.timers.t1, null, `${r.no} 不该有 T1`);
  }

  // 只快进 8 天：待验收的一律自动验收（转已完成），已完成的一律归档
  await env.call('POST', '/demo/advance', { body: { days: 8 } });
  for (const r of verifying) {
    const d = (await env.call('GET', `/orders/${r.no}`, { role: 'student' })).data;
    assert.equal(d.status, 'DONE', 'T1 超时 → 自动验收，签字人是系统');
    const auto = d.events.find(e => e.event === 'AUTO_ACCEPT');
    assert.equal(auto.manual, 0, '自动验收 manual=0');
    assert.ok(d.timers.t2, '自动验收之后才开始数归档那 7 天');
  }
  // 再快进 7 天，才轮到归档
  await env.call('POST', '/demo/advance', { body: { days: 8 } });
  for (const r of verifying) {
    const d = (await env.call('GET', `/orders/${r.no}`, { role: 'student' })).data;
    assert.equal(d.status, 'CLOSED');
  }
});

test('J. 管理端看板与例外清单的口径', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('admin');

  const d = await env.call('GET', '/admin/dashboard', { role: 'admin' });
  const data = d.data;
  assert.equal(typeof data.zeroManual.rate, 'number');
  assert.equal(data.zeroManual.total, 3, '已完成/已关闭的样本数');
  assert.equal(data.zeroManual.zero, 2, '其中两张全程没有管理员事件');
  assert.equal(data.zeroManual.rate, 0.67);
  assert.ok(data.reworkRate >= 0 && data.reworkRate <= 1);
  assert.ok(data.byBuilding.length >= 2);

  const ex = await env.call('GET', '/admin/exceptions', { role: 'admin' });
  const kinds = new Set(ex.data.items.map(i => i.kind));
  for (const k of ['MISMATCH', 'OVER_BUDGET', 'REPEAT_REWORK', 'CANCEL_REQUEST']) {
    assert.ok(kinds.has(k), `四类例外都要露出来：${k}`);
  }
  assert.equal(ex.data.items.length, 4, '一类一条样本，不多露');

  // 例外清单只是看，不许改状态
  const snap = snapshot(env.db);
  await env.call('GET', '/admin/exceptions', { role: 'admin' });
  assert.equal(snapshot(env.db), snap);
});

test('K. 师傅的任务列表与查询', async (t) => {
  const env = await boot();
  t.after(() => env.close());
  await env.login('worker');

  const todo = await env.call('GET', '/worker/todo', { role: 'worker' });
  assert.ok(Array.isArray(todo.data.buildings));
  const b = todo.data.buildings[0];
  assert.ok(b.buildingId && b.buildingName);
  assert.ok(Array.isArray(b.floors));
  if (b.floors.length > 1) {
    assert.ok(b.floors[0].floor > b.floors[b.floors.length - 1].floor, '楼层要从高往低排，师傅一层层往下走');
  }

  const board = await env.call('GET', `/buildings/${b.buildingId}/board`, { role: 'worker' });
  assert.equal(board.status, 200);

  const bad = await env.call('GET', '/orders/BX20260101001', { role: 'worker' });
  assert.equal(bad.status, 404);
});

test('L. 建单时带 autoAssign = 服务端自动受理并派工', async (t) => {
  const env = await boot({ empty: true });
  t.after(() => env.close());

  // 没有种子单时先补两个账号，否则没人可派
  await env.call('POST', '/demo/reset', { role: 'admin' });

  await env.login('student');
  const plain = await env.call('POST', '/orders', {
    body: { desc: '洗手池下面的软管一直滴水，快接满了', photoUrls: ['demo://a.jpg'] }
  });
  assert.equal(plain.status, 201);
  assert.equal(plain.data.status, 'PENDING', '不带 autoAssign 必须是待派工，行为不变');
  assert.equal(plain.data.worker, null);

  const auto = await env.call('POST', '/orders', {
    body: { desc: '洗手池下面的软管一直滴水，快接满了', photoUrls: ['demo://b.jpg'], autoAssign: true }
  });
  assert.equal(auto.status, 201);
  assert.equal(auto.data.status, 'ASSIGNED', '带 autoAssign 时服务端自己派工');
  assert.ok(auto.data.worker && auto.data.worker.id, '返回体里要带派给了谁');

  const evs = await env.call('GET', `/orders/${auto.data.no}/events`, { role: 'student' });
  const assignEv = evs.data.items.find((e) => e.event === 'ASSIGN');
  assert.ok(assignEv, '要留下 ASSIGN 事件');
  assert.equal(assignEv.actorType, 'system');
  assert.equal(assignEv.manual, 0, '系统自动派工要算 manual=0，不能算人的功劳');
});
