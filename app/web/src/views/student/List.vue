<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import StatusPill from '../../components/StatusPill.vue'
import LevelPill from '../../components/LevelPill.vue'
import { ordersApi } from '../../api/orders'
import { shortLeft, STATUS } from '../../constants/status'

const route = useRoute()
const router = useRouter()
const tab = ref(route.query.tab || 'ACTIVE')
const all = ref([])
const loading = ref(true)
const err = ref('')

const TABS = [
  { key: 'ACTIVE', label: '进行中' },
  { key: 'DONE', label: '已完工' },
  { key: 'ALL', label: '全部' }
]

const counts = computed(() => {
  const c = { ACTIVE: 0, DONE: 0, ALL: all.value.length }
  for (const o of all.value) {
    if (['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS', 'VERIFYING', 'REWORKING'].includes(o.status)) c.ACTIVE++
    if (o.status === 'DONE' || o.status === 'CLOSED') c.DONE++
  }
  return c
})

const rows = computed(() => {
  if (tab.value === 'ALL') return all.value
  if (tab.value === 'DONE') return all.value.filter((o) => o.status === 'DONE' || o.status === 'CLOSED')
  return all.value.filter((o) => ['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS', 'VERIFYING', 'REWORKING'].includes(o.status))
})

function left (o) {
  const t = o.timers && (o.timers.t1 || o.timers.t2)
  if (!t) return ''
  return `${t.label === '归档倒计时' ? '归档' : '验收'} ${shortLeft(t.leftMs)}`
}

async function load () {
  try {
    const r = await ordersApi.myOrders('ALL')
    all.value = r.items || []
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}

function pick (k) {
  tab.value = k
  router.replace({ query: { tab: k } })
}

onMounted(load)
</script>

<template>
  <header class="top"><b>我的报修</b><span class="tiny muted">共 {{ counts.ALL }} 单</span></header>

  <div class="body">
    <div class="seg">
      <button v-for="t in TABS" :key="t.key" :class="{ on: tab === t.key }" @click="pick(t.key)">
        {{ t.label }} {{ counts[t.key] }}
      </button>
    </div>

    <p v-if="err" class="err">{{ err }}</p>

    <div style="margin-top: 12px">
      <div
        v-for="o in rows" :key="o.no" class="ticket" :data-st="o.status"
        @click="router.push(`/student/detail/${o.no}`)"
      >
        <div class="tx">
          <div class="r1">
            <b>{{ o.building }} {{ o.floor }}F-{{ o.room }}</b>
            <StatusPill :status="o.status" />
          </div>
          <div class="r2">{{ o.desc }}</div>
          <div class="r3">
            {{ o.no }} · {{ o.category }}
            <template v-if="o.reworkOf"> · 返修 R{{ o.reworkIndex }}</template>
          </div>
          <div v-if="left(o)" class="r4">{{ left(o) }}</div>
        </div>
      </div>

      <div v-if="!loading && !rows.length" class="card empty">
        {{ tab === 'DONE' ? '还没有完工的报修' : '这一栏是空的' }}
      </div>
    </div>
  </div>

  <BottomNav role="student" />
</template>

<style scoped>
.top { display: flex; align-items: center; justify-content: space-between; padding: 13px 16px 10px; font-size: 16px; }
.err { color: var(--danger); font-size: 13px; }
.r1 { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.r1 b { font-size: 14px; }
.r2 {
  font-size: 12.5px; color: var(--ink-2); margin: 4px 0 3px;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.r3 { font-size: 11px; color: var(--ink-3); font-variant-numeric: tabular-nums; }
.r4 {
  margin-top: 7px; padding-top: 7px; border-top: 1px dashed var(--line);
  font-size: 11.5px; color: var(--orange); font-variant-numeric: tabular-nums;
}
</style>
