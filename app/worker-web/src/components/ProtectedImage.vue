<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fileApi } from '../api/files'

const props = defineProps({
  src: { type: String, required: true },
  alt: { type: String, default: '照片' }
})
const url = ref('')
let objectUrl = ''

async function load () {
  if (objectUrl) URL.revokeObjectURL(objectUrl)
  objectUrl = ''
  url.value = ''
  if (!props.src || props.src.startsWith('demo://')) return
  try {
    const blob = await fileApi.blob(props.src.split('/').pop())
    objectUrl = URL.createObjectURL(blob)
    url.value = objectUrl
  } catch {
    url.value = ''
  }
}

onMounted(load)
watch(() => props.src, load)
onBeforeUnmount(() => { if (objectUrl) URL.revokeObjectURL(objectUrl) })
</script>

<template>
  <img v-if="url" :src="url" :alt="alt" />
  <template v-else><span>📷</span><span>{{ alt }}</span></template>
</template>
