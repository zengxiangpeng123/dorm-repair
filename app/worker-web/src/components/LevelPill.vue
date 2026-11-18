<script setup>
defineProps({ level: { type: String, default: '' } })
</script>

<template>
  <span v-if="level" class="pill lv" :data-lv="level">{{ level }}</span>
</template>
