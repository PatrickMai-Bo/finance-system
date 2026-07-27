<template>
  <div ref="el" style="width:100px;height:40px"></div>
</template>
<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Array, default: () => [] } })
const el = ref(null)
let chart = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  const d = props.data || []
  const rising = d.length > 1 && d[d.length - 1] >= d[0]
  chart.setOption({
    grid: { left: 2, right: 2, top: 4, bottom: 4 },
    xAxis: { type: 'category', show: false, data: d.map((_, i) => i) },
    yAxis: { type: 'value', show: false },
    tooltip: { trigger: 'axis', formatter: (p) => '第' + (p[0].dataIndex + 1) + '年 FCF: ' + p[0].value + '亿' },
    series: [{
      type: 'line', data: d, smooth: true, symbol: 'none',
      lineStyle: { color: rising ? '#e53935' : '#43a047', width: 2 },
      areaStyle: { color: rising ? 'rgba(229,57,53,0.12)' : 'rgba(67,160,71,0.12)' }
    }]
  })
}

onMounted(() => nextTick(render))
watch(() => props.data, () => nextTick(render))
</script>
