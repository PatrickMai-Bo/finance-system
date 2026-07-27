<template>
  <svg :width="w" :height="h" :viewBox="`0 0 ${w} ${h}`" v-if="points">
    <polyline :points="points" fill="none" stroke="#2b6cb0" stroke-width="1.5" />
    <circle :cx="last.x" :cy="last.y" r="2" fill="#f56c6c" />
  </svg>
  <span v-else class="muted">—</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  w: { type: Number, default: 90 },
  h: { type: Number, default: 28 }
})

const points = computed(() => {
  const d = props.data
  if (!d || d.length < 2) return ''
  const max = Math.max(...d)
  const min = Math.min(...d)
  const range = max - min || 1
  const step = props.w / (d.length - 1)
  return d
    .map((v, i) => {
      const x = i * step
      const y = props.h - ((v - min) / range) * (props.h - 4) - 2
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

const last = computed(() => {
  const arr = points.value.split(' ')
  const p = arr[arr.length - 1].split(',')
  return { x: parseFloat(p[0]), y: parseFloat(p[1]) }
})
</script>
