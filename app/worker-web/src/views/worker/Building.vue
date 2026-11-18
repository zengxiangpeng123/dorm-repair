<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import Plate from '../../components/Plate.vue'
import StatusPill from '../../components/StatusPill.vue'
import { workerApi } from '../../api/orders'
import { userOf } from '../../api/session'
import { describeError } from '../../api/request'
import { WALK_STATUS, shortWindow } from '../../constants/status'

const router = useRouter()
const me = userOf()
const board = ref(null)
const meta = ref(null)
const loading = ref(true)
const err = ref('')
const busy = ref(false)
const toast = ref('')
let toastTimer = null

const all = computed(() => (board.value?.floors || []).flatMap((f) => f.orders || []))
const arrived = computed(() => all.value.some((o) => o.arriveAt))
// 只列要上楼的；等验收、返修的单不用跑腿，别塞进清单让人白爬楼
const floors = computed(() =>
  (board.value?.floors || [])
    .map((f) => ({ floor: f.floor, orders: (f.orders || []).filter((o) => WALK_STATUS.includes(o.status)) }))
    .filter((f) => f.orders.length)
)
const other = computed(() => all.value.filter((o) => !WALK_STATUS.includes(o.status)).length)
const count = computed(() => floors.value.reduce((n, f) => n + f.orders.length, 0))

function tone (o) {
  return o.status
}

async function load () {
  try {
    const m = meta.value || await workerApi.meta()
    meta.value = m
    const b = m.buildings.find((x) => x.id === me.building) || m.buildings[0]
    board.value = await workerApi.board(b.id, b.floors)
    err.value = ''
  } catch (e) {
    err.value = describeError(e)
  } finally {
    loading.value = false
  }
}

async function arriveBatch () {
  busy.value = true
  err.value = ''
  try {
    const r = await workerApi.arriveBuilding(me.building, {})
    toast.value = `已打卡 ${(r.ok || []).length} 单${(r.skipped || []).length ? `，${(r.skipped || []).length} 单跳过` : ''}`
    window.clearTimeout(toastTimer)
    toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
    await load()
  } catch (e) {
    err.value = describeError(e)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <header class="top">
    <button class="back" @click="router.push('/worker')">‹ 工作台</button>
    <b>{{ board?.buildingName || '本楼' }}</b>
    <span class="tiny muted">{{ count }} 单要上楼</span>
  </header>

  <div class="body has-dock">
    <p v-if="err" class="err">{{ err }}</p>

    <div v-if="!arrived" class="note" data-tone="warn">
      还没进楼。到楼门口点下面的按钮，这一批单一起记「到门」。
    </div>
    <div v-else class="note" data-tone="ok">已在楼内 · 按楼层从高往低走，一层一层来。</div>

    <div v-for="f in floors" :key="f.floor">
      <div class="floor-h">
        <b>{{ f.floor }}F</b><span>{{ f.orders.length }} 间</span>
      </div>
      <div
        v-for="o in f.orders" :key="o.no" class="ticket" :data-st="tone(o)"
        @click="router.push(`/worker/task/${o.no}`)"
      >
        <Plate :room="o.room" :floor="o.floor" :status="o.status" />
        <div class="tx">
          <div class="r1"><b>{{ o.room }} 室</b><StatusPill :status="o.status" /></div>
          <div class="r2">{{ o.desc }}</div>
          <div class="r3">{{ o.studentName }} · {{ shortWindow(o.window) || '全天' }} · {{ o.category }}</div>
        </div>
      </div>
    </div>

    <div v-if="board?.emptyFloors?.length" class="empty-mini">
      {{ board.emptyFloors.map((n) => n + 'F').join('、') }} 本层没有单，不用上去
    </div>
    <div v-if="other" class="tiny muted" style="margin-top: 10px">
      另有 {{ other }} 单在等学生验收或返修，不用上楼。
    </div>
    <div v-if="!loading && !count" class="card empty">本楼没有要上楼的活。</div>
  </div>

  <div class="dock">
    <button v-if="!arrived" class="dbtn primary" :disabled="busy" @click="arriveBatch">
      {{ busy ? '打卡中…' : '进楼打卡' }}
    </button>
    <button v-else class="dbtn" @click="router.push('/worker')">回工作台</button>
  </div>

  <div v-if="toast" class="toast">{{ toast }}</div>

  <BottomNav role="worker" />
</template>

<style scoped>
.top { display: flex; align-items: center; justify-content: space-between; padding: 13px 16px 8px; font-size: 15px; }
.back { border: 0; background: transparent; color: var(--ink-2); font-size: 14px; padding: 0; }
.err { color: var(--danger); font-size: 13px; }
.floor-h {
  display: flex; align-items: baseline; gap: 8px; margin: 18px 3px 9px;
}
.floor-h b { font-size: 14px; font-weight: 700; }
.floor-h span { font-size: 11.5px; color: var(--ink-3); }
.floor-h::after { content: ''; flex: 1; height: 1px; background: var(--line); }
.r1 { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.r1 b { font-size: 14px; }
.r2 {
  font-size: 12.5px; color: var(--ink-2); margin: 3px 0;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.r3 { font-size: 11px; color: var(--ink-3); }
.empty-mini { font-size: 12px; color: var(--ink-3); padding: 10px 3px 0; }
.dock {
  position: fixed; left: 10px; right: 10px; bottom: calc(var(--nav-h) + 10px + env(safe-area-inset-bottom, 0px));
  max-width: 410px; margin: 0 auto; padding: 9px; background: #fff; border-radius: 14px;
  box-shadow: var(--sh-md); z-index: 20;
}
.dbtn { width: 100%; height: 46px; border-radius: 11px; border: 1px solid var(--line); background: #fff; font-size: 15px; font-weight: 650; }
.dbtn.primary { background: var(--navy); border-color: var(--navy); color: #fff; }
.toast {
  position: fixed; left: 50%; transform: translateX(-50%); bottom: calc(var(--nav-h) + 76px);
  background: rgba(11,21,36,.92); color: #fff; padding: 9px 14px; border-radius: 10px; font-size: 13px; z-index: 40;
}
</style>
