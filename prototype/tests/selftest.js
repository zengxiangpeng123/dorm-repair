/* 无头自测：把 seed.js + app.js 装进一个假 window，跑一遍完整链路
 * 验三件事（对应 demo 三必证）：
 *   1. 一条链能不能自己走完（提交 → 派工 → 打卡 → 完工 → 验收 → 归档）
 *   2. 非法转移是否当场 409，且状态不变
 *   3. 两颗 7 天计时器互不干扰
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = process.argv[2] || '.';
const store = {};
const sandbox = {
  console,
  localStorage: {
    getItem: k => (k in store ? store[k] : null),
    setItem: (k, v) => { store[k] = String(v); },
    removeItem: k => { delete store[k]; }
  },
  document: {
    querySelector: () => null,
    querySelectorAll: () => [],
    createElement: () => ({ style: {}, classList: { add() {} }, appendChild() {}, setAttribute() {} }),
    body: { appendChild() {}, classList: { add() {}, remove() {} } }
  },
  location: { search: '', href: '' },
  setTimeout, clearTimeout, Date, Math, JSON
};
sandbox.window = sandbox;
sandbox.globalThis = sandbox;

function load(rel) {
  const p = path.join(ROOT, rel);
  vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(p, 'utf8'), sandbox, { filename: p });
}
load('data/seed.js');
load('assets/app.js');

const App = sandbox.App;
let pass = 0, fail = 0;
function ok(name, cond, extra) {
  if (cond) { pass++; console.log('  ✓ ' + name); }
  else { fail++; console.log('  ✗ ' + name + (extra ? '  → ' + extra : '')); }
}

console.log('\n=== 1. 一条链自己走完（含 AI 定级、生成单号） ===');
App.reset();
const o = App.create({ desc: '洗手盆下面的软管一直滴水，快接满了', photosBefore: 1, window: '下午 14:00-18:00', building: 'B7', room: '402' });
ok('建单后状态是待派工', o.status === 'PENDING', o.status);
ok('单号形如 BX+8位日期+3位序号', /^BX\d{11}$/.test(o.no), o.no);
ok('照片 0 张被拒', (() => { try { App.create({ desc: '一二三四', photosBefore: 0 }); return false; } catch (e) { return e.code === 400; } })());
ok('描述 3 字被拒', (() => { try { App.create({ desc: '漏水啊', photosBefore: 1 }); return false; } catch (e) { return e.code === 400; } })());
ok('AI 判成给排水·紧急（安全类优先）', o.category === '给排水' && o.level === '紧急', o.category + '/' + o.level);
ok('AI 依据写进了工单', !!(o.aiBasis && o.aiBasis.length > 5));

App.assignPending('B7');
const a = App.order(o.no);
ok('派工后是已派工且写了师傅', a.status === 'ASSIGNED' && !!a.workerId, a.status + '/' + a.workerId);

const wName = (App.worker(a.workerId) || {}).name;
App.fire(o.no, 'ARRIVE', 'worker:' + wName, '到门打卡');
let x = App.order(o.no);
ok('打卡后是维修中且写了 arriveAt', x.status === 'REPAIRING' && !!x.arriveAt, x.status);
ok('arriveAt 与 statusAt 同步', x.arriveAt === x.statusAt);

App.fire(o.no, 'FINISH', 'worker:' + wName, '换软管试水，完工照 1 张');
x = App.order(o.no);
ok('完工后是待验收且写了 finishAt', x.status === 'VERIFYING' && !!x.finishAt, x.status);

App.fire(o.no, 'ACCEPT', 'student:张同学', '学生签字');
x = App.order(o.no);
ok('验收后是已完成且写了 signAt', x.status === 'DONE' && !!x.signAt, x.status);
ok('全程事件 manual 标记齐全', x.events.every(e => e.manual === 0 || e.manual === 1));

App.fire(o.no, 'ARCHIVE', 'system', '满 7 天归档');
x = App.order(o.no);
ok('归档后是已关闭（终态）', x.status === 'CLOSED', x.status);

console.log('\n=== 2. 非法转移一律 409，且状态不变 ===');
const illegal = [
  ['PENDING', 'ARRIVE'], ['PENDING', 'FINISH'], ['PENDING', 'ACCEPT'], ['PENDING', 'ARCHIVE'],
  ['ASSIGNED', 'FINISH'], ['ASSIGNED', 'ACCEPT'],
  ['REPAIRING', 'ACCEPT'], ['REPAIRING', 'CANCEL'], ['REPAIRING', 'ARCHIVE'],
  ['VERIFYING', 'ARRIVE'], ['VERIFYING', 'CANCEL'], ['VERIFYING', 'ASSIGN'],
  ['DONE', 'ACCEPT'], ['CLOSED', 'ACCEPT'], ['CLOSED', 'ARCHIVE'], ['CANCELLED', 'ASSIGN']
];
let n409 = 0, stateKept = 0;
illegal.forEach(([st, ev]) => {
  const probe = App.create({ desc: '探针单用来测非法转移是否 409', photosBefore: 1 });
  probe.status = st;                      // 直接摆到目标状态（绕开流程，只为构造）
  const before = probe.status;
  let code = null;
  try { App.fire(probe.no, ev, 'student:张同学', '非法尝试'); } catch (e) { code = e.code; }
  if (code === 409) n409++;
  if (App.order(probe.no).status === before) stateKept++;
});
ok(`16 种非法转移全部 409（${n409}/16）`, n409 === illegal.length);
ok(`被拒后状态一律没变（${stateKept}/16）`, stateKept === illegal.length);

console.log('\n=== 3. 触发器与撤销边界 ===');
const p2 = App.create({ desc: '再建一张用来测触发方和撤销边界', photosBefore: 1, building: 'B7', room: '411' });
ok('学生不能发 ASSIGN', (() => { try { App.fire(p2.no, 'ASSIGN', 'student:张同学', ''); return false; } catch (e) { return e.code === 409; } })());
ok('系统不能发 SUBMIT', (() => { try { App.fire(p2.no, 'SUBMIT', 'system', ''); return false; } catch (e) { return e.code === 409; } })());
App.fire(p2.no, 'CANCEL', 'student:张同学', '还没派工，直接撤');
ok('待派工可以撤销', App.order(p2.no).status === 'CANCELLED');

const p3 = App.create({ desc: '测师傅已进楼后不能直接撤销', photosBefore: 1, building: 'B7', room: '412' });
App.assignPending('B7');
if (App.order(p3.no).status === 'ASSIGNED') {
  const wn = (App.worker(App.order(p3.no).workerId) || {}).name;
  App.fire(p3.no, 'ARRIVE', 'worker:' + wn, '进楼');
  ok('已进楼后学生撤销被 409 挡住', (() => { try { App.fire(p3.no, 'CANCEL', 'student:张同学', ''); return false; } catch (e) { return e.code === 409; } })());
} else {
  ok('已进楼后学生撤销被 409 挡住', false, '派工没成功，样本不可用');
}

console.log('\n=== 4. 返修单（-R1）与回暖流 ===');
const r = App.create({ desc: '水龙头关了还在渗水，越来越厉害', photosBefore: 1, building: 'B7', room: '421' });
App.assignPending('B7');
let rn = (App.worker(App.order(r.no).workerId) || {}).name;
App.fire(r.no, 'ARRIVE', 'worker:' + rn, '进楼');
App.fire(r.no, 'FINISH', 'worker:' + rn, '换阀芯，完工照 1 张');
const beforeLv = App.order(r.no).level;
App.fire(r.no, 'REJECT', 'student:张同学', '还在渗水');
const orig = App.order(r.no);
const child = App.order(r.no + '-R1');
ok('原单变返修中', orig.status === 'REWORKING', orig.status);
ok('生成了 -R1 新单', !!child, child && child.no);
ok('-R1 从待派工入队', child && child.status === 'PENDING', child && child.status);
ok('-R1 指回原单', child && child.reworkOf === r.no, child && child.reworkOf);
ok('紧急度升了一档', child && child.level !== beforeLv && ['较急', '紧急'].indexOf(child.level) >= 0, beforeLv + ' → ' + (child && child.level));

console.log('\n=== 5. 两颗 7 天计时器互不干扰 ===');
const t1o = App.order('BX20261115005');   // 待验收，finishAt 11-15 16:40
const t2o = App.order('BX20261114002');   // 已完成，signAt
const T1 = App.t1(t1o), T2 = App.t2(t2o);
ok('待验收单只有 T1', !!T1 && !App.t2(t1o));
ok('已完成单只有 T2', !!T2 && !App.t1(t2o));
ok('T1 起点是 finishAt', t1o.finishAt && T1.from.getTime() === new Date(t1o.finishAt.replace(' ','T')+':00').getTime(), T1 && T1.from);
ok('T2 起点是 signAt', t2o.signAt && T2.from.getTime() === new Date(t2o.signAt.replace(' ','T')+':00').getTime(), T2 && T2.from);
const m1 = App.t1(t1o), m2 = App.t2(t2o);
ok('两者剩余时间不相等（不同计时器）', m1.left !== m2.left, m1.left + ' vs ' + m2.left);

console.log('\n=== 6. 自动动作（到点才动，不提前）===');
App.reset();
const before = App.order('BX20261115005').status;
App.tick(true);
ok('刚打开不会立刻自动验收', App.order('BX20261115005').status === before, App.order('BX20261115005').status);
App.advance(8 * 24 * 3600 * 1000);
App.tick(true);
const after5 = App.order('BX20261115005').status;
ok('快进 8 天后待验收自动确认（manual=0）', after5 === 'DONE' && (App.order('BX20261115005').events.slice(-1)[0] || {}).manual === 0, after5);
const after2 = App.order('BX20261114002').status;
ok('快进 8 天后已完成自动归档', after2 === 'CLOSED', after2);

console.log('\n=== 7. 例外清单与零人工完成率 ===');
App.reset();
App.tick(true);
const ex = App.exceptions();
ok('例外清单只露约定的几类', ex.every(e => ['数据对不上', '超预算采购', '反复返修', '学生申请撤销'].indexOf(e.kind) >= 0),
  JSON.stringify(ex.map(e => e.kind)));
const rate = App.zeroManualRate();
ok('零人工完成率是 0~1 之间的比例', rate.rate >= 0 && rate.rate <= 1, JSON.stringify(rate));

console.log(`\n----- 结果：${pass} 通过 / ${fail} 失败 -----`);
process.exit(fail ? 1 : 0);

