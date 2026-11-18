<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import StatusPill from '../../components/StatusPill.vue'
import LevelPill from '../../components/LevelPill.vue'
import { studentApi } from '../../api/orders'
import { userOf } from '../../api/session'
import { describeError } from '../../api/request'
import { shortWindow } from '../../constants/status'

const router = useRouter()
const me = userOf()
const meta = ref(null)
const photos = ref([])
const desc = ref('')
const window = ref('')
const busy = ref(false)
const err = ref('')
const done = ref(null)

const windows = computed(() => meta.value?.windows || [])
const canSubmit = computed(() => photos.value.length > 0 && desc.value.trim().length >= 4 && !busy.value)

onMounted(async () => {
  try {
    meta.value = await studentApi.meta()
    window.value = windows.value[0] || ''
  } catch (e) {
    err.value = e.message
  }
})

function shoot () {
  if (photos.value.length >= 3) return
  // 演示环境没有真上传：拿到的是一个标记，服务端只校验"至少一张"
  photos.value.push(`demo://before/${me.id}/${Date.now()}-${photos.value.length + 1}.jpg`)
}

function remove (i) {
  photos.value.splice(i, 1)
}

async function submit () {
  if (!canSubmit.value) return
  busy.value = true
  err.value = ''
  try {
    const created = await studentApi.submit({
      building: me.building,
      floor: Number(me.room[0]),
      room: me.room,
      desc: desc.value.trim(),
      window: window.value,
      photosBefore: photos.value
    })
    done.value = created
  } catch (e) {
    err.value = describeError(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <header class="top">
    <button class="back" @click="router.back()">‹ 返回</button>
    <b>报修</b>
    <span class="tiny muted">{{ me.building }} {{ me.room }}</span>
  </header>

  <div class="body">
    <div class="card">
      <div class="card-hd"><h3>① 拍一张</h3><span class="hint">必填，至少 1 张</span></div>
      <button class="uploader" @click="shoot">
        <div style="font-size: 20px">📷</div>
        <div style="margin-top: 4px">点一下，拍下坏的地方</div>
        <div class="tiny" style="margin-top: 2px">最多 3 张</div>
      </button>
      <div v-if="photos.length" class="thumbs">
        <div v-for="(p, i) in photos" :key="p" class="thumb" data-tone="ok" @click="remove(i)">
          <span>📷</span><span>第 {{ i + 1 }} 张</span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-hd"><h3>② 说一句</h3><span class="hint">{{ desc.trim().length }} 字</span></div>
      <textarea
        v-model="desc" rows="4" maxlength="200" class="ta"
        placeholder="举个例子：洗手台下面一直滴水，水桶快接满了"
      />
      <div class="note" data-tone="info" style="margin-top: 9px">
        紧急程度不用你选。系统看描述自动判故障类型和紧急程度，判不准就往急里判，判完把依据写在这张单上，你可以查。
      </div>
    </div>

    <div class="card">
      <div class="card-hd"><h3>③ 什么时候有人</h3><span class="hint">选填</span></div>
      <div class="seg">
        <button
          v-for="w in windows" :key="w"
          :class="{ on: window === w }"
          @click="window = window === w ? '' : w"
        >
          {{ shortWindow(w) }}
        </button>
      </div>
      <div class="kv" style="margin-top: 11px">
        <span class="k">位置</span>
        <span class="v">{{ me.building === 'B7' ? '东区7号楼' : '东区8号楼' }} {{ me.room }} 室 · 按学籍带出，不用填</span>
      </div>
    </div>

    <p v-if="err" class="err">{{ err }}</p>
    <button class="submit" :disabled="!canSubmit" @click="submit">
      {{ busy ? '提交中…' : '提交报修' }}
    </button>
    <p class="tiny muted center">提交后自动派给本楼当班师傅，你可以在「我的报修」里看到每一步。</p>
  </div>

  <BottomNav role="student" />

  <!-- 报好了：把系统的判断原样摊给学生看 -->
  <div v-if="done" class="mask" @click.self="router.push('/student')">
    <div class="sheet">
      <div class="sheet-hd">
        <b>报好了</b>
        <button @click="router.push('/student')">知道了</button>
      </div>
      <div class="ok-no num">{{ done.no }}</div>
      <div class="row" style="margin: 6px 0 12px">
        <StatusPill :status="done.status" />
        <LevelPill :level="done.level" />
        <span class="pill plain">{{ done.category }}</span>
      </div>
      <div class="kv"><span class="k">谁上门</span><span class="v">{{ done.worker ? `${done.worker.name}` : '等派工' }}</span></div>
      <div class="kv"><span class="k">时段</span><span class="v">{{ shortWindow(done.window) || '全天' }}</span></div>
      <div class="kv" v-if="done.aiBasis"><span class="k">系统怎么判</span><span class="v">{{ done.aiBasis }}</span></div>
      <button class="submit" style="margin-top: 14px" @click="router.push(`/student/detail/${done.no}`)">看这单进度</button>
    </div>
  </div>
</template>

<style scoped>
.top {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px 10px; font-size: 15px;
}
.back { border: 0; background: transparent; color: var(--ink-2); font-size: 14px; padding: 0; }
.ta {
  width: 100%; border: 1px solid var(--line); border-radius: 11px; padding: 11px 12px;
  font-size: 13.5px; line-height: 1.6; font-family: inherit; resize: none; color: var(--ink);
  background: #fafbfd;
}
.ta:focus { outline: 0; border-color: var(--navy); background: #fff; box-shadow: 0 0 0 3px rgba(18, 41, 75, .08); }
.submit {
  width: 100%; margin-top: 12px; height: 48px; border: 0; border-radius: 13px;
  background: var(--navy); color: #fff; font-size: 15px; font-weight: 650;
}
.submit:disabled { background: #c7d0dd; }
.err { color: var(--danger); font-size: 13px; margin: 8px 0 0; }
.center { text-align: center; margin: 9px 0 0; }
.mask {
  position: fixed; inset: 0; background: rgba(11, 21, 36, .42);
  display: flex; align-items: flex-end; justify-content: center; z-index: 50;
}
.sheet {
  width: 100%; max-width: 430px; background: #fff; border-radius: 18px 18px 0 0;
  padding: 16px 16px calc(20px + env(safe-area-inset-bottom, 0px));
}
.sheet-hd { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.sheet-hd b { font-size: 16px; }
.sheet-hd button { border: 0; background: transparent; color: var(--ink-3); font-size: 13px; }
.ok-no { font-size: 20px; font-weight: 700; letter-spacing: .02em; }
</style>
