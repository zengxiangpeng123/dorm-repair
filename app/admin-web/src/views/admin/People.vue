<script setup>
import { onMounted, reactive, ref } from 'vue'
import { userApi } from '../../api/users'
import { describeError } from '../../api/request'

const users = ref([])
const meta = ref({ buildings: [] })
const loading = ref(false)
const busy = ref('')
const err = ref('')
const ok = ref('')
const showCreate = ref(false)
const resetUser = ref(null)
const resetPassword = ref('')
const form = reactive({ id: '', role: 'STUDENT', name: '', team: '', building: 'B7', room: '', phone: '', buildingsText: 'B7', password: '' })

async function load () {
  loading.value = true
  try {
    const [u, m] = await Promise.all([userApi.list(), (await import('../../api/orders')).adminApi.meta()])
    users.value = u.items || []
    meta.value = m || { buildings: [] }
    err.value = ''
  } catch (e) { err.value = describeError(e) } finally { loading.value = false }
}

function openCreate () {
  Object.assign(form, { id: '', role: 'STUDENT', name: '', team: '', building: 'B7', room: '', phone: '', buildingsText: 'B7', password: '' })
  showCreate.value = true
  err.value = ''
}

async function createUser () {
  busy.value = 'create'
  err.value = ''
  try {
    const body = {
      id: form.id.trim(), role: form.role, name: form.name.trim(), team: form.team || null,
      building: form.role === 'STUDENT' || form.role === 'WORKER' ? form.building : null,
      room: form.role === 'STUDENT' ? form.room.trim() : null,
      phone: form.phone || null,
      buildings: form.role === 'WORKER' ? form.buildingsText.split(',').map((x) => x.trim()).filter(Boolean) : null,
      password: form.password
    }
    await userApi.create(body)
    showCreate.value = false
    ok.value = `账号 ${body.id} 已创建`
    await load()
  } catch (e) { err.value = describeError(e) } finally { busy.value = '' }
}

function openReset (u) {
  resetUser.value = u
  resetPassword.value = ''
  err.value = ''
}

async function submitReset () {
  busy.value = 'reset'
  try {
    await userApi.resetPassword(resetUser.value.id, resetPassword.value)
    ok.value = `${resetUser.value.id} 的口令已重置`
    resetUser.value = null
    resetPassword.value = ''
  } catch (e) { err.value = describeError(e) } finally { busy.value = '' }
}

onMounted(load)
</script>

<template>
  <div class="people-page">
    <header class="people-head">
      <div>
        <h1>人员管理</h1>
        <p>账号按学生、师傅、管理员分表保存；口令只保存哈希，创建后不会回显。</p>
      </div>
      <button class="primary-btn" @click="openCreate">新增账号</button>
    </header>

    <p v-if="err" class="err">{{ err }}</p>
    <p v-if="ok" class="ok">{{ ok }}</p>

    <section class="people-card">
      <table>
        <thead><tr><th>账号</th><th>角色</th><th>姓名</th><th>班组/楼栋</th><th>电话</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td class="mono">{{ u.id }}</td>
            <td><span class="role">{{ u.role }}</span></td>
            <td>{{ u.name }}</td>
            <td>{{ u.team || u.building || '—' }}<span v-if="u.room"> / {{ u.room }}</span></td>
            <td>{{ u.phone || '—' }}</td>
            <td>{{ u.active === false ? '停用' : '启用' }}</td>
            <td><button class="text-btn" @click="openReset(u)">重置口令</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!users.length && !loading" class="empty">暂无人员账号</p>
    </section>

    <div v-if="showCreate" class="mask" @click.self="showCreate = false">
      <form class="sheet" @submit.prevent="createUser">
        <h2>新增账号</h2>
        <label>账号<input v-model="form.id" required placeholder="学号 / 工号" /></label>
        <label>角色<select v-model="form.role"><option value="STUDENT">学生</option><option value="WORKER">维修师傅</option><option value="ADMIN">管理员</option></select></label>
        <label>姓名<input v-model="form.name" required /></label>
        <template v-if="form.role === 'STUDENT'">
          <label>楼栋<select v-model="form.building"><option v-for="b in meta.buildings" :key="b.id" :value="b.id">{{ b.name }}</option></select></label>
          <label>房间<input v-model="form.room" required placeholder="402" /></label>
        </template>
        <template v-if="form.role === 'WORKER'">
          <label>班组<input v-model="form.team" placeholder="水电班 / 综合班" /></label>
          <label>服务楼栋<input v-model="form.buildingsText" placeholder="B7,B8" /></label>
        </template>
        <label>电话<input v-model="form.phone" /></label>
        <label>初始口令<input v-model="form.password" type="password" minlength="8" required autocomplete="new-password" /></label>
        <div class="actions"><button type="button" class="text-btn" @click="showCreate = false">取消</button><button class="primary-btn" :disabled="busy === 'create'">创建</button></div>
      </form>
    </div>

    <div v-if="resetUser" class="mask" @click.self="resetUser = null">
      <form class="sheet" @submit.prevent="submitReset">
        <h2>重置 {{ resetUser.id }} 的口令</h2>
        <label>新口令<input v-model="resetPassword" type="password" minlength="8" required autocomplete="new-password" /></label>
        <div class="actions"><button type="button" class="text-btn" @click="resetUser = null">取消</button><button class="primary-btn" :disabled="busy === 'reset'">确认重置</button></div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.people-page { max-width: 1120px; margin: 0 auto; padding: 34px 28px; color: #0b1524; }
.people-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
h1 { margin: 0; font-size: 26px; } .people-head p { margin: 7px 0 0; color: #667085; font-size: 13px; }
.people-card { background: #fff; border-radius: 14px; box-shadow: 0 3px 14px #12294b14; overflow: auto; }
table { width: 100%; border-collapse: collapse; } th,td { text-align: left; padding: 13px 16px; border-bottom: 1px solid #edf0f4; font-size: 13px; }
th { color: #667085; font-weight: 600; background: #f8fafc; } .mono { font-family: ui-monospace, monospace; }
.role { display: inline-block; padding: 3px 8px; border-radius: 999px; background: #eff4ff; color: #1b4ed8; font-size: 11px; font-weight: 650; }
.primary-btn { border: 0; border-radius: 9px; background: #1b4ed8; color: #fff; padding: 10px 16px; cursor: pointer; font-weight: 650; }
.text-btn { border: 0; background: transparent; color: #1b4ed8; cursor: pointer; padding: 5px; }
.err { color: #b42318; background: #fef3f2; padding: 10px 12px; border-radius: 8px; } .ok { color: #067647; background: #ecfdf3; padding: 10px 12px; border-radius: 8px; }
.empty { padding: 22px; text-align: center; color: #667085; }
.mask { position: fixed; inset: 0; background: #0b152466; display: grid; place-items: center; z-index: 20; }
.sheet { width: min(480px, calc(100vw - 32px)); background: #fff; border-radius: 16px; padding: 22px; display: grid; gap: 12px; box-shadow: 0 20px 50px #0b15244d; }
.sheet h2 { margin: 0 0 5px; font-size: 19px; } .sheet label { display: grid; gap: 6px; font-size: 12px; color: #46536a; }
.sheet input,.sheet select { border: 1px solid #d8dee8; border-radius: 8px; padding: 10px; font: inherit; }
.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }
@media (max-width: 760px) { .people-page { padding: 20px 14px; } .people-head { align-items: flex-start; flex-direction: column; } th,td { padding: 11px 10px; white-space: nowrap; } }
</style>
