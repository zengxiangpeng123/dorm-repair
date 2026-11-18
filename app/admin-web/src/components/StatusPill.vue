<script setup>
import { STATUS } from '../constants/status'
defineProps({ status: { type: String, required: true }, plain: Boolean })
</script>

<template>
  <span class="pill" :class="{ plain }" :data-st="status">{{ STATUS[status] || status }}</span>
</template>
