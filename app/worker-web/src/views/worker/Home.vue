<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import Plate from '../../components/Plate.vue'
import StatusPill from '../../components/StatusPill.vue'
import { workerApi } from '../../api/orders'
import { userOf } from '../../api/session'
import { describeError } from '../../api/request'
import { shortWindow, shortLeft } from '../../constants/status'
import { timersOf } from '../../constants/timers'

const router = useRouter()
const me = userOf()
const todo = ref(null)
const meta = ref(null)
const loading = ref(true)
const err = ref('')
const busy = ref(false)

const ORDER = { REPAIRING: 0, ASSIGNED: 1, PARTS: 2, PENDING: 3 }
const hasCancelRequest = (o) => (o.events || []).some((e) => e.event === 'CANCEL_REQUEST')

const buildings = computed(() => todo.value?.buildings || [])
const summary = computed(() => todo.value?.summary || { todo: 0, repairing: 0, parts: 0 })
const main = computed(() => buildings.value[0] || null)
const arrived = computed(() => !!main.value?.arrived && main.value.orders.some((o) => o.arriveAt))

const queue = computed(() => {
  const list = [...(main.value?.orders || [])]
  return list.sort((a, b) => (ORDER[a.status] ?? 9) - (ORDER[b.status] ?? 9) || b.floor - a.floor)
})
const next = computed(() => queue.value[0] || null)

const doneCount = computed(() => main.value?.progress?.done ?? 0)
const totalCount = computed(() => main.value?.progress?.total ?? 0)

const stayed = computed(() => {
  if (!next.value) return ''
  const t = timersOf(next.value, meta.value)
  return t.stayedMs ? shortLeft(t.stayedMs) : ''
})

async function load () {
  try {
    const [t, m] = await Promise.all([workerApi.workerTodo(), workerApi.meta()])
    todo.value = t
    meta.value = m
    err.value = ''
  } catch (e) {
    err.value = describeError(e)
  } finally {
    loading.value = false
  }
}

async function arriveBatch () {
  if (!main.value) return
  busy.value = true
  err.value = ''
  try {
    const r = await workerApi.arriveBuilding(main.value.buildingId, {})
    const ok = (r.ok || []).length
    const skipped = (r.skipped || []).length
    await load()
    alertSkip(ok, skipped)
  } catch (e) {
    err.value = describeError(e)
  } finally {
    busy.value = false
  }
}

function alertSkip (ok, skipped) {
  // 用最笨的办法报结果：一次点击打进几单，本来就是一目了然的事
  window.setTimeout(() => {}, 0)
  toast.value = `已打卡 ${ok} 单${skipped ? `，${skipped} 单已在楼内跳过` : ''}`
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
}
const toast = ref('')
let toastTimer = null

onMounted(load)
</script>

<template>
  <header class="worktop">
    <div class="who">
      <div>
        <div class="name">{{ me.name }}</div>
        <div class="team">{{ me.team }} · 负责{{ main?.buildingName || me.building || '待分配楼栋' }}</div>
      </div>
      <div class="badge">{{ arrived ? '已在楼内' : '还没进楼' }}</div>
    </div>
    <div class="tally">
      <div><div class="n num">{{ summary.todo }}</div><div class="k">待我上门</div></div>
      <div><div class="n num">{{ summary.repairing }}</div><div class="k">正在修</div></div>
      <div><div class="n num">{{ summary.parts }}</div><div class="k">等配件</div></div>
      <div><div class="n num">{{ doneCount }}</div><div class="k">本楼已完</div></div>
    </div>
  </header>

  <div class="body has-dock">
    <p v-if="err" class="err">{{ err }}</p>

    <div v-if="next" class="card next">
      <div class="between">
        <span class="tiny muted">下一间</span>
        <StatusPill :status="next.status" />
      </div>
      <div class="nx">
        <Plate :room="next.room" :floor="next.floor" :status="next.status" large />
        <div class="nx-tx">
          <div class="nx-r">{{ next.building }} {{ next.floor }}F-{{ next.room }}</div>
          <div class="nx-d">{{ next.desc }}</div>
          <div class="nx-m">
            {{ next.studentName }} · {{ shortWindow(next.window) || '全天' }}
            <template v-if="stayed"> · 已停留 {{ stayed }}</template>
          </div>
        </div>
      </div>
      <button class="go" @click="router.push(`/worker/task/${next.no}`)">
        {{ next.status === 'REPAIRING' ? '进去接着修' : next.status === 'ASSIGNED' ? '到门打卡' : '打开这单' }}
      </button>
    </div>

    <div v-else-if="!loading" class="card empty">
      本楼没有要上楼的活。等学生报修，系统会自己派过来。
    </div>

    <div class="section-title" v-if="queue.length > 1">本楼还有 {{ queue.length - 1 }} 单</div>
    <div
      v-for="o in queue.slice(1)" :key="o.no" class="ticket" :data-st="o.status"
      @click="router.push(`/worker/task/${o.no}`)"
    >
      <Plate :room="o.room" :floor="o.floor" :status="o.status" />
      <div class="tx">
        <div class="r1">
          <b>{{ o.floor }}F-{{ o.room }}</b>
          <StatusPill :status="o.status" />
        </div>
        <div class="r2">{{ o.desc }}</div>
        <div class="r3">{{ o.category }} · {{ o.level }} · {{ shortWindow(o.window) || '全天' }}</div>
        <div v-if="hasCancelRequest(o)" class="tiny" style="color: var(--orange); margin-top: 3px">学生申请撤销，等管理员定</div>
      </div>
    </div>
  </div>

  <div class="dock">
    <button v-if="!arrived" class="dbtn primary" :disabled="busy" @click="arriveBatch">
      {{ busy ? '打卡中…' : `进楼打卡 · ${main?.buildingName || '本楼'}` }}
    </button>
    <button v-else class="dbtn" @click="router.push('/worker/building')">看本楼清单</button>
  </div>

  <div class="tip" data-tone="info">
    进楼打卡一次，本批的单一起记「到门」。学生看到的就是"师傅已进楼"。
  </div>

  <div v-if="toast" class="toast">{{ toast }}</div>

  <BottomNav role="worker" />
</template>

<style scoped>
.worktop { background: var(--navy); color: #fff; padding: 16px 16px 18px; }
.badge { font-size: 11px; padding: 4px 9px; border-radius: 999px; background: rgba(255,255,255,.14); }
.err { color: var(--danger); font-size: 13px; }
.next { border-radius: 14px; box-shadow: var(--sh-md); }
.nx { display: flex; gap: 12px; align-items: center; margin: 9px 0 12px; }
.nx-tx { min-width: 0; }
.nx-r { font-size: 17px; font-weight: 700; }
.nx-d {
  font-size: 12.5px; color: var(--ink-2); margin: 3px 0 4px;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.nx-m { font-size: 11.5px; color: var(--ink-3); }
.go {
  width: 100%; height: 44px; border: 0; border-radius: 11px; background: var(--orange);
  color: #fff; font-size: 15px; font-weight: 650;
}
.ticket { align-items: center; }
.r1 { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.r1 b { font-size: 14px; }
.r2 {
  font-size: 12.5px; color: var(--ink-2); margin: 3px 0 3px;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.r3 { font-size: 11px; color: var(--ink-3); }
.dock {
  position: fixed; left: 10px; right: 10px; bottom: calc(var(--nav-h) + 10px + env(safe-area-inset-bottom, 0px));
  max-width: 410px; margin: 0 auto; padding: 9px; background: #fff;
  border-radius: 14px; box-shadow: var(--sh-md); z-index: 20;
}
.dbtn { width: 100%; height: 46px; border-radius: 11px; border: 1px solid var(--line); background: #fff; font-size: 15px; font-weight: 650; }
.dbtn.primary { background: var(--navy); border-color: var(--navy); color: #fff; }
.tip {
  margin: 0 16px 12px; padding: 10px 12px; border-radius: 10px; font-size: 12px; line-height: 1.6;
  background: #eff4ff; border: 1px solid #d1e0ff; color: #1a3fa8;
}
.toast {
  position: fixed; left: 50%; transform: translateX(-50%);
  bottom: calc(var(--nav-h) + 76px); background: rgba(11,21,36,.92); color: #fff;
  padding: 9px 14px; border-radius: 10px; font-size: 13px; z-index: 40;
}
</style>
