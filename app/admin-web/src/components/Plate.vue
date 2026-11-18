<script setup>
/** 门牌：房间号 + 楼层，颜色跟状态走。列表里重复出现，所以它必须编码信息，不做纯装饰。 */
defineProps({
  room: { type: String, required: true },
  floor: { type: [Number, String], default: '' },
  status: { type: String, default: 'PENDING' },
  large: Boolean
})
</script>

<template>
  <div class="plate" :class="{ lg: large }" :data-st="status">
    <span class="r num">{{ room }}</span>
    <span v-if="floor" class="f">{{ floor }}F</span>
  </div>
</template>
