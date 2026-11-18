<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const shell = computed(() => route.meta.shell || 'phone')
const wide = computed(() => shell.value === 'desk')

// 截图模式：?shot=1 时把粘底导航与浮起动作条放回文档流，否则整页图里永远拍不到它们
onMounted(() => {
  if (location.search.includes('shot=1')) document.body.classList.add('shot')
})
</script>

<template>
  <div v-if="wide" class="desk-page">
    <router-view />
  </div>
  <div v-else class="stage">
    <router-view />
  </div>
</template>

<style>
.desk-page { min-height: 100%; background: #f4f6f9; }
</style>
