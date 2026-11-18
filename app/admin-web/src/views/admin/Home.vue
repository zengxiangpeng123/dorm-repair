<script setup>
import { computed, onMounted, ref } from 'vue'
import StatusPill from '../../components/StatusPill.vue'
import LevelPill from '../../components/LevelPill.vue'
import ProtectedImage from '../../components/ProtectedImage.vue'
import { adminApi } from '../../api/orders'
import { describeError } from '../../api/request'
import { shortLeft, STATUS } from '../../constants/status'
import { timersOf } from '../../constants/timers'

const dash = ref(null)
const ex = ref(null)
const orders = ref([])
const cfg = ref(null)
const meta = ref(null)
const reviews = ref({ items: [], total: 0 })
const reviewSel = ref(null)
const reviewDecision = ref('APPROVE')
const reviewReason = ref('')
const err = ref('')
const busy = ref('')
const cfgDraft = ref({})

const kindLabel = computed(() => {
  const m = {}
  for (const k of meta.value?.exceptionKinds || []) m[k.code] = k.label
  return m
})
const active = computed(() => orders.value.filter((o) => !['DONE', 'CLOSED', 'CANCELLED'].includes(o.status)))
const archive = computed(() => orders.value.filter((o) => ['DONE', 'CLOSED'].includes(o.status)).slice(0, 6))

function leftOf (o) {
  const t = timersOf(o, meta.value)
  const one = t.t1 || t.t2
  if (!one) return ''
  return `${t.t1 ? 'T1' : 'T2'} ${shortLeft(one.leftMs)}`
}

function manualCount (o) {
  return (o.events || []).filter((e) => e.manual === 1).length
}

async function load () {
  try {
    const [d, e, o, c, m, rv] = await Promise.all([
      adminApi.dashboard(),
      adminApi.exceptions(),
      adminApi.list({ pageSize: 200 }),
      adminApi.config(),
      adminApi.meta(),
      adminApi.reworkReviews()
    ])
    dash.value = d
    ex.value = e
    orders.value = o.items || o || []
    cfg.value = c
    cfgDraft.value = { budgetLine: c.budgetLine, verifyHours: c.verifyHours, archiveDays: c.archiveDays }
    meta.value = m
    reviews.value = rv || { items: [], total: 0 }
    err.value = ''
  } catch (e2) {
    err.value = describeError(e2)
  }
}

async function saveCfg () {
  busy.value = 'cfg'
  err.value = ''
  try {
    cfg.value = await adminApi.saveConfig(cfgDraft.value)
    await load()
  } catch (e) {
    err.value = describeError(e)
  } finally {
    busy.value = ''
  }
}

async function partsArrived (item) {
  busy.value = 'parts-' + item.no
  err.value = ''
  try {
    await adminApi.partsArrived(item.no, { version: item.version })
    keeper.value = `${item.no} 配件已确认到货，工单已回到待派工`
    window.clearTimeout(keeperTimer)
    keeperTimer = window.setTimeout(() => { keeper.value = '' }, 4000)
    await load()
  } catch (e) {
    err.value = describeError(e)
    if (e.status === 409) await load()
  } finally {
    busy.value = ''
  }
}

async function tryCancel (item) {
  busy.value = item.no
  err.value = ''
  try {
    await adminApi.adminCancel(item.no, { version: item.version })
  } catch (e) {
    // 这里就是要 501：这条边还没批变更单 CR-001
    err.value = describeError(e)
  } finally {
    busy.value = ''
  }
}

function openReview (item, decision) {
  reviewSel.value = item
  reviewDecision.value = decision
  reviewReason.value = ''
}

async function submitReview () {
  if (!reviewSel.value || reviewReason.value.trim().length < 2) return
  busy.value = 'review-' + reviewSel.value.id
  err.value = ''
  try {
    const decidedNo = reviewSel.value.no
    const decision = reviewDecision.value
    const result = await adminApi.reworkReview(decidedNo, {
      version: reviewSel.value.version,
      decision,
      reason: reviewReason.value.trim()
    })
    reviewSel.value = null
    reviewReason.value = ''
    keeper.value = decision === 'APPROVE'
      ? `审查通过：已生成 ${result.reworkOrder?.no || '返修单'}`
      : `申请已驳回：${decidedNo} 继续验收/归档`
    window.clearTimeout(keeperTimer)
    keeperTimer = window.setTimeout(() => { keeper.value = '' }, 5000)
    await load()
  } catch (e2) {
    err.value = describeError(e2)
    if (e2.status === 409) await load()
  } finally {
    busy.value = ''
  }
}

function keep (item) {
  busy.value = ''
  window.setTimeout(() => {}, 0)
  alertKeeper(item)
}
const keeper = ref('')
function alertKeeper (item) {
  keeper.value = `${item.no} 先不动，等师傅报完工再看过`
  window.clearTimeout(keeperTimer)
  keeperTimer = window.setTimeout(() => { keeper.value = '' }, 2600)
}
let keeperTimer = null

onMounted(load)
</script>

<template>
  <div class="desk">
    <aside class="desk-side">
      <div class="brand">
        <div class="logo">修</div>
        <div>
          <div class="bt">宿舍维修系统</div>
          <div class="bs">后勤维修部 · 管理端</div>
        </div>
      </div>
      <nav>
        <a href="#" class="on">监测台</a>
        <a href="#" @click.prevent="load">刷新数据</a>
      </nav>
      <div class="side-foot">
        督察守则：只做返修审查与例外决策。<br />正常工单不催办、不改道。
      </div>
    </aside>

    <main class="desk-main">
      <div class="desk-top">
        <div>
          <div class="t1">监测台</div>
          <div class="t2">在办 {{ dash?.active ?? '—' }} 单 · 例外 {{ ex?.total ?? 0 }} 条 · 更新于 {{ new Date().toLocaleTimeString('zh-CN', { hour12: false }) }}</div>
        </div>
        <div class="sp" />
        <div class="who">后勤维修部</div>
        <button class="dbtn" @click="load">刷新</button>

      </div>

      <div class="desk-wrap">
        <p v-if="err" class="err">{{ err }}</p>

        <section class="panel review-panel">
          <header>
            <b>返修审查</b><span class="spacer" />
            <span class="hint">待审 {{ reviews?.total || 0 }} 条 · 学生误报可驳回</span>
          </header>
          <div v-if="!(reviews?.items || []).length" class="review-empty">当前没有待审返修申请</div>
          <div v-for="r in reviews?.items || []" :key="r.id" class="review-row">
            <div class="review-main">
              <div class="ln1">
                <span class="tag">{{ r.attemptNo > 1 ? '再次申请' : '首次申请' }}</span>
                <b class="num">{{ r.no }}</b>
                <span class="loc">{{ r.location }}</span>
              </div>
              <div class="why">学生原因：{{ r.reason }}</div>
              <div class="why">师傅：{{ r.workerName || '—' }} · 历史返修 {{ r.reworkCount }} 次</div>
              <div v-if="(r.photosBefore || []).length || (r.photosAfter || []).length" class="thumbs review-thumbs">
                <div v-for="(p, i) in r.photosBefore || []" :key="p" class="thumb" data-tone="ok">
                  <ProtectedImage v-if="!p.startsWith('demo://')" :src="p" :alt="`现场 ${i + 1}`" style="width:100%;height:100%;object-fit:cover;border-radius:inherit" />
                  <template v-else><span>📷</span><span>现场 {{ i + 1 }}</span></template>
                </div>
                <div v-for="(p, i) in r.photosAfter || []" :key="p" class="thumb" data-tone="ok">
                  <ProtectedImage v-if="!p.startsWith('demo://')" :src="p" :alt="`完工 ${i + 1}`" style="width:100%;height:100%;object-fit:cover;border-radius:inherit" />
                  <template v-else><span>📸</span><span>完工 {{ i + 1 }}</span></template>
                </div>
              </div>
            </div>
            <div class="review-actions">
              <button class="dbtn sm" :disabled="busy === 'review-' + r.id" @click="openReview(r, 'REJECT')">驳回申请</button>
              <button class="dbtn sm primary" :disabled="busy === 'review-' + r.id" @click="openReview(r, 'APPROVE')">确认返修</button>
            </div>
          </div>
        </section>

        <div class="strip">
          <div><div class="num">{{ dash?.active ?? '—' }}</div><div class="k">在办</div></div>
          <div><div class="num">{{ dash?.pendingArchive ?? '—' }}</div><div class="k">待归档</div></div>
          <div><div class="num">{{ ex?.total ?? '—' }}</div><div class="k">例外待看</div></div>
          <div>
            <div class="num">{{ dash ? Math.round((dash.zeroManual?.rate ?? 0) * 100) : '—' }}%</div>
            <div class="k">
              零人工完成率（{{ dash?.zeroManual?.zero ?? 0 }}/{{ dash?.zeroManual?.total ?? 0 }} 张）
              <span class="hint-inline">严格口径 {{ dash ? Math.round((dash.strictZero?.rate ?? 0) * 100) : '—' }}%</span>
            </div>
          </div>
        </div>

        <div class="row2">
          <section class="panel">
            <header><b>在办工单</b><span class="spacer" /><span class="hint">按楼层倒序 · 只读 · 待归档的 {{ archive.length }} 张见「最近归档」</span></header>
            <table class="dt">
              <thead>
                <tr>
                  <th>单号</th><th>位置</th><th>故障</th><th>紧急</th><th>状态</th><th>师傅</th><th>操作</th><th class="r">状态时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="o in active" :key="o.no">
                  <td class="num">
                    {{ o.no }}
                    <span v-if="o.reworkOf" class="mini">R{{ o.reworkIndex }}</span>
                  </td>
                  <td>{{ o.building }} {{ o.floor }}F-{{ o.room }}</td>
                  <td>{{ o.category }}</td>
                  <td><LevelPill :level="o.level" /></td>
                  <td>
                    <StatusPill :status="o.status" />
                    <span v-if="leftOf(o)" class="timer-inline num">{{ leftOf(o) }}</span>
                  </td>
                  <td>{{ o.workerName || '—' }}</td>
                  <td>
                    <button v-if="o.status === 'PARTS'" class="dbtn sm primary" :disabled="busy === 'parts-' + o.no" @click="partsArrived(o)">
                      {{ busy === 'parts-' + o.no ? '确认中…' : '确认配件到货' }}
                    </button>
                    <span v-else class="tiny muted">—</span>
                  </td>
                  <td class="r num">{{ o.statusAt ? String(o.statusAt).slice(5, 16) : '—' }}</td>
                </tr>
              </tbody>
            </table>
            <div class="panel-foot">
              排队：安全闸 → 截止时间 → 紧急度 → 先来先修。楼栋只用来攒批，不参与排队先后。
            </div>
          </section>

          <section class="panel">
            <header><b>例外待看</b><span class="spacer" /><span class="hint">四类之外一律不露</span></header>
            <div class="pn">
              <div v-for="it in ex?.items || []" :key="it.id" class="ex-row">
                <div class="ln1">
                  <span class="pill plain tag">{{ kindLabel[it.kind] || it.kind }}</span>
                  <b class="num">{{ it.no }}</b>
                  <span class="loc">{{ it.building }} {{ it.floor }}F-{{ it.room }}</span>
                </div>
                <div class="why">{{ it.detail }}</div>
                <div class="acts">
                  <template v-if="it.kind === 'CANCEL_REQUEST'">
                    <button class="dbtn sm" disabled>批准撤销（501）</button>
                    <button class="dbtn sm ghost" @click="keep(it)">先不动</button>
                  </template>
                  <template v-else>
                    <span class="then">往哪儿看：先看单据和照片，别直接改在办单</span>
                  </template>
                </div>
              </div>
              <div v-if="!ex?.items?.length" class="empty">今天没有例外。管理端大部分时间是空的，这就是目标。</div>
            </div>
            <div class="panel-foot">
              前三类不占状态位；只有「学生申请撤销」能改状态，且要过变更单 CR-001。
            </div>
          </section>
        </div>

        <div class="row2">
          <div class="col">
            <section class="panel">
              <header><b>楼栋台账</b><span class="spacer" /><span class="hint">事后回看用</span></header>
              <table class="dt">
                <thead><tr><th>楼栋</th><th class="r">在办</th><th class="r">已完成</th><th class="r">例外</th></tr></thead>
                <tbody>
                  <tr v-for="b in dash?.byBuilding || []" :key="b.buildingId">
                    <td>{{ b.buildingName }}</td>
                    <td class="r num">{{ b.active }}</td>
                    <td class="r num">{{ b.done }}</td>
                    <td class="r num">{{ b.exceptions }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section class="panel">
              <header><b>最近归档</b><span class="spacer" /><span class="hint">归档之后才回看，不回改在办单</span></header>
              <table class="dt">
                <thead><tr><th>单号</th><th>位置</th><th>状态</th><th class="r">返修</th><th class="r">人工介入</th></tr></thead>
                <tbody>
                  <tr v-for="o in archive" :key="o.no">
                    <td class="num">{{ o.no }}</td>
                    <td>{{ o.building }} {{ o.floor }}F-{{ o.room }}</td>
                    <td><StatusPill :status="o.status" /></td>
                    <td class="r num">{{ o.reworkCount || 0 }}</td>
                    <td class="r num">{{ manualCount(o) }} 步</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section class="panel">
              <header><b>两颗计时器</b><span class="spacer" /><span class="hint">起点不一样，各算各的</span></header>
              <table class="dt">
                <thead><tr><th>单号</th><th>位置</th><th>哪颗</th><th>起点</th><th class="r">还剩</th></tr></thead>
                <tbody>
                  <tr v-for="t in dash?.timers || []" :key="t.no + (t.t1 ? 'a' : 'b')">
                    <td class="num">{{ t.no }}</td>
                    <td>{{ t.room }}</td>
                    <td>{{ (t.t1 || t.t2)?.label }}</td>
                    <td class="num tiny">{{ (t.t1 || t.t2)?.from }}</td>
                    <td class="r num">{{ shortLeft((t.t1 || t.t2)?.leftMs) }}</td>
                  </tr>
                </tbody>
              </table>
              <div class="panel-foot">T1 从师傅完工起算；T2 从学生签字起算。同一张单不会同时有两颗表。</div>
            </section>
          </div>

          <div class="col">
            <section class="panel">
              <header><b>口径与介入点</b><span class="spacer" /><span class="hint">SYS-004</span></header>
              <div class="pn">
                <div class="rule-row"><span class="n">①</span><div><b>建单判不出来</b><div class="why">AI 置信度低就往急里判并留依据，不拦单。</div></div></div>
                <div class="rule-row"><span class="n">②</span><div><b>缺件超预算</b><div class="why">金额超预算线进清单，事前就知道花多少。</div></div></div>
                <div class="rule-row"><span class="n">③</span><div><b>返修审查</b><div class="why">每次返修都先由管理员核对，确认质量问题后才生成返修单。</div></div></div>
                <div class="rule-row"><span class="n">④</span><div><b>学生申请撤销</b><div class="why">师傅已进楼才有的情况，唯一能改状态的入口。</div></div></div>
              </div>
              <div class="panel-foot">9 个状态、13 条转移。</div>
            </section>

            <section class="panel">
              <header><b>可调参数</b><span class="spacer" /><span class="hint">来源 {{ cfg?.source || '—' }}</span></header>
              <div class="pn">
                <div class="cfg">
                  <label>预算线（元）<input v-model.number="cfgDraft.budgetLine" class="in num" /></label>
                  <label>验收超时（小时）<input v-model.number="cfgDraft.verifyHours" class="in num" /></label>
                  <label>归档天数<input v-model.number="cfgDraft.archiveDays" class="in num" /></label>
                </div>
                <button class="dbtn primary" :disabled="busy === 'cfg'" @click="saveCfg">
                  {{ busy === 'cfg' ? '保存中…' : '保存' }}
                </button>
                <div class="why" style="margin-top: 8px">存进 t_config，改完立刻生效，不用重启。</div>
              </div>
            </section>
          </div>
        </div>

        <div class="foot-note warn">
          <span class="tag">唯一例外</span>
          批准「学生申请撤销」要动工单状态，接口现在返回 501，等变更单 CR-001 批了才开。
          <button class="link" @click="tryCancel({ no: (ex?.items || []).find((x) => x.kind === 'CANCEL_REQUEST')?.no, version: (ex?.items || []).find((x) => x.kind === 'CANCEL_REQUEST')?.version })">
            点一下试试（会拿到 501）
          </button>
        </div>
        <div class="foot-note info">
          <span class="tag">不做</span>
          管理端只处理返修审查和例外，不接管正常工单推进；返修审查是保证学生误报不误伤师傅的前置门禁。
        </div>
      </div>

      <div v-if="reviewSel" class="rv-mask" @click.self="reviewSel = null">
        <div class="rv-sheet">
          <div class="sheet-hd"><b>{{ reviewDecision === 'APPROVE' ? '确认返修' : '驳回返修申请' }}</b><button @click="reviewSel = null">取消</button></div>
          <div class="why">工单 <span class="num">{{ reviewSel.no }}</span> · 学生原因：{{ reviewSel.reason }}</div>
          <label class="lb">审查意见</label>
          <textarea v-model="reviewReason" rows="3" class="in ta" :placeholder="reviewDecision === 'APPROVE' ? '说明为什么属于维修质量问题' : '说明为什么是学生误报或非维修质量问题'" />
          <p v-if="reviewDecision === 'APPROVE'" class="note" data-tone="warn">批准后原单进入返修中，并生成新的 -R 子单重新排队。</p>
          <p v-else class="note" data-tone="ok">驳回后不生成返修单，原单继续正常验收与归档。</p>
          <button class="dbtn primary review-submit" :disabled="reviewReason.trim().length < 2 || !!busy" @click="submitReview">{{ busy ? '处理中…' : '提交审查' }}</button>
        </div>
      </div>
      <div v-if="keeper" class="toast">{{ keeper }}</div>
    </main>
  </div>
</template>

<style scoped>
.brand .logo {
  width: 30px; height: 30px; border-radius: 9px; background: #2563eb; display: grid; place-items: center;
  font-size: 15px; font-weight: 700;
}
.bt { font-size: 13px; font-weight: 700; }
.bs { font-size: 10.5px; color: #8fa3bd; margin-top: 1px; }
.side-foot { margin-top: 18px; padding: 0 8px; font-size: 10.5px; color: #6f8299; line-height: 1.7; }
.desk-top { display: flex; align-items: center; gap: 12px; }
.t1 { font-size: 16px; font-weight: 700; }
.t2 { font-size: 11.5px; color: var(--ink-3); margin-top: 2px; }
.sp { flex: 1; }
.who { font-size: 12px; color: var(--ink-2); }
.dbtn {
  height: 32px; padding: 0 13px; border-radius: 8px; border: 1px solid var(--line);
  background: #fff; font-size: 12.5px; color: var(--ink); font-weight: 600;
}
.dbtn.primary { background: #1b4ed8; border-color: #1b4ed8; color: #fff; }
.dbtn.sm { height: 26px; padding: 0 10px; font-size: 11.5px; }
.dbtn.ghost { color: var(--ink-2); }
.dbtn:disabled { opacity: .55; }
.err { color: var(--danger); font-size: 12.5px; margin: 0 0 12px; }
.strip { margin-bottom: 14px; }
.hint-inline { color: #98a2b3; margin-left: 6px; }
.row2 { display: grid; grid-template-columns: minmax(0, 1.62fr) minmax(340px, 1fr); gap: 14px; }
.row2 + .row2 { margin-top: 14px; }
.col { display: flex; flex-direction: column; gap: 14px; }
.pn { padding: 0; }
.mini {
  display: inline-block; margin-left: 4px; padding: 0 5px; border-radius: 4px;
  background: #fef3f2; color: #b42318; font-size: 10px; line-height: 15px;
}
.timer-inline { display: block; margin-top: 2px; font-size: 10.5px; color: #98a2b3; font-variant-numeric: tabular-nums; }
.ex-row { padding: 12px 16px; border-bottom: 1px solid #f3f6fa; }
.ex-row:last-child { border-bottom: 0; }
.ln1 { display: flex; align-items: center; gap: 8px; }
.ln1 .tag { background: #f2f4f7; border-color: #e4e7ec; color: #475467; font-size: 11px; }
.ln1 b { font-size: 13px; }
.ln1 .loc { font-size: 12px; color: var(--ink-3); }
.why { font-size: 12px; color: var(--ink-2); line-height: 1.6; margin-top: 4px; }
.acts { margin-top: 7px; display: flex; gap: 7px; align-items: center; }
.then { font-size: 11.5px; color: var(--ink-3); }
.rule-row { display: flex; gap: 10px; padding: 11px 16px; border-bottom: 1px solid #f3f6fa; }
.rule-row:last-child { border-bottom: 0; }
.rule-row .n { font-size: 12px; color: #1b4ed8; font-weight: 700; }
.rule-row b { font-size: 12.5px; }
.cfg { display: flex; flex-direction: column; gap: 9px; padding: 12px 16px; }
.cfg label { font-size: 11.5px; color: var(--ink-3); display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.in {
  width: 130px; border: 1px solid var(--line); border-radius: 8px; padding: 6px 9px;
  font-size: 13px; text-align: right; font-family: inherit; color: var(--ink);
}
.foot-note {
  margin-top: 12px; padding: 11px 14px; border-radius: 10px; font-size: 12px; line-height: 1.7;
  background: #fff; box-shadow: var(--sh-sm);
}
.foot-note .tag {
  display: inline-block; margin-right: 7px; padding: 1px 7px; border-radius: 5px;
  font-size: 11px; background: #f2f4f7; color: #475467;
}
.foot-note.warn { border-left: 3px solid #f59e0b; }
.foot-note.info { border-left: 3px solid #2563eb; }
.link { border: 0; background: transparent; color: #1b4ed8; font-size: 11.5px; padding: 0; margin-left: 4px; cursor: pointer; }
.review-panel { margin-bottom: 14px; }
.review-empty { padding: 18px 16px; color: var(--ink-3); font-size: 12px; }
.review-row { display: flex; align-items: center; gap: 14px; padding: 12px 16px; border-top: 1px solid #f3f6fa; }
.review-thumbs { margin-top: 8px; }
.review-main { flex: 1; min-width: 0; }
.review-actions { display: flex; gap: 7px; flex: none; }
.review-actions .primary { background: #1b4ed8; border-color: #1b4ed8; color: #fff; }
.rv-mask { position: fixed; inset: 0; background: rgba(11,21,36,.42); display: grid; place-items: center; z-index: 60; }
.rv-sheet { width: min(480px, calc(100vw - 36px)); background: #fff; border-radius: 14px; padding: 18px; box-shadow: var(--sh-lg); }
.sheet-hd { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.sheet-hd b { font-size: 16px; }
.sheet-hd button { border: 0; background: transparent; color: var(--ink-3); }
.lb { display: block; margin-top: 12px; font-size: 12.5px; color: var(--ink-3); }
.in { width: 100%; margin-top: 6px; border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px; font-family: inherit; }
.ta { resize: none; }
.review-submit { width: 100%; margin-top: 12px; height: 42px; }
.toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: rgba(11,21,36,.92); color: #fff; padding: 9px 14px; border-radius: 9px; font-size: 12.5px; z-index: 40;
}
</style>
