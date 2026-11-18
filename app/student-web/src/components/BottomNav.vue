<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({ role: { type: String, default: 'student' } })
const route = useRoute()
const router = useRouter()

// 手机端不跳桌面页：'说明'就地开一层弹层讲清规矩
const help = ref(false)

const ICONS = {
  home: 'M3 10.5 12 3l9 7.5V21H3z',
  list: 'M4 6h16M4 12h16M4 18h10',
  plus: 'M12 5v14M5 12h14',
  wrench: 'M14 7a4 4 0 1 0 5 5L9 22l-3-3 10-10z',
  stairs: 'M4 19h5v-4h5v-4h5V7',
  box: 'M4 8h16v12H4zM4 8l8-4 8 4',
  monitor: 'M3 5h18v11H3zM8 20h8',
  info: 'M12 8h.01M11 12h1v5h1'
}

const items = computed(() => {
  return [
    { to: '/student', label: '首页', icon: 'home', match: (p) => p === '/student' },
    { to: '/student/list', label: '我的报修', icon: 'list', match: (p) => p.startsWith('/student/list') || p.startsWith('/student/detail') },
    { to: '/student/new', label: '报修', icon: 'plus', match: (p) => p.startsWith('/student/new'), cta: true },
    { to: '#help', label: '说明', icon: 'info', match: () => false, sheet: true }
  ]
})

function go (it) {
  if (it.sheet) { help.value = true; return }
  router.push(it.to)
}
</script>

<template>
  <nav class="bottom-nav">
    <a
      v-for="it in items" :key="it.to" href="#"
      :class="{ on: it.match(route.path), cta: it.cta }"
      @click.prevent="go(it)"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path :d="ICONS[it.icon]" />
      </svg>
      <span>{{ it.label }}</span>
    </a>
  </nav>

  <el-dialog v-model="help" title="这套东西的规矩" width="330" align-center append-to-body>
      <div class="hp">
        <p><b>你是谁</b>学生只做三件事：拍一张、说一句、选个方便的时间。紧急程度不用你选，系统按描述判，判不准就往上抬一档。</p>
        <p><b>不用等人点头</b>报修进来自动排进本楼队列、自动派给师傅。学生不用催，师傅不用接单。</p>
        <p><b>两颗计时器</b>师傅报完工后，你有 7 天（168 小时）验收，不表态就自动验收；验收后 7 天归档，之后这单只读。</p>
        <p><b>什么时候能撤</b>师傅还没进楼，你可以直接撤销；进楼之后只能申请，由管理员定。</p>
        <p><b>有异议怎么办</b>验收时点「还没修好」只会提交返修申请；管理员审查确认是维修质量问题后，才生成带 -R1 的返修单，原单历史全部保留。</p>
      </div>
  </el-dialog>
</template>

<style scoped>
.bottom-nav a.cta {
  margin: -18px 6px 0;
  border-radius: 16px;
  background: var(--navy);
  color: #fff;
  box-shadow: var(--sh-md);
}
.bottom-nav a.cta span { color: #fff; }
.bottom-nav a.cta svg { width: 22px; height: 22px; }
.hp { font-size: 13px; line-height: 1.75; color: var(--ink-2); }
.hp p { margin: 0 0 9px; }
.hp p:last-child { margin-bottom: 0; }
.hp b { color: var(--ink); margin-right: 5px; }
</style>
