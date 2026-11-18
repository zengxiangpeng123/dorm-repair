<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '../api/auth'
import { clearSession, saveSession, userOf } from '../api/session'
import { describeError } from '../api/request'

const router = useRouter()
const route = useRoute()
const account = ref('S001')
const password = ref('dorm@2026')
const busy = ref(false)
const err = ref('')
const existing = computed(() => userOf())

function homeOf (role) {
  if (role === 'WORKER') return '/worker'
  if (role === 'ADMIN') return '/admin'
  return '/student'
}

async function login () {
  err.value = ''
  busy.value = true
  try {
    const r = await authApi.login(account.value.trim(), password.value)
    saveSession({ token: r.token, user: r.user, expiresAt: r.expiresAt })
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : homeOf(r.user.role)
    await router.replace(target)
  } catch (e) {
    err.value = describeError(e)
  } finally {
    busy.value = false
  }
}

function logout () {
  clearSession()
  account.value = 'S001'
  password.value = 'dorm@2026'
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand"><span>修</span><div><b>宿舍维修系统</b><small>后勤服务登录</small></div></div>
      <h1>登录工作台</h1>
      <p class="hint">演示账号：S001 / W0428 / A001，初始口令 dorm@2026</p>

      <div v-if="existing" class="session">
        当前登录：{{ existing.name }}（{{ existing.role }}）
        <button class="link" @click="logout">退出</button>
      </div>

      <label>账号<input v-model="account" autocomplete="username" @keyup.enter="login" /></label>
      <label>口令<input v-model="password" type="password" autocomplete="current-password" @keyup.enter="login" /></label>
      <p v-if="err" class="err">{{ err }}</p>
      <button class="submit" :disabled="busy || !account.trim() || !password" @click="login">
        {{ busy ? '登录中…' : '登录' }}
      </button>
      <button class="back" @click="router.push('/')">返回三端入口</button>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; background: #edf1f6; padding: 24px; }
.login-card { width: min(420px, 100%); background: #fff; border-radius: 20px; padding: 28px; box-shadow: 0 18px 60px rgba(18, 41, 75, .14); }
.brand { display: flex; gap: 11px; align-items: center; margin-bottom: 22px; }
.brand > span { width: 38px; height: 38px; display: grid; place-items: center; color: #fff; background: #1b4ed8; border-radius: 11px; font-weight: 700; }
.brand b, .brand small { display: block; }
.brand small { color: #7b8798; margin-top: 2px; font-size: 11px; }
h1 { margin: 0 0 6px; font-size: 24px; }
.hint { font-size: 12px; color: #7b8798; line-height: 1.6; margin: 0 0 18px; }
label { display: block; font-size: 12px; color: #46536a; margin-top: 12px; }
input { display: block; width: 100%; margin-top: 6px; border: 1px solid #e3e8ee; border-radius: 11px; padding: 11px 12px; font: inherit; }
input:focus { outline: 0; border-color: #1b4ed8; box-shadow: 0 0 0 3px rgba(27,78,216,.08); }
.submit { width: 100%; height: 46px; margin-top: 18px; border: 0; border-radius: 12px; background: #12294b; color: #fff; font-weight: 650; cursor: pointer; }
.submit:disabled { opacity: .55; }
.back { width: 100%; margin-top: 10px; border: 0; background: transparent; color: #7b8798; cursor: pointer; }
.err { color: #b42318; font-size: 12px; margin: 10px 0 0; }
.session { background: #f4f7fb; border-radius: 10px; padding: 10px 12px; color: #46536a; font-size: 12px; }
.link { border: 0; background: transparent; color: #1b4ed8; cursor: pointer; margin-left: 8px; }
</style>