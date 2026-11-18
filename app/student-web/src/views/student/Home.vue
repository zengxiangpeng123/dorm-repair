<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import StatusPill from '../../components/StatusPill.vue'
import LevelPill from '../../components/LevelPill.vue'
import { studentApi } from '../../api/orders'
import { userOf } from '../../api/session'
import { ACTIVE_STATUS, STATUS, shortLeft, steps } from '../../constants/status'

const router = useRouter()
const me = userOf()
const active = ref([])
const recent = ref([])
const loading = ref(true)
const err = ref('')
let timer = null

const current = computed(() => active.value[0] || null)
const stepList = computed(() => (current.value ? steps(current.value) : []))

function leftOf (o) {
  const t = o.timers && (o.timers.t1 || o.timers.t2)
  return t ? shortLeft(t.leftMs) : ''
}

async function load () {
  try {
    const [a, all] = await Promise.all([studentApi.myOrders('ACTIVE'), studentApi.myOrders('ALL')])
    active.value = a.items || []
    recent.value = (all.items || []).slice(0, 4)
    err.value = ''
  } catch (e) {
    err.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}


onMounted(() => {
  load()
  timer = setInterval(load, 30000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <header class="top">
    <div class="between">
      <div>
        <div class="tiny muted">{{ me.building === 'B7' ? '东区7号楼' : '东区8号楼' }} · {{ me.room }} 室</div>
        <div class="name">{{ me.name }}</div>
      </div>

    </div>
  </header>

  <div class="body">
    <p v-if="err" class="err">{{ err }}</p>

    <div v-if="current" class="card cur" @click="router.push(`/student/detail/${current.no}`)">
      <div class="card-hd">
        <h3>{{ current.building }} {{ current.floor }}F-{{ current.room }}</h3>
        <div class="row">
          <LevelPill :level="current.level" />
          <StatusPill :status="current.status" />
        </div>
      </div>
      <p class="desc">{{ current.desc }}</p>
      <div class="steps">
        <div v-for="(s, i) in stepList" :key="i" class="st" :data-s="s.state">
          <i></i><span>{{ s.label }}</span>
        </div>
      </div>
      <div v-if="leftOf(current)" class="timer">
        <span>{{ current.timers.t1 ? '验收倒计时' : '归档倒计时' }}</span>
        <b>{{ leftOf(current) }}</b>
      </div>
    </div>

    <div v-else-if="!loading" class="card empty">
      现在没有在办的报修。有东西坏了就报一单，师傅会自己看到。
    </div>

    <button class="big-cta" @click="router.push('/student/new')">
      <span class="plus">＋</span>
      <span>
        <b>我要报修</b>
        <em>拍一张、说一句，剩下的它自己跑</em>
      </span>
    </button>

    <div v-if="recent.length" class="section-title">最近的报修</div>
    <div
      v-for="o in recent" :key="o.no" class="ticket" :data-st="o.status"
      @click="router.push(`/student/detail/${o.no}`)"
    >
      <div class="tx">
        <div class="r1">
          <b>{{ o.building }} {{ o.floor }}F-{{ o.room }}</b>
          <StatusPill :status="o.status" />
        </div>
        <div class="r2">{{ o.desc }}</div>
        <div class="r3">{{ o.no }} · {{ o.category }} · {{ o.level }}</div>
      </div>
    </div>
  </div>

  <BottomNav role="student" />
</template>

<style scoped>
.top { padding: 12px 16px 10px; }
.name { font-size: 18px; font-weight: 700; }
.switch {
  border: 1px solid var(--line); background: #fff; color: var(--ink-2);
  border-radius: 999px; padding: 6px 12px; font-size: 12px;
}
.err { color: var(--danger); font-size: 13px; margin: 0 0 10px; }
.cur { cursor: pointer; }
.cur .desc {
  margin: 2px 0 12px; font-size: 13.5px; color: var(--ink-2);
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.steps { display: grid; grid-template-columns: repeat(5, 1fr); gap: 2px; }
.steps .st { text-align: center; font-size: 10.5px; color: var(--ink-3); }
.steps .st i {
  display: block; height: 3px; border-radius: 2px; background: var(--line); margin-bottom: 6px;
}
.steps .st[data-s="done"] i { background: var(--navy); }
.steps .st[data-s="now"] i { background: var(--orange); }
.steps .st[data-s="now"] span { color: var(--ink); font-weight: 650; }
.steps .st[data-s="done"] span { color: var(--ink-2); }
.timer {
  margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--line);
  display: flex; justify-content: space-between; font-size: 12.5px; color: var(--ink-2);
}
.timer b { font-variant-numeric: tabular-nums; color: var(--orange); }
.big-cta {
  width: 100%; margin: 12px 0 4px; padding: 15px 16px; border: 0; border-radius: 16px;
  background: var(--navy); color: #fff; display: flex; gap: 12px; align-items: center;
  text-align: left; box-shadow: var(--sh-md);
}
.big-cta .plus {
  width: 34px; height: 34px; border-radius: 12px; background: rgba(255,255,255,.14);
  display: grid; place-items: center; font-size: 20px; line-height: 1;
}
.big-cta b { display: block; font-size: 15.5px; }
.big-cta em { display: block; font-style: normal; font-size: 12px; color: #b9c7dc; margin-top: 2px; }
.r1 { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.r1 b { font-size: 14px; }
.r2 {
  font-size: 12.5px; color: var(--ink-2); margin: 4px 0 3px;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.r3 { font-size: 11px; color: var(--ink-3); font-variant-numeric: tabular-nums; }
</style>
