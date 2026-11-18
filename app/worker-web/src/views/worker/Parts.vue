<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomNav from '../../components/BottomNav.vue'
import Plate from '../../components/Plate.vue'
import StatusPill from '../../components/StatusPill.vue'
import { workerApi } from '../../api/orders'
import { describeError } from '../../api/request'

const router = useRouter()
const list = ref([])
const meta = ref(null)
const loading = ref(true)
const err = ref('')

const budgetLine = computed(() => meta.value?.budgetLine ?? 200)
const over = (o) => Number(o.partsAmount) > budgetLine.value

async function load () {
  try {
    const [t, m] = await Promise.all([workerApi.workerTodo(), workerApi.meta()])
    list.value = (t.buildings || [])
      .flatMap((b) => b.orders || [])
      .filter((o) => o.status === 'PARTS')
    meta.value = m
    err.value = ''
  } catch (e) {
    err.value = describeError(e)
  } finally {
    loading.value = false
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
    </div>

    <div v-if="!loading && !list.length" class="card empty">
      现在没有等配件的单。缺件时在单间作业里登记，管理员确认到货后，这单会回队列。
    </div>

    <div class="note" style="margin-top: 12px">
      <b>为什么让你填金额</b><br />
      金额不是走形式：超过预算线会进入管理端例外清单。管理员确认到货后，这单回队列，定级不降。
    </div>
  </div>

  <BottomNav role="worker" />
</template>

<style scoped>
.top { display: flex; align-items: center; justify-content: space-between; padding: 13px 16px 10px; font-size: 16px; }
.err { color: var(--danger); font-size: 13px; }
.rno { font-size: 14.5px; font-weight: 650; }
</style>
