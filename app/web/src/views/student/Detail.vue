<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import StatusPill from '../../components/StatusPill.vue'
import LevelPill from '../../components/LevelPill.vue'
import { ordersApi } from '../../api/orders'
import { describeError } from '../../api/request'
import { EVENT, shortWindow, shortLeft, steps } from '../../constants/status'
import { timersOf, toneOf } from '../../constants/timers'

const route = useRoute()
const router = useRouter()
const no = route.params.no || route.query.no
const o = ref(null)
const meta = ref(null)
const events = ref([])
const loading = ref(true)
const err = ref('')
const busy = ref('')
const nowTick = ref(Date.now())
const sheet = ref(null) // 'accept' | 'reject' | 'cancel' | 'cancelRequest'

const form = ref({ signerName: '', reason: '' })
let timer = null

const tm = computed(() => timersOf(o.value, meta.value))
const stepList = computed(() => (o.value ? steps(o.value) : []))
const canCancel = computed(() => o.value && (o.value.status === 'PENDING' || (o.value.status === 'ASSIGNED' && !o.value.arriveAt)))
const afterPhotos = computed(() => (o.value?.photosAfter || []).length)
const mismatch = computed(
  () => o.value && ['VERIFYING', 'DONE', 'CLOSED'].includes(o.value.status) && afterPhotos.value === 0
)

async function load () {
  try {
    const [d, m] = await Promise.all([ordersApi.detail(no), ordersApi.meta()])
    o.value = d
    meta.value = m
    events.value = d.events || []
    err.value = ''
  } catch (e) {
    err.value = describeError(e)
  } finally {
    loading.value = false
  }
}

async function fire (path, body, tag) {
  busy.value = tag
  err.value = ''
  try {
    const r = await path(o.value.no, { ...body, version: o.value.version })
    sheet.value = null
    form.value = { signerName: '', reason: '' }
    if (tag === 'reject' && r && r.no) {
      router.replace(`/student/detail/${r.no}`)
      return
    }
    await load()
  } catch (e) {
    err.value = describeError(e)
    // 409 说明别人动过这张单，顺手刷一下，别让学生看着旧状态反复点
    if (e.status === 409) load()
  } finally {
    busy.value = ''
  }
}

function openSheet (kind) {
  form.value = { signerName: o.value.studentName || '', reason: '' }
  sheet.value = kind
}

function confirmSheet () {
  if (sheet.value === 'accept') {
    if (!form.value.signerName.trim()) return
    fire(ordersApi.accept, { signerName: form.value.signerName.trim() }, 'accept')
  } else if (sheet.value === 'reject') {
    if (form.value.reason.trim().length < 2) return
    fire(ordersApi.reject, { reason: form.value.reason.trim() }, 'reject')
  } else if (sheet.value === 'cancel') {
    fire(ordersApi.cancel, { reason: form.value.reason.trim() || '不需要修了' }, 'cancel')
  } else if (sheet.value === 'cancelRequest') {
    if (form.value.reason.trim().length < 2) return
    fire(ordersApi.cancelRequest, { reason: form.value.reason.trim() }, 'cancelRequest')
  }
}

function fmtAt (s) {
  return s ? String(s).slice(5, 16) : ''
}

onMounted(() => {
  load()
  timer = setInterval(() => { nowTick.value = Date.now() }, 1000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <header class="top">
    <button class="back" @click="router.push('/student/list')">‹ 我的报修</button>
    <span class="tiny muted num">{{ no }}</span>
  </header>

  <div v-if="loading" class="empty">加载中…</div>
  <div v-else-if="!o" class="empty">{{ err || '找不到这张单' }}</div>

  <div v-else class="body has-dock">
    <div class="card">
      <div class="between">
        <div>
          <div class="loc">{{ o.building }} {{ o.floor }}F-{{ o.room }}</div>
          <div class="tiny muted">{{ o.category }} · {{ shortWindow(o.window) || '全天' }}</div>
        </div>
        <div class="row"><LevelPill :level="o.level" /><StatusPill :status="o.status" /></div>
      </div>
      <p class="desc">{{ o.desc }}</p>

      <div class="steps">
        <div v-for="(s, i) in stepList" :key="i" class="st" :data-s="s.state">
          <i></i><span>{{ s.label }}</span>
        </div>
      </div>
    </div>

    <!-- 两颗计时器：起点不一样，不要合成一颗 -->
    <div v-if="tm.t1" class="card">
      <div class="card-hd"><h3>学生还差多久自动验收</h3><span class="hint">T1</span></div>
      <div class="timerbox" :data-tone="toneOf(tm.t1.leftMs, tm.t1.total)">
        <div>
          <div class="lb">{{ tm.t1.label }}</div>
          <div class="sub">起点：师傅完工 {{ tm.t1.from }}</div>
        </div>
        <div class="val num">{{ shortLeft(tm.t1.leftMs) }}</div>
      </div>
      <div class="note" data-tone="info" style="margin-top: 9px">
        满 {{ meta?.verifyHours ?? 168 }} 小时没回应，系统自己确认完成，记成"系统自动"，不算人工。
      </div>
    </div>

    <div v-if="tm.t2" class="card">
      <div class="card-hd"><h3>这单还有多久归档</h3><span class="hint">T2</span></div>
      <div class="timerbox" :data-tone="toneOf(tm.t2.leftMs, tm.t2.total)">
        <div>
          <div class="lb">{{ tm.t2.label }}</div>
          <div class="sub">起点：学生签字 {{ tm.t2.from }}</div>
        </div>
        <div class="val num">{{ shortLeft(tm.t2.leftMs) }}</div>
      </div>
      <div class="note" style="margin-top: 9px">
        满 {{ meta?.archiveDays ?? 7 }} 天转只读。归档之后管理端才回看，不回改在办的单。
      </div>
    </div>

    <div class="card">
      <div class="card-hd"><h3>谁在处理</h3></div>
      <div class="kv"><span class="k">师傅</span><span class="v">{{ o.workerName || '还没派到' }}</span></div>
      <div class="kv"><span class="k">派工</span><span class="v num">{{ o.assignedAt || '—' }}</span></div>
      <div class="kv"><span class="k">到门</span><span class="v num">{{ o.arriveAt || '—' }}</span></div>
      <div class="kv"><span class="k">完工</span><span class="v num">{{ o.finishAt || '—' }}</span></div>
      <div v-if="o.signAt" class="kv"><span class="k">签字</span><span class="v num">{{ o.signAt }}</span></div>
    </div>

    <div class="card">
      <div class="card-hd"><h3>照片</h3><span class="hint">现场 {{ (o.photosBefore || []).length }} 张 · 完工 {{ afterPhotos }} 张</span></div>
      <div class="thumbs">
        <div v-for="(p, i) in o.photosBefore" :key="p" class="thumb" data-tone="ok">
          <span>📷</span><span>现场 {{ i + 1 }}</span>
        </div>
        <div v-for="(p, i) in o.photosAfter" :key="p" class="thumb" data-tone="ok">
          <span>📸</span><span>完工 {{ i + 1 }}</span>
        </div>
        <div v-if="mismatch" class="thumb" data-tone="bad">
          <span>⚠️</span><span>缺完工照</span>
        </div>
        <div v-else-if="!afterPhotos" class="thumb">
          <span>📷</span><span>等师傅上传</span>
        </div>
      </div>
      <div v-if="mismatch" class="note" data-tone="bad" style="margin-top: 9px">
        这单已经到「{{ o.statusLabel }}」，但一张完工照都没有。它会出现在管理端「数据对不上」清单里。
      </div>
    </div>

    <div v-if="o.partsName" class="card">
      <div class="card-hd"><h3>缺的配件</h3><span class="hint">{{ o.partsEta || '' }}</span></div>
      <div class="money">
        <div><div class="n">{{ o.partsName }}</div><div class="k">配件</div></div>
        <div>
          <div class="n num" :class="{ hot: Number(o.partsAmount) > (meta?.budgetLine ?? 200) }">¥{{ o.partsAmount }}</div>
          <div class="k">金额</div>
        </div>
        <div><div class="n num">¥{{ meta?.budgetLine ?? 200 }}</div><div class="k">预算线</div></div>
      </div>
      <div v-if="Number(o.partsAmount) > (meta?.budgetLine ?? 200)" class="note" data-tone="warn" style="margin-top: 9px">
        超过预算线，这笔采购会进管理端「超预算采购」清单等审批。
      </div>
    </div>

    <div class="card">
      <div class="card-hd"><h3>系统怎么判的</h3><span class="hint">只读</span></div>
      <div class="kv"><span class="k">故障类型</span><span class="v">{{ o.category }}</span></div>
      <div class="kv"><span class="k">紧急程度</span><span class="v">{{ o.level }}（{{ o.levelSource === 'FALLBACK' ? 'AI 不可用，走的兜底规则' : o.levelSource === 'MANUAL' ? '人工指定' : 'AI 判定' }}）</span></div>
      <div class="kv"><span class="k">判定依据</span><span class="v">{{ o.aiBasis }}</span></div>
      <div class="note" style="margin-top: 9px">判定依据不许事后改，改了就没法复盘 AI 准不准。</div>
    </div>

    <div class="card">
      <div class="card-hd"><h3>这单经过</h3><span class="hint">共 {{ events.length }} 步</span></div>
      <div class="tl">
        <div
          v-for="(e, i) in events" :key="e.seq || i" class="ev"
          :data-s="i === events.length - 1 ? 'now' : 'done'"
        >
          <div class="t num">{{ fmtAt(e.at) }}</div>
          <div class="d">
            {{ EVENT[e.event] || e.event }}
            <span v-if="e.manual === 0" class="pill plain tiny" data-st="ASSIGNED">系统自动</span>
          </div>
          <div class="tiny muted">{{ e.actorName }}{{ e.note ? ' · ' + e.note : '' }}</div>
        </div>
      </div>
    </div>

    <p v-if="err" class="err">{{ err }}</p>

    <div v-if="o.cancelRequestAt || o.status === 'REPAIRING'" class="note" data-tone="warn">
      师傅已经进楼了，这单你自己撤不了。要撤就点「申请撤销」，管理员看了再决定作废还是补师傅工钱。
    </div>
  </div>

  <!-- 动作条：按状态给唯一该做的事 -->
  <div v-if="o" class="dock">
    <template v-if="canCancel">
      <button class="dbtn ghost" @click="openSheet('cancel')">撤销报修</button>
      <button class="dbtn primary" @click="router.push('/student/list')">回到列表</button>
    </template>
    <template v-else-if="o.status === 'REPAIRING'">
      <button class="dbtn" :disabled="busy === 'cancelRequest'" @click="openSheet('cancelRequest')">
        {{ busy === 'cancelRequest' ? '提交中…' : '申请撤销' }}
      </button>
    </template>
    <template v-else-if="o.status === 'VERIFYING'">
      <button class="dbtn ghost" @click="openSheet('reject')">还没修好</button>
      <button class="dbtn primary" @click="openSheet('accept')">确认修好并签字</button>
    </template>
    <template v-else>
      <div class="readonly">{{ o.statusLabel }}{{ o.status === 'DONE' ? ' · 还在归档倒计时里' : o.status === 'CLOSED' ? ' · 已归档只读' : '' }}</div>
    </template>
  </div>

  <!-- 弹层：签字 / 打回 / 撤销 -->
  <div v-if="sheet" class="mask" @click.self="sheet = null">
    <div class="sheet">
      <div class="sheet-hd">
        <b>{{ sheet === 'accept' ? '确认修好并签字' : sheet === 'reject' ? '还没修好' : sheet === 'cancel' ? '撤销报修' : '申请撤销' }}</b>
        <button @click="sheet = null">取消</button>
      </div>

      <template v-if="sheet === 'accept'">
        <label class="lb">签字人</label>
        <input v-model="form.signerName" class="in" placeholder="签谁的名字" />
        <p class="note" data-tone="info" style="margin-top: 9px">
          签完这单就完成，开始算 {{ meta?.archiveDays ?? 7 }} 天归档倒计时。签完不能再改。
        </p>
      </template>

      <template v-else-if="sheet === 'reject'">
        <label class="lb">哪儿还不行</label>
        <textarea v-model="form.reason" rows="3" class="in ta" placeholder="举个例子：还在滴水，地上还是湿的" />
        <p class="note" data-tone="warn" style="margin-top: 9px">
          打回会生成一张返修单（-R1），紧急程度升一档，原单的历史和照片都带过去。
        </p>
      </template>

      <template v-else-if="sheet === 'cancel'">
        <label class="lb">为什么撤（可不写）</label>
        <div class="chips">
          <button
            v-for="r in (meta?.cancelReasons || ['不需要修了', '重复报修', '自己已经修好', '信息填错'])"
            :key="r" class="chip" :class="{ on: form.reason === r }" @click="form.reason = r"
          >{{ r }}</button>
        </div>
        <p class="note" style="margin-top: 9px">师傅还没上门，撤了就是撤了，不计入任何考核。</p>
      </template>

      <template v-else>
        <label class="lb">情况说明</label>
        <textarea v-model="form.reason" rows="3" class="in ta" placeholder="师傅已经进楼了，说明一下为什么想撤" />
        <p class="note" data-tone="warn" style="margin-top: 9px">
          这条不改状态，只是把申请记下来给管理员看。作废还是给师傅补工钱，由管理员决定（接口在这个分支返回 501，等变更单 CR-001）。
        </p>
      </template>

      <button
        class="submit"
        :disabled="!!busy || (sheet === 'accept' && !form.signerName.trim()) || ((sheet === 'reject' || sheet === 'cancelRequest') && form.reason.trim().length < 2)"
        @click="confirmSheet"
      >{{ busy ? '提交中…' : '确定' }}</button>
      <p v-if="err" class="err">{{ err }}</p>
    </div>
  </div>

  <BottomNav role="student" />
</template>

<style scoped>
.top { display: flex; align-items: center; justify-content: space-between; padding: 13px 16px 8px; }
.back { border: 0; background: transparent; color: var(--ink-2); font-size: 14px; padding: 0; }
.loc { font-size: 17px; font-weight: 700; }
.desc { margin: 8px 0 12px; font-size: 13.5px; line-height: 1.65; color: var(--ink-2); }
.steps { display: grid; grid-template-columns: repeat(5, 1fr); gap: 2px; }
.steps .st { text-align: center; font-size: 10.5px; color: var(--ink-3); }
.steps .st i { display: block; height: 3px; border-radius: 2px; background: var(--line); margin-bottom: 6px; }
.steps .st[data-s="done"] i { background: var(--navy); }
.steps .st[data-s="now"] i { background: var(--orange); }
.steps .st[data-s="now"] span { color: var(--ink); font-weight: 650; }
.steps .st[data-s="done"] span { color: var(--ink-2); }
.dock {
  position: fixed; left: 10px; right: 10px; bottom: calc(var(--nav-h) + 10px + env(safe-area-inset-bottom, 0px));
  max-width: 410px; margin: 0 auto; display: flex; gap: 8px; padding: 9px;
  background: #fff; border-radius: 14px; box-shadow: var(--sh-md); z-index: 20;
}
.dbtn {
  flex: 1; height: 44px; border-radius: 11px; border: 1px solid var(--line);
  background: #fff; color: var(--ink); font-size: 14px; font-weight: 650;
}
.dbtn.primary { background: var(--navy); border-color: var(--navy); color: #fff; }
.dbtn.ghost { color: var(--ink-2); }
.readonly { flex: 1; text-align: center; font-size: 13px; color: var(--ink-3); padding: 13px 0; }
.err { color: var(--danger); font-size: 12.5px; margin: 8px 0 0; }
.mask {
  position: fixed; inset: 0; background: rgba(11, 21, 36, .42);
  display: flex; align-items: flex-end; justify-content: center; z-index: 50;
}
.sheet {
  width: 100%; max-width: 430px; background: #fff; border-radius: 18px 18px 0 0;
  padding: 16px 16px calc(20px + env(safe-area-inset-bottom, 0px));
}
.sheet-hd { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.sheet-hd b { font-size: 16px; }
.sheet-hd button { border: 0; background: transparent; color: var(--ink-3); font-size: 13px; }
.lb { font-size: 12.5px; color: var(--ink-3); }
.in {
  width: 100%; margin-top: 6px; border: 1px solid var(--line); border-radius: 11px;
  padding: 10px 12px; font-size: 14px; font-family: inherit; background: #fafbfd; color: var(--ink);
}
.in:focus { outline: 0; border-color: var(--navy); background: #fff; box-shadow: 0 0 0 3px rgba(18, 41, 75, .08); }
.ta { resize: none; line-height: 1.6; }
.submit {
  width: 100%; margin-top: 12px; height: 46px; border: 0; border-radius: 12px;
  background: var(--navy); color: #fff; font-size: 15px; font-weight: 650;
}
.submit:disabled { background: #c7d0dd; }
</style>
