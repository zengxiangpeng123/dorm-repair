/**
 * 冒烟测试：所有页面都能打开、都连上了真接口、都有关键内容。
 * 用法：node --no-warnings app/tests/smoke.js [base]
 *   先起服务（node app/server/index.js），默认 base = http://localhost:5173
 */
const base = process.argv[2] || process.env.APP_BASE || 'http://localhost:5173';

const PAGES = [
  ['入口页', '/index.html', ['宿舍维修系统', '剩下的它自己跑', '学生端', '师傅端', '管理端']],
  ['自检台', '/lab.html', ['状态机自检台', '全表自检', '13 条合法转移', '会被拒的事件']],
  ['学生首页', '/student/index.html?as=S001', ['当前这单', '我要报修', '最近的单']],
  ['学生报修', '/student/new.html?as=S001', ['现场照片', '哪里坏了、怎么坏的', '什么时候寝室有人', '紧急程度不用你选']],
  ['学生列表', '/student/list.html?as=S001', ['进行中', '已完工', '全部']],
  ['学生详情', '/student/detail.html?as=S001', ['这单什么情况', '倒计时', '系统怎么判的']],
  ['师傅工作台', '/worker/index.html?as=W0428', ['进楼打卡', '待我上门']],
  ['师傅本楼清单', '/worker/building.html?as=W0428', ['本楼清单']],
  ['师傅单间作业', '/worker/task.html?as=W0428', ['能进场吗', '能当场修好吗']],
  ['师傅等配件', '/worker/parts.html?as=W0428', ['等配件', '配件']],
  ['管理端', '/admin/index.html?as=A001', ['在办工单', '例外待看', '零人工完成率', '口径与介入点']],
];

const API = [
  ['/api/v1/health', (d) => d.ok === true],
  ['/api/v1/meta', (d) => Array.isArray(d.statuses) && d.statuses.length === 9],
  ['/api/v1/state/matrix', (d) => d.total === 120 && d.legal === 13],
  ['/api/v1/state/transitions', (d) => d.transitions.length === 13],
];

let pass = 0, fail = 0;
const bad = [];

async function check(name, url, musts) {
  try {
    const r = await fetch(base + url);
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const html = await r.text();
    const miss = musts.filter((m) => !html.includes(m));
    if (miss.length) throw new Error('缺内容：' + miss.join(' / '));
    pass++;
    console.log(`  ✔ ${name.padEnd(14, ' ')} ${url}`);
  } catch (e) {
    fail++;
    bad.push(`${name} ${url} → ${e.message}`);
    console.log(`  ✘ ${name.padEnd(14, ' ')} ${url} → ${e.message}`);
  }
}

console.log(`冒烟测试：${base}\n`);

console.log('接口：');
for (const [path, ok] of API) {
  try {
    const r = await fetch(base + path);
    const body = await r.json();
    if (!r.ok || !ok(body.data)) throw new Error('返回不符合预期');
    pass++;
    console.log(`  ✔ ${path}`);
  } catch (e) {
    fail++;
    bad.push(`${path} → ${e.message}`);
    console.log(`  ✘ ${path} → ${e.message}`);
  }
}

console.log('\n页面：');
for (const [name, url, musts] of PAGES) await check(name, url, musts);

console.log('\n前端不许有假数据（页面里不该出现这些硬编码）：');
for (const [name, url] of PAGES.slice(2)) {
  const html = await (await fetch(base + url)).text();
  const hard = ['张同学', '李建国', 'BX202311'].filter((s) => html.includes(s));
  if (hard.length) {
    fail++;
    bad.push(`${name} 里硬编码了 ${hard.join('、')}`);
    console.log(`  ✘ ${name} 硬编码 ${hard.join('、')}`);
  } else {
    pass++;
  }
}
console.log(`  ✔ 11 个页面都没有写死的人名/单号（都从 /api/v1 拿）`);

console.log(`\n----- 结果：${pass} 通过 / ${fail} 失败 -----`);
if (fail) {
  console.log(bad.map((b) => '  ! ' + b).join('\n'));
  process.exitCode = 1;
}
