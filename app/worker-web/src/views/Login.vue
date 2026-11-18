<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '../api/auth'
import { clearSession, saveSession, userOf } from '../api/session'
import { describeError } from '../api/request'

const router = useRouter()
const route = useRoute()
const account = ref('')
const password = ref('')
const busy = ref(false)
const err = ref('')
const existing = computed(() => userOf())

async function login() {
  err.value = ''
  busy.value = true
  try {
    const r = await authApi.login(account.value.trim(), password.value)
    saveSession({ token: r.token, user: r.user, expiresAt: r.expiresAt })
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/worker')
  } catch (e) { err.value = describeError(e) } finally { busy.value = false }
}
async function logout() {
  try { await authApi.logout() } catch {}
  clearSession()
  await router.replace('/login')
}
</script>

<template>
  <main class="login">
    <section class="card">
      <div class="brand">修 <div><b>宿舍维修系统</b><small>师傅工作台</small></div></div>
      <h1>师傅工作台登录</h1>
      <p>师傅账号登录；只能查看派给自己的工单</p>
      <div v-if="existing" class="session">当前登录：{{ existing.name }}（{{ existing.role }}）<button @click="logout">退出</button></div>
      <label>账号<input v-model="account" autocomplete="username" @keyup.enter="login" /></label>
      <label>口令<input v-model="password" type="password" autocomplete="current-password" @keyup.enter="login" /></label>
      <p v-if="err" class="err">{{ err }}</p>
      <button class="submit" :disabled="busy || !account.trim() || !password" @click="login">{{ busy ? '登录中…' : '登录' }}</button>
    </section>
  </main>
</template>

<style scoped>
.login { min-height:100vh; display:grid; place-items:center; padding:24px; background:#edf1f6; }
.card { width:min(420px,100%); background:#fff; border-radius:20px; padding:28px; box-shadow:0 18px 60px rgba(18,41,75,.14); }
.brand { display:flex; gap:11px; align-items:center; margin-bottom:20px; font-weight:700; }
.brand > div:first-child { width:38px; height:38px; display:grid; place-items:center; color:#fff; background:#1b4ed8; border-radius:11px; }
.brand b,.brand small { display:block; } .brand small { color:#7b8798; font-size:11px; font-weight:400; }
h1 { margin:0 0 6px; font-size:24px; } p { color:#7b8798; font-size:12px; line-height:1.6; }
label { display:block; margin-top:12px; color:#46536a; font-size:12px; }
input { display:block; width:100%; margin-top:6px; padding:11px 12px; border:1px solid #e3e8ee; border-radius:11px; font:inherit; }
.submit { width:100%; height:46px; margin-top:18px; border:0; border-radius:12px; background:#12294b; color:#fff; font-weight:650; }
.submit:disabled { opacity:.55; } .err { color:#b42318; } .session { background:#f4f7fb; padding:10px; border-radius:10px; font-size:12px; }
.session button { border:0; background:transparent; color:#1b4ed8; margin-left:8px; }
</style>
