<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import Plate from '../../components/Plate.vue'
import StatusPill from '../../components/StatusPill.vue'
import LevelPill from '../../components/LevelPill.vue'
import ProtectedImage from '../../components/ProtectedImage.vue'
import { workerApi } from '../../api/orders'
import { fileApi } from '../../api/files'
import { userOf } from '../../api/session'
import { describeError } from '../../api/request'
import { EVENT, shortWindow } from '../../constants/status'

const route = useRoute()
const router = useRouter()
const no = route.params.no
const o = ref(null)
const meta = ref(null)
const loading = ref(true)
const err = ref('')
const busy = ref('')
const sheet = ref(null) // 'finish' | 'parts' | 'noshow'

const photoCount = ref(0)
const photoFiles = ref([])
const uploading = ref(false)
const fileInput = ref(null)
const noteTags = ref([])
const note = ref('')
const parts = ref({ name: '', amount: '', eta: '' })
const noShowReason = ref('')

const me = userOf()
const isMine = computed(() => !!o.value?.workerId && o.value.workerId === me?.id)
const canArrive = computed(() => o.value?.status === 'ASSIGNED' && !o.value?.arriveAt)
const canFinish = computed(() => o.value?.status === 'REPAIRING')
const budgetLine = computed(() => meta.value?.budgetLine ?? 200)
const overBudget = computed(() => Number(parts.value.amount || 0) > budgetLine.value)

const tags = computed(() => meta.value?.finishTags || [])
const reasons = computed(() => meta.value?.noShowReasons || [])

// 事件 note 里已经带了「申请撤销：」前缀，界面别再说两遍
const cancelWhy = computed(() => {
  const raw = String(o.value?.cancelRequest?.reason || '')
  return raw.split('申请撤销').pop().replace(/^[：:]\s*/, '')
})

async function load () {
  try {
    const m = meta.value || await workerApi.meta()
    meta.value = m
    let target = no
    if (!target) {
      // 没带单号就自己挑一张：在修的优先，其次已派工，再次等配件
      const t = await workerApi.workerTodo()
      const list = (t.buildings || []).flatMap((b) => b.orders || [])
      const rank = { REPAIRING: 0, ASSIGNED: 1, PARTS: 2, PENDING: 3 }
      list.sort((a, b) => (rank[a.status] ?? 9) - (rank[b.status] ?? 9))
      target = list[0]?.no
    }
    if (!target) { err.value = '本楼暂时没有要处理的单'; return }
    o.value = await workerApi.detail(target)
    parts.value.eta = tomorrow()
  } catch (e) {
    err.value = describeError(e)
  } finally {
    loading.value = false
  }
}

function tomorrow () {
  const d = new Date(Date.now() + 86400000)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function fire (path, body, tag) {
  busy.value = tag
  err.value = ''
  try {
    await path(o.value.no, { ...body, version: o.value.version })
    sheet.value = null
    await load()
  } catch (e) {
    err.value = describeError(e)
    if (e.status === 409) load()
  } finally {
    busy.value = ''
  }
}

function openSheet (k) {
  if (k === 'finish') { photoCount.value = 0; photoFiles.value = []; noteTags.value = []; note.value = '' }
  if (k === 'parts') { parts.value = { name: '', amount: '', eta: tomorrow() } }
  if (k === 'noshow') noShowReason.value = reasons.value[0]?.code || ''
  sheet.value = k
}

function pickFinishPhotos () {
  if (photoCount.value >= 3 || uploading.value) return
  fileInput.value?.click()
}

async function finishPhotosPicked (event) {
  const files = Array.from(event.target.files || []).slice(0, 3 - photoCount.value)
  event.target.value = ''
  if (!files.length) return
  uploading.value = true
  err.value = ''
  try {
    for (const file of files) {
      const uploaded = await fileApi.upload(file, 'AFTER')
      photoFiles.value.push({ id: uploaded.id, url: uploaded.url, name: file.name })
    }
    photoCount.value = photoFiles.value.length
  } catch (e) {
    err.value = describeError(e)
  } finally {
    uploading.value = false
  }
}

function removeFinishPhoto (i) {
  photoFiles.value.splice(i, 1)
  photoCount.value = photoFiles.value.length
}

function submitFinish () {
  if (!photoCount.value || uploading.value) return
  const text = [...noteTags.value, note.value.trim()].filter(Boolean).join('、') || '完工提交'
  fire(workerApi.finish, {
    photosAfter: photoFiles.value.map((p) => p.url),
    photoIdsAfter: photoFiles.value.map((p) => p.id),
    note: text
  }, 'finish')
}

function submitParts () {
  if (!parts.value.name.trim() || !(Number(parts.value.amount) > 0)) return
  fire(workerApi.needParts, {
    partsName: parts.value.name.trim(),
    partsAmount: Number(parts.value.amount),
    partsEta: parts.value.eta,
    note: `缺件：${parts.value.name.trim()}`
  }, 'parts')
}

onMounted(load)
</script>

<template>
  <header class="top">
    <button class="back" @click="router.push('/worker')">‹ 工作台</button>
    <b v-if="o">{{ o.floor }}F-{{ o.room }}</b>
    <span v-if="o" class="tiny muted"><StatusPill :status="o.status" /></span>
  </header>

  <div v-if="loading" class="empty">加载中…</div>
  <div v-else-if="!o" class="empty">{{ err || '没有要处理的单' }}</div>

  <div v-else class="body has-dock">
    <!-- 学生申请撤销：先别动手，等管理员定 -->
    <div v-if="o.cancelRequest" class="note" data-tone="warn" style="margin-bottom: 10px">
      <b>学生申请撤销</b>　{{ o.cancelRequest.at }} 提的，原因：{{ cancelWhy }}。
      管理员还没定——先别去，去了也白跑。
    </div>

    <!-- 这间什么毛病 -->
    <div class="card hero">
      <div class="hrow">
        <Plate :room="o.room" :floor="o.floor" :status="o.status" large />
        <div class="htx">
          <div class="hno">{{ o.building }} {{ o.floor }}F-{{ o.room }}</div>
          <div class="tiny muted num">{{ o.no }}<template v-if="o.reworkOf"> · 返修 R{{ o.reworkIndex }}</template></div>
          <div class="row" style="margin-top: 5px">
            <LevelPill :level="o.level" />
            <span class="pill plain">{{ o.category }}</span>
          </div>
        </div>
      </div>
      <p class="desc">{{ o.desc }}</p>
      <div class="kv"><span class="k">学生</span><span class="v">{{ o.studentName }}</span></div>
      <div class="kv"><span class="k">可上门</span><span class="v">{{ shortWindow(o.window) || '全天' }}</span></div>
      <div v-if="o.aiBasis" class="kv"><span class="k">系统判的</span><span class="v tiny muted">{{ o.aiBasis }}</span></div>
    </div>

    <!-- ① 能进场吗（已进楼就收成一行，别让工序从 ② 开始） -->
    <div v-if="canArrive" class="card">
      <div class="card-hd"><h3>① 能进场吗</h3><span class="hint">必须本人打卡</span></div>
      <div class="btns">
        <button class="dbtn primary" :disabled="busy === 'arrive'" @click="fire(workerApi.arrive, { note: '到门打卡' }, 'arrive')">
          {{ busy === 'arrive' ? '打卡中…' : '门开着，进楼修' }}
        </button>
        <button class="dbtn" @click="openSheet('noshow')">敲了没人应</button>
      </div>
      <div class="note" style="margin-top: 9px">
        在楼门口打卡会把这一批单一起记「到门」。人没见到就选「敲了没人应」，这单回队列重排，不算你白跑。
      </div>
    </div>

    <div v-else-if="o.arriveAt" class="card done-strip">
      <span class="tick">✓</span>
      <div>
        <b>① 已到门</b>
        <span class="muted tiny">{{ o.arriveAt }} 打卡 · 是本人到这间</span>
      </div>
    </div>

    <!-- ② 能当场修好吗 -->
    <div v-if="canFinish" class="card">
      <div class="card-hd"><h3>② 能当场修好吗</h3></div>
      <div class="btns">
        <button class="dbtn primary" @click="openSheet('finish')">修好了，上传完工照</button>
        <button class="dbtn" @click="openSheet('parts')">缺配件，先登记</button>
      </div>
      <div class="note" data-tone="info" style="margin-top: 9px">
        登记缺件要填名称和金额。金额超过 {{ budgetLine }} 元会进管理端审批，配件到了这单自动回队列。
      </div>
    </div>

    <div v-else-if="o.status === 'PARTS'" class="card">
      <div class="card-hd"><h3>等配件</h3><span class="hint">{{ o.partsEta || '' }}</span></div>
      <div class="money">
        <div><div class="n">{{ o.partsName }}</div><div class="k">配件</div></div>
        <div><div class="n num" :class="{ hot: Number(o.partsAmount) > budgetLine }">¥{{ o.partsAmount }}</div><div class="k">金额</div></div>
        <div><div class="n num">¥{{ budgetLine }}</div><div class="k">预算线</div></div>
      </div>
      <div class="note" style="margin-top: 9px">配件到货后系统把这单放回队列，定级不降。你可以在「等配件」里确认到货。</div>
    </div>

    <div v-else class="card">
      <div class="note" :data-tone="o.status === 'VERIFYING' ? 'info' : ''">
        {{ o.status === 'VERIFYING' ? '已经完工，等学生验收。验收通过或超时自动验收后这单结束。' : `这单现在是「${o.statusLabel}」，不需要你动手。` }}
      </div>
    </div>

    <!-- 照片 -->
    <div class="card">
      <div class="card-hd"><h3>照片</h3><span class="hint">学生 {{ (o.photosBefore || []).length }} 张 · 你上传 {{ (o.photosAfter || []).length }} 张</span></div>
      <div class="thumbs">
        <div v-for="(p, i) in o.photosBefore" :key="p" class="thumb">
          <ProtectedImage v-if="!p.startsWith('demo://')" :src="p" :alt="`现场 ${i + 1}`" class="photo-img" />
          <template v-else><span>📷</span><span>现场 {{ i + 1 }}</span></template>
        </div>
        <div v-for="(p, i) in o.photosAfter" :key="p" class="thumb" data-tone="ok">
          <ProtectedImage v-if="!p.startsWith('demo://')" :src="p" :alt="`完工 ${i + 1}`" class="photo-img" />
          <template v-else><span>📸</span><span>完工 {{ i + 1 }}</span></template>
        </div>
        <div v-if="!o.photosAfter?.length" class="thumb" data-tone="bad"><span>⚠️</span><span>完工照必填</span></div>
      </div>
    </div>

    <!-- 经过 -->
    <div class="card">
      <div class="card-hd"><h3>这单经过</h3><span class="hint">最近 {{ Math.min(6, (o.events || []).length) }} 步</span></div>
      <div class="tl">
        <div
          v-for="(e, i) in (o.events || []).slice(-6)" :key="e.seq || i" class="ev"
          :data-s="i === (o.events || []).slice(-6).length - 1 ? 'now' : 'done'"
        >
          <div class="t num">{{ String(e.at).slice(5, 16) }}</div>
          <div class="d">{{ EVENT[e.event] || e.event }}<span v-if="e.manual === 0" class="tiny muted"> · 系统自动</span></div>
        </div>
      </div>
    </div>

    <p v-if="err" class="err">{{ err }}</p>
  </div>

  <div v-if="o" class="dock">
    <button v-if="canArrive" class="dbtn primary" @click="openSheet('noshow')">人没见到</button>
    <button v-else-if="canFinish" class="dbtn primary" @click="openSheet('finish')">完工</button>
    <button v-else class="dbtn" @click="router.push('/worker')">回工作台</button>
  </div>

  <!-- 完工 -->
  <div v-if="sheet === 'finish'" class="mask" @click.self="sheet = null">
    <div class="sheet">
      <div class="sheet-hd"><b>完工提交</b><button @click="sheet = null">取消</button></div>
      <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" capture="environment" multiple hidden @change="finishPhotosPicked" />
      <button class="uploader" :disabled="uploading || photoCount >= 3" @click="pickFinishPhotos">
        <div style="font-size: 20px">📸</div>
        <div style="margin-top: 4px">{{ uploading ? '上传中…' : `拍完工照（必填，已上传 ${photoCount} 张）` }}</div>
        <div class="tiny" style="margin-top: 2px">JPEG / PNG / WebP，最多 3 张，每张 ≤ 8 MB</div>
      </button>
      <div v-if="photoCount" class="thumbs">
        <div v-for="(p, i) in photoFiles" :key="p.id" class="thumb" data-tone="ok" @click="removeFinishPhoto(i)">
          <ProtectedImage :src="p.url" :alt="p.name" style="width:100%;height:100%;object-fit:cover;border-radius:inherit" />
        </div>
      </div>
      <div class="lb" style="margin-top: 12px">动了什么（可多选）</div>
      <div class="chips" style="margin-top: 6px">
        <button
          v-for="t in tags" :key="t" class="chip" :class="{ on: noteTags.includes(t) }"
          @click="noteTags.includes(t) ? noteTags.splice(noteTags.indexOf(t), 1) : noteTags.push(t)"
        >{{ t }}</button>
      </div>
      <div class="lb" style="margin-top: 12px">补充一句（可不写）</div>
      <input v-model="note" class="in" placeholder="举个例子：阀芯老化，换完不漏了" />
      <button class="submit" :disabled="!photoCount || uploading || !!busy" @click="submitFinish">
        {{ busy === 'finish' ? '提交中…' : '提交完工' }}
      </button>
      <p v-if="err" class="err">{{ err }}</p>
    </div>
  </div>

  <!-- 登记缺件 -->
  <div v-if="sheet === 'parts'" class="mask" @click.self="sheet = null">
    <div class="sheet">
      <div class="sheet-hd"><b>缺什么配件</b><button @click="sheet = null">取消</button></div>
      <div class="lb">配件名称</div>
      <input v-model="parts.name" class="in" placeholder="举个例子：角阀" />
      <div class="lb" style="margin-top: 10px">多少钱</div>
      <input v-model="parts.amount" class="in num" inputmode="decimal" placeholder="38" />
      <div class="lb" style="margin-top: 10px">预计哪天到货</div>
      <input v-model="parts.eta" type="date" class="in num" />
      <div class="note" :data-tone="overBudget ? 'warn' : 'info'" style="margin-top: 10px">
        {{ overBudget
          ? `超过预算线 ${budgetLine} 元，这笔会进管理端「超预算采购」清单等审批，别自己先垫钱买。`
          : `在 ${budgetLine} 元预算线内，登记完直接进采购。` }}
      </div>
      <button class="submit" :disabled="!parts.name.trim() || !(Number(parts.amount) > 0) || !!busy" @click="submitParts">
        {{ busy === 'parts' ? '提交中…' : '登记缺件' }}
      </button>
      <p v-if="err" class="err">{{ err }}</p>
    </div>
  </div>

  <!-- 到访未遇 -->
  <div v-if="sheet === 'noshow'" class="mask" @click.self="sheet = null">
    <div class="sheet">
      <div class="sheet-hd"><b>人没见到</b><button @click="sheet = null">取消</button></div>
      <div class="chips">
        <button
          v-for="r in reasons" :key="r.code" class="chip" :class="{ on: noShowReason === r.code }"
          @click="noShowReason = r.code"
        >{{ r.label }}</button>
      </div>
      <p class="note" data-tone="warn" style="margin-top: 10px">
        这张单会回队列重排，系统记一笔「到访未遇」。这不是白跑——有记录，就不是你说了算也不是学生说了算。
      </p>
      <button class="submit" :disabled="!noShowReason || !!busy" @click="fire(workerApi.noShow, { reason: noShowReason }, 'noshow')">
        {{ busy === 'noshow' ? '提交中…' : '确认人没见到' }}
      </button>
      <p v-if="err" class="err">{{ err }}</p>
    </div>
  </div>

  <BottomNav role="worker" />
</template>

<style scoped>
.top { display: flex; align-items: center; justify-content: space-between; padding: 13px 16px 8px; font-size: 15px; }
.back { border: 0; background: transparent; color: var(--ink-2); font-size: 14px; padding: 0; }
.hero { border-radius: 14px; box-shadow: var(--sh-md); }
.hrow { display: flex; gap: 12px; align-items: center; }
.htx { min-width: 0; }
.hno { font-size: 17px; font-weight: 700; }
.desc { margin: 10px 0; font-size: 13.5px; line-height: 1.65; color: var(--ink-2); }
.btns { display: flex; flex-direction: column; gap: 8px; }
.dbtn { width: 100%; height: 44px; border-radius: 11px; border: 1px solid var(--line); background: #fff; font-size: 14.5px; font-weight: 650; }
.dbtn.primary { background: var(--navy); border-color: var(--navy); color: #fff; }
.dbtn:disabled { opacity: .6; }
.dock {
  position: fixed; left: 10px; right: 10px; bottom: calc(var(--nav-h) + 10px + env(safe-area-inset-bottom, 0px));
  max-width: 410px; margin: 0 auto; padding: 9px; background: #fff; border-radius: 14px; box-shadow: var(--sh-md); z-index: 20;
}
.err { color: var(--danger); font-size: 12.5px; }
.mask { position: fixed; inset: 0; background: rgba(11,21,36,.42); display: flex; align-items: flex-end; justify-content: center; z-index: 50; }
.sheet {
  width: 100%; max-width: 430px; background: #fff; border-radius: 18px 18px 0 0;
  padding: 16px 16px calc(20px + env(safe-area-inset-bottom, 0px));
}
.sheet-hd { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.sheet-hd b { font-size: 16px; }
.sheet-hd button { border: 0; background: transparent; color: var(--ink-3); font-size: 13px; }
.lb { font-size: 12.5px; color: var(--ink-3); }
.in {
  width: 100%; margin-top: 6px; border: 1px solid var(--line); border-radius: 11px; padding: 10px 12px;
  font-size: 14px; font-family: inherit; background: #fafbfd; color: var(--ink);
}
.in:focus { outline: 0; border-color: var(--navy); background: #fff; box-shadow: 0 0 0 3px rgba(18,41,75,.08); }
.submit { width: 100%; margin-top: 12px; height: 46px; border: 0; border-radius: 12px; background: var(--navy); color: #fff; font-size: 15px; font-weight: 650; }
.submit:disabled { background: #c7d0dd; }
.photo-img { width: 100%; height: 100%; object-fit: cover; border-radius: inherit; }
</style>
