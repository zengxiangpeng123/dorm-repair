<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ordersApi } from '../api/orders'
import { describeError } from '../api/request'
import { userOf } from '../api/session'

const router = useRouter()
const stats = ref({ orders: '—', zero: '—', exceptions: '—' })
const err = ref('')
const busy = ref('')

const ENDS = [
  {
    to: '/student', device: 'phone', title: '学生端', sub: '430 宽 · 手机页',
    points: ['拍一张、说一句就报修', '不用选紧急程度，系统判', '全程看到师傅在哪一步']
  },
  {
    to: '/worker', device: 'phone', title: '师傅端', sub: '430 宽 · 手机页',
    points: ['按楼栋跑，楼层从高到低', '进楼打卡一次，一批单一起记到门', '缺件填金额，超预算自动上审批']
  },
  {
    to: '/admin', device: 'desk', title: '管理端', sub: '1440 宽 · 桌面页',
    points: ['不派工、不审批、不催办', '只看四类例外', '零人工完成率与返修率两个数']
  }
]

const currentUser = userOf()

function enter (to) {
  router.push(currentUser ? to : { path: '/login', query: { redirect: to } })
}

async function load () {
  if (currentUser?.role !== 'ADMIN') return
  try {
    const [dash, ex] = await Promise.all([ordersApi.dashboard(), ordersApi.exceptions()])
    stats.value = {
      orders: dash.active + dash.pendingArchive,
      zero: `${Math.round((dash.zeroManual?.rate ?? 0) * 100)}%`,
      exceptions: ex.total
    }
  } catch (e) {
    err.value = describeError(e)
  }
}

async function advance (hours) {
  busy.value = `advance${hours}`
  err.value = ''
  try {
    await ordersApi.advance(hours)
    await load()
  } catch (e) {
    err.value = describeError(e)
  } finally {
    busy.value = ''
  }
}

async function reset () {
  busy.value = 'reset'
  err.value = ''
  try {
    await ordersApi.reset()
    await load()
  } catch (e) {
    err.value = describeError(e)
  } finally {
    busy.value = ''
  }
}

onMounted(() => { if (currentUser?.role === 'ADMIN') load() })
</script>

<template>
  <div class="launch">
    <header class="hd">
      <div class="logo">修</div>
      <div>
        <div class="t">宿舍维修系统</div>
        <div class="s">示范大学 · 后勤维修部 · V5.0</div>
      </div>
      <div class="sp" />
      <button class="link" @click="enter('/admin')">进管理端 ›</button>
      <button class="link" @click="router.push('/login')">{{ currentUser ? '切换账号' : '登录' }} ›</button>
    </header>

    <section class="hero">
      <h1>拍一张、说一句，<em>剩下的它自己跑。</em></h1>
      <p>
        学生的报修进来自动定级、自动派给本楼师傅；师傅按楼层跑楼、缺件当场登记；<br />
        管理端不进流程，只在四类例外里出手。<br />
        一条链走完不用任何人点头，这就是这套东西要证明的事。
      </p>
    </section>

    <p v-if="err" class="err">{{ err }}</p>

    <div class="kpis">
      <div><div class="n num">{{ stats.orders }}</div><div class="k">在办与待归档</div></div>
      <div><div class="n num">13</div><div class="k">条合法转移（共 130 种组合）</div></div>
      <div><div class="n num">{{ stats.zero }}</div><div class="k">零人工完成率</div></div>
      <div><div class="n num">{{ stats.exceptions }}</div><div class="k">例外待看</div></div>
    </div>

    <div class="ends">
      <article v-for="e in ENDS" :key="e.to" class="end">
        <div class="eh">
          <span class="dev" :data-d="e.device">{{ e.device === 'desk' ? '电脑' : '手机' }}</span>
          <span class="sub">{{ e.sub }}</span>
        </div>
        <h3>{{ e.title }}</h3>
        <ul>
          <li v-for="p in e.points" :key="p">{{ p }}</li>
        </ul>
        <button class="go" @click="enter(e.to)">进入 ›</button>
      </article>
    </div>

    <div class="tools">
      <button class="tool" @click="router.push('/lab')">
        <b>状态机自检台</b><span>选一张单，推一条边，看 409 现场怎么拒</span>
      </button>
      <button class="tool" :disabled="busy === 'advance168'" @click="advance(168)">
        <b>快进 168 小时</b><span>让待验收的单超时自动验收</span>
      </button>
      <button class="tool" :disabled="busy === 'advance336'" @click="advance(336)">
        <b>再快进 168 小时</b><span>让已完成的单满 7 天自动归档</span>
      </button>
      <button class="tool" :disabled="busy === 'reset'" @click="reset">
        <b>重置演示数据</b><span>清空重灌一套，回到 7 张单</span>
      </button>
    </div>

    <div class="rule">
      <b>规矩</b>
      <span>9 个状态、13 条转移。没这条边就是 409，前置不满足是 422，身份不对是 403 —— 三种错不能混着返回。</span>
      <button class="link" @click="router.push('/lab')">看全表 ›</button>
    </div>

    <footer class="ft">
      学生端与师傅端是手机页（430 宽），管理端与自检台是桌面页 —— 这是产品定位，不是样式偏好。
    </footer>
  </div>
</template>

<style scoped>
.launch { max-width: 1180px; margin: 0 auto; padding: 22px 26px 40px; }
.hd { display: flex; align-items: center; gap: 11px; }
.logo {
  width: 34px; height: 34px; border-radius: 10px; background: #12294b; color: #fff;
  display: grid; place-items: center; font-size: 16px; font-weight: 700;
}
.t { font-size: 14px; font-weight: 700; }
.s { font-size: 11px; color: var(--ink-3); margin-top: 1px; }
.sp { flex: 1; }
.link { border: 0; background: transparent; color: #1b4ed8; font-size: 12.5px; cursor: pointer; }
.hero { margin: 34px 0 26px; }
.hero h1 { font-size: 38px; line-height: 1.28; margin: 0 0 12px; letter-spacing: -.01em; }
.hero h1 em { font-style: normal; color: #1b4ed8; }
.hero p { margin: 0; font-size: 13.5px; line-height: 1.95; color: var(--ink-2); }
.err { color: var(--danger); font-size: 13px; }
.kpis {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 1px; background: #e7ecf3;
  border-radius: 12px; overflow: hidden; margin-bottom: 22px;
}
.kpis > div { background: #fff; padding: 15px 18px; }
.kpis .n { font-size: 25px; font-weight: 700; }
.kpis .k { font-size: 11.5px; color: var(--ink-3); margin-top: 3px; }
.ends { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.end { background: #fff; border-radius: 14px; padding: 18px; box-shadow: var(--sh-sm); }
.eh { display: flex; align-items: center; gap: 8px; }
.dev {
  font-size: 10.5px; padding: 2px 8px; border-radius: 5px; background: #f2f4f7; color: #475467;
}
.dev[data-d="desk"] { background: #eff4ff; color: #1a3fa8; }
.sub { font-size: 11px; color: var(--ink-3); }
.end h3 { font-size: 17px; margin: 11px 0 9px; }
.end ul { margin: 0 0 15px; padding-left: 16px; }
.end li { font-size: 12.5px; color: var(--ink-2); line-height: 1.9; }
.go {
  width: 100%; height: 40px; border-radius: 10px; border: 1px solid var(--line);
  background: #fff; font-size: 13.5px; font-weight: 650; color: var(--ink); cursor: pointer;
}
.go:hover { background: #f7f9fc; }
.tools { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-top: 20px; }
.tool {
  text-align: left; background: #fff; border: 1px solid var(--line); border-radius: 12px;
  padding: 14px 15px; cursor: pointer;
}
.tool:hover { border-color: #c7d0dd; }
.tool b { display: block; font-size: 13px; }
.tool span { display: block; font-size: 11.5px; color: var(--ink-3); margin-top: 4px; line-height: 1.6; }
.rule {
  margin-top: 20px; background: #fff; border-radius: 12px; padding: 14px 16px;
  font-size: 12.5px; color: var(--ink-2); line-height: 1.75; box-shadow: var(--sh-sm);
}
.rule b { margin-right: 8px; color: var(--ink); }
.ft { margin-top: 18px; font-size: 11.5px; color: var(--ink-3); }
@media (max-width: 1000px) {
  .ends, .tools, .kpis { grid-template-columns: 1fr; }
  .hero h1 { font-size: 30px; }
}
</style>
