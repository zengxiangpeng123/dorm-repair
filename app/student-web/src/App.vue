<script setup>
import { onMounted, onUnmounted } from 'vue'

// 截图模式：?shot=1 时把粘底导航和浮起动作条放回文档流，整页长图才不会被遮住。
function syncShotMode() {
  document.body.classList.toggle('shot', new URLSearchParams(location.search).get('shot') === '1')
}
onMounted(syncShotMode)
onUnmounted(() => document.body.classList.remove('shot'))
</script>

<template><router-view /></template>
<style>#app { min-height: 100vh; }</style>
