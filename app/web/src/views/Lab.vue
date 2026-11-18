<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ordersApi } from '../api/orders'
import { EVENT as EVENT_CN, STATUS as STATUS_CN } from '../constants/status'
import { describeError } from '../api/request'

const router = useRouter()

const meta = ref({ statuses: [], events: [], transitions: [], buildings: [] })
const matrix = ref({ rows: [], rowCount: 0, eventCount: 0, total: 0, legal: 0 })
const orders = ref([])
const picked = ref('')
const result = ref(null)
const busy = ref('')
const booting = ref(true)
const bootErr = ref('')

const order = computed(() => orders.value.find(o => o.no === picked.value) || null)
const trs = ref([])
const transitions = computed(() => trs.value)

/** 这件事的合法触发方，来自 /state/transitions。 */
function triggerOf (ev) {
  const t = transitions.value.find(x => x.event === ev)
  return t ? t.trigger || [] : []
}

/** 这条边从当前状态出发存不存在。 */
function rowOf (st) {
  const row = (matrix.value.rows || []).find(r => r.from === st)
  return row ? row.cells || [] : []
}

const allowed = computed(() => {
  if (!order.value) return []
  const list = rowOf(order.value.status).filter(c => c.legal).map(c => ({ ev: c.event, to: c.to, trigger: triggerOf(c.event) }))
  // 申请撤销不在转移表里：它只留一条记录、不动状态，所以矩阵里是"非法"，但维修中确实能提交
  if (order.value.status === 'REPAIRING' && !list.some(x => x.ev === 'CANCEL_REQUEST')) {
    list.push({ ev: 'CANCEL_REQUEST', to: null, trigger: ['student'] })
  }
  return list
})

const rejected = computed(() => {
  if (!order.value) return []
  return rowOf(order.value.status).filter(c => !c.legal).map(c => ({ ev: c.event, trigger: triggerOf(c.event) }))
})

/** 事件 → 路径后缀。SUBMI 只能在建单时发生，这里不提供。 */
const EP = {
  ASSIGN: 'assign', ARRIVE: 'arrive', NO_SHOW: 'no-show', FINISH: 'finish',
  NEED_PARTS: 'need-parts', PARTS_ARRIVED: 'parts-arrived', ACCEPT: 'accept',
  REJECT: 'reject', CANCEL: 'cancel', CANCEL_REQUEST: 'cancel-request',
  AUTO_ACCEPT: 'auto-accept', ARCHIVE: 'archive'
}

function tomorrow () {
  const d = new Date(Date.now() + 86400000)
  return d.toISOString().slice(0, 10)
}

function payloadFor (ev) {
  switch (ev) {
    case 'NO_SHOW': return { reason: 'KNOCK_NO_ANSWER', note: '自检台演示' }
    case 'FINISH': return { photosAfter: ['demo://lab/after.jpg'], noteTags: ['更换软管'], note: '自检台演示' }
    case 'NEED_PARTS': return { partsName: '演示配件', partsAmount: 50, partsEta: tomorrow() }
    case 'ACCEPT': return { signerName: order.value?.studentName || '演示签字' }
    case 'REJECT': return { reason: '还没修好（自检台演示）' }
    case 'CANCEL': return { reason: '不需要修了' }
    case 'CANCEL_REQUEST': return { reason: '等太久了（自检台演示）' }
    case 'ASSIGN': return {}
    default: return {}
  }
}

/** 按触发方切身份 —— 这样拿到的才是真正的 409/422，而不是一律 403。 */
function optsFor (ev) {
  const trig = triggerOf(ev)
  if (trig.includes('worker')) return { role: 'worker', id: order.value?.workerId || null }
  if (trig.includes('student')) return { role: 'student', id: order.value?.studentId || null }
  if (trig.includes('admin')) return { role: 'admin', id: 'A001' }
  return { role: 'system', id: 'system' }
}

function labelOf (ev) {
  return EVENT_CN[ev] || ev
}

function stLabel (st) {
  return STATUS_CN[st] || st
}

async function push (ev) {
  const ep = EP[ev]
  if (!order.value || !ep) return
  const opts = optsFor(ev)
  if (opts.role === 'worker' && !opts.id) {
    result.value = { ok: false, code: 403, title: '这单还没派师傅', lines: ['点下去只会得到 403：这条边的触发方是师傅，而单子上没有师傅。'] }
    return
  }
  busy.value = ev
  const before = { status: order.value.status, version: order.value.version }
  try {
    const body = { ...payloadFor(ev), version: order.value.version }
    const r = await ordersApi[ep === 'assign' ? 'assign' : ep === 'arrive' ? 'arrive'
      : ep === 'no-show' ? 'noShow' : ep === 'finish' ? 'finish'
        : ep === 'need-parts' ? 'needParts' : ep === 'parts-arrived' ? 'partsArrived'
          : ep === 'accept' ? 'accept' : ep === 'reject' ? 'reject'
            : ep === 'cancel' ? 'cancel' : ep === 'cancel-request' ? 'cancelRequest' : 'adminCancel'](order.value.no, body, opts)
    result.value = {
      ok: true,
      code: 200,
      title: `${labelOf(ev)} 成功：${stLabel(before.status)} → ${stLabel(r.status)}`,
      lines: [`版本 v${before.version} → v${r.version}`, `状态时间 ${r.statusAt}`, `以「${opts.role}${opts.id ? ' ' + opts.id : ''}」身份提交`]
    }
  } catch (e) {
    const d = e.detail || {}
    const lines = []
    if (e.status === 409) {
      lines.push(d.message || '状态机不接受这条边')
      if (d.reason === 'VERSION_CONFLICT') {
        lines.push(`版本对不上：服务端是 v${d.serverVersion ?? d.gotVersion ?? '?'}，你手上是 v${d.clientVersion ?? d.expectVersion ?? '?'}`)
      } else if (d.allowed) {
        lines.push('这单现在只能做：' + (d.allowed || []).map(labelOf).join(' / '))
      }
      const now = await ordersApi.detail(order.value.no).catch(() => null)
      if (now) lines.push(`工单一个字没动：仍是 ${stLabel(now.status)}，version 仍是 ${now.version}`)
    } else if (e.status === 403) {
      lines.push(d.message || '这个身份不能调这个接口')
    } else if (e.status === 422) {
      lines.push(d.message || '前置条件不满足')
    } else {
      lines.push(describeError(e))
    }
    result.value = { ok: false, code: e.status, title: `${labelOf(ev)} 被拒（HTTP ${e.status}）`, lines }
  } finally {
    busy.value = ''
    await loadOrders()
  }
}

async function loadOrders () {
  try {
    const r = await ordersApi.list({ pageSize: 100 })
    orders.value = r.items || []
    if (!orders.value.find(o => o.no === picked.value)) picked.value = orders.value[0]?.no || ''
  } catch (e) {
    bootErr.value = describeError(e)
  }
}

onMounted(async () => {
  try {
    const [m, mx, tr] = await Promise.all([ordersApi.meta(), ordersApi.matrix(), ordersApi.transitions(), loadOrders()])
    meta.value = m
    matrix.value = mx
    trs.value = tr.items || []
  } catch (e) {
    bootErr.value = describeError(e)
  } finally {
    booting.value = false
  }
})
</script>

<template>
  <div class="desk">
    <aside class="desk-side">
      <div class="brand"><span class="mk">修</span><div><b>状态机自检台</b><i>{{ matrix.total || 130 }} 种组合，合法 {{ matrix.legal || 13 }} 条</i></div></div>
      <nav>
        <a href="#" class="on" @click.prevent>自检台</a>
        <button @click="router.push('/admin')">管理端</button>
        <button @click="router.push('/')">回到入口</button>
      </nav>
      <div class="side-foot">
        这里不做业务判断，只把状态机摆出来让人点。<br />出边是表里查的，不是页面自己写的。
      </div>
    </aside>

    <main class="desk-main">
      <header class="desk-top">
        <h1>状态机自检台</h1>
        <div class="hint">9 个状态 + 1 个新建入口，13 个事件，共 {{ matrix.total || 130 }} 种组合 —— 只有 {{ matrix.legal || 13 }} 条能走通</div>
        <div class="sp" />
        <button class="dbtn" @click="loadOrders">刷新</button>
      </header>

      <div class="desk-wrap">
        <p v-if="bootErr" class="note" data-tone="bad">{{ bootErr }}</p>
        <p v-else-if="booting" class="note">加载中…</p>

        <div class="lab-grid">
          <div class="col">
            <section class="panel">
              <header><b>选一张单手动推</b><span class="spacer" /><span class="hint">{{ orders.length }} 张</span></header>
              <div class="pn">
                <select v-model="picked" class="sel">
                  <option v-for="o in orders" :key="o.no" :value="o.no">
                    {{ o.no }} · {{ o.statusLabel }} · {{ o.building }} {{ o.room }}
                  </option>
                </select>
                <div v-if="order" class="cur">
                  <span class="pill" :data-st="order.status">{{ order.statusLabel }}</span>
                  <span class="v">version {{ order.version }}</span>
                  <span class="v">{{ order.building }} {{ order.floor }}F-{{ order.room }}</span>
                  <span v-if="order.workerName" class="v">师傅 {{ order.workerName }}</span>
                  <span v-else class="v warn">没派师傅</span>
                </div>
              </div>
            </section>

            <section class="panel">
              <header><b>允许的事件</b><span class="spacer" /><span class="hint">点下去应该成功</span></header>
              <div class="pn">
                <button v-for="a in allowed" :key="a.ev" class="evbtn" :disabled="busy === a.ev" @click="push(a.ev)">
                  <span class="l1">{{ labelOf(a.ev) }}<template v-if="a.to"> → {{ stLabel(a.to) }}</template><template v-else> · 只留记录</template></span>
                  <span class="l2">{{ a.ev }} · {{ (a.trigger || []).join('/') }}</span>
                </button>
                <p v-if="!allowed.length" class="empty">这单现在没有能走的路（终态）。</p>
              </div>
            </section>

            <section class="panel">
              <header><b>会被拒的事件</b><span class="spacer" /><span class="hint">点下去应该 409</span></header>
              <div class="pn">
                <button
                  v-for="r in rejected" :key="r.ev" class="evbtn bad"
                  :disabled="busy === r.ev || (r.trigger.includes('worker') && !order?.workerId)"
                  @click="push(r.ev)"
                >
                  <span class="l1">{{ labelOf(r.ev) }}</span>
                  <span class="l2">
                    {{ r.ev }} · {{ r.trigger.includes('worker') && !order?.workerId ? '这单还没派师傅：只会得到 403' : '状态机说这条边不存在' }}
                  </span>
                </button>
              </div>
              <div class="panel-foot">
                409 = 状态机说这条边不存在；403 = 你这个身份不能调这个接口；422 = 边有，但前置条件不满足。三种错不能混。
              </div>
            </section>

            <section v-if="result" class="panel res" :class="result.ok ? 'ok' : 'bad'">
              <header><b>{{ result.title }}</b><span class="spacer" /><span class="hint">HTTP {{ result.code }}</span></header>
              <div class="pn">
                <p v-for="(l, i) in result.lines" :key="i" class="rl">{{ l }}</p>
              </div>
            </section>
          </div>

          <div class="col">
            <section class="panel">
              <header>
                <b>全表自检</b><span class="spacer" />
                <span class="hint">{{ matrix.total }} 种组合 · 合法 {{ matrix.legal }} 条</span>
              </header>
              <div class="pn pn0">
                <div class="mxwrap">
                  <table class="mx">
                  <thead>
                    <tr>
                      <th class="sticky">从 \ 事件</th>
                      <th v-for="e in (matrix.rows[0]?.cells || [])" :key="e.event"><span>{{ e.event }}</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in matrix.rows" :key="r.from">
                      <th class="sticky">{{ stLabel(r.from) }}<i>{{ r.from }}</i></th>
                      <td v-for="c in r.cells" :key="c.event" :class="{ ok: c.legal }">
                        <template v-if="c.legal">→ {{ stLabel(c.to) }}</template>
                      </td>
                    </tr>
                  </tbody>
                  </table>
                </div>
              </div>
              <div class="panel-foot">
                绿色是能走通的。其余 {{ matrix.total - matrix.legal }} 种一律拒绝，返回 HTTP 409 —— 页面不自己判，全看这张表。
              </div>
            </section>

            <section class="panel">
              <header><b>13 条合法转移</b><span class="spacer" /><span class="hint">SYS-004 第五条</span></header>
              <div class="pn pn0">
                <table class="dt tr">
                  <thead><tr><th>#</th><th>从</th><th>事件</th><th>到</th><th>谁来触发</th></tr></thead>
                  <tbody>
                    <tr v-for="(t, i) in transitions" :key="t.event + t.from">
                      <td class="n">{{ i + 1 }}</td>
                      <td>{{ stLabel(t.from) }}</td>
                      <td class="ev">{{ labelOf(t.event) }}<i>{{ t.event }}</i></td>
                      <td>{{ stLabel(t.to) }}</td>
                      <td class="who">{{ (t.trigger || []).join(' / ') }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.lab-grid { display: grid; grid-template-columns: 372px minmax(0, 1fr); gap: 16px; align-items: start; }
.col { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.sel { width: 100%; height: 36px; border: 1px solid var(--line); border-radius: 8px; font-size: 12.5px; padding: 0 8px; }
.cur { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 10px; }
.v { font-size: 11.5px; color: var(--ink-3); }
.v.warn { color: var(--warn); }
.evbtn {
  display: block; width: 100%; text-align: left; height: auto; margin-bottom: 8px;
  padding: 9px 12px; line-height: 1.35; border: 1px solid var(--line); border-radius: 9px;
  background: #fff; cursor: pointer;
}
.evbtn:hover { border-color: #c7d0dd; background: #fafbfd; }
.evbtn:disabled { opacity: .55; cursor: not-allowed; }
.evbtn .l1 { display: block; font-size: 12.5px; font-weight: 650; }
.evbtn .l2 { display: block; font-size: 11px; color: var(--ink-3); margin-top: 2px; }
.evbtn.bad .l1 { color: var(--danger); }
.res.ok { border-left: 3px solid var(--ok); }
.res.bad { border-left: 3px solid var(--danger); }
.rl { margin: 0 0 5px; font-size: 12.5px; line-height: 1.7; }
.empty { font-size: 12.5px; color: var(--ink-3); margin: 4px 0 0; }
.pn0 { padding: 0; }
.mxwrap { overflow-x: auto; }
.mx { min-width: 860px; }
.mx { width: 100%; border-collapse: collapse; font-size: 11px; }
.mx th, .mx td { border-bottom: 1px solid #f3f6fa; padding: 5px 4px; text-align: center; }
.mx thead th { height: 78px; vertical-align: bottom; font-weight: 500; color: var(--ink-3); }
.mx thead th span {
  display: block; transform: rotate(-58deg); transform-origin: bottom left;
  font-size: 9.5px; white-space: nowrap; width: 52px; margin-left: 26px;
}
.mx .sticky { text-align: left; width: 92px; }
.mx .sticky i { display: block; font-size: 9px; color: var(--ink-3); font-style: normal; }
.mx td.ok { background: #ecfdf3; color: #067647; font-weight: 600; }
.mx tbody tr:hover td { background: #fafbfd; }
.mx tbody tr:hover td.ok { background: #d7f5e3; }
.tr td, .tr th { padding: 6px 10px; font-size: 11.5px; }
.tr .n { color: var(--ink-3); width: 26px; }
.tr .ev { font-weight: 600; }
.tr .ev i { display: block; font-style: normal; font-size: 9.5px; color: var(--ink-3); font-weight: 400; }
.tr .who { text-align: right; color: var(--ink-3); }
@media (max-width: 1100px) { .lab-grid { grid-template-columns: 1fr; } }
</style>
