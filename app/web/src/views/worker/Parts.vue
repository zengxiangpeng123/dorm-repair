<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import Plate from '../../components/Plate.vue'
import StatusPill from '../../components/StatusPill.vue'
import { ordersApi } from '../../api/orders'
import { describeError } from '../../api/request'

const router = useRouter()
const list = ref([])
const meta = ref(null)
const loading = ref(true)
const err = ref('')
const busy = ref('')

const budgetLine = computed(() => meta.value?.budgetLine ?? 200)
const over = (o) => Number(o.partsAmount) > budgetLine.value

async function load () {
  try {
    const [r, m] = await Promise.all([ordersApi.myOrders('PARTS'), ordersApi.meta()])
    list.value = r.items || []
    meta.value = m
    err.value = ''
  } catch (e) {
    err.value = describeError(e)
  } finally {
    loading.value = false
  }
}

/**
 * 仓管确认到货是 ADMIN/SYSTEM 的动作（PARTS_ARRIVED 的合法触发方里没有师傅）。
 * 演示环境里页面替仓管点这一下，所以要显式以 admin 身份发请求 —— 不然服务端会按师傅判 403。
 */
async function arrived (o) {
  busy.value = o.no
  err.value = ''
  try {
    await ordersApi.partsArrived(o.no, { version: o.version }, { role: 'admin' })
    await load()
  } catch (e) {
    err.value = describeError(e)
    if (e.status === 409) load()
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<template>
  <header class="top">
    <b>等配件</b>
    <span class="tiny muted">{{ list.length }} 单</span>
  </header>

  <div class="body">
    <p v-if="err" class="err">{{ err }}</p>

    <div v-for="o in list" :key="o.no" class="card">
      <div class="between">
        <div class="row">
          <Plate :room="o.room" :floor="o.floor" status="PARTS" />
          <div>
            <div class="rno">{{ o.floor }}F-{{ o.room }}</div>
            <div class="tiny muted num">{{ o.no }}</div>
          </div>
        </div>
        <StatusPill status="PARTS" />
      </div>

      <div class="money" style="margin-top: 11px">
        <div><div class="n">{{ o.partsName || '配件' }}</div><div class="k">配件</div></div>
        <div>
          <div class="n num" :class="{ hot: over(o) }">¥{{ o.partsAmount }}</div>
          <div class="k">金额</div>
        </div>
        <div><div class="n num">{{ o.partsEta || '—' }}</div><div class="k">预计到货</div></div>
      </div>

      <div v-if="over(o)" class="note" data-tone="warn" style="margin-top: 10px">
        ¥{{ o.partsAmount }} 超过 {{ budgetLine }} 元预算线，这单在管理端「超预算采购」清单里等审批。
      </div>

      <button class="btn" :disabled="busy === o.no" @click="arrived(o)">
        {{ busy === o.no ? '确认中…' : '到货了，确认收货' }}
      </button>
    </div>

    <div v-if="!loading && !list.length" class="card empty">
      现在没有等配件的单。缺件时在单间作业里登记，配件到了这单会自己回队列。
    </div>

    <div class="note" style="margin-top: 12px">
      <b>为什么让你填金额</b><br />
      金额不是走形式：它决定这单要不要管理员批。超过预算线会挂起来等审批，配件到了这单回队列，定级不降。
    </div>
  </div>

  <BottomNav role="worker" />
</template>

<style scoped>
.top { display: flex; align-items: center; justify-content: space-between; padding: 13px 16px 10px; font-size: 16px; }
.err { color: var(--danger); font-size: 13px; }
.rno { font-size: 14.5px; font-weight: 650; }
.btn {
  width: 100%; margin-top: 11px; height: 42px; border-radius: 11px;
  border: 1px solid var(--line); background: #fff; font-size: 14px; font-weight: 650; color: var(--ink);
}
.btn:disabled { opacity: .6; }
</style>
