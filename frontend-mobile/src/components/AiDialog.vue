<template>
  <van-button size="small" type="primary" plain @click="open">{{ label }}</van-button>
  <van-popup v-model:show="show" position="right" :style="{ width: '100%', height: '100%' }">
    <div class="ai-page">
      <van-nav-bar :title="'AI 分析 · ' + sceneLabel" left-text="返回" left-arrow @click-left="show = false" />
      <div class="ai-body">
        <van-button v-if="!result" type="primary" block :loading="loading" @click="run">开始分析</van-button>
        <div v-else>
          <van-tag v-if="result.mode" :type="result.mode === 'real' ? 'success' : 'warning'" style="margin-bottom:8px">
            {{ result.mode === 'real' ? '真实模型' : '演示模式' }}
          </van-tag>
          <p v-if="result.disclaimer" class="muted" style="font-size:12px">{{ result.disclaimer }}</p>
          <MobileMarkdown :source="result.analysis" />
          <van-button type="primary" block style="margin-top:14px" :loading="loading" @click="run">重新分析</van-button>
        </div>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import { aiApi, llmApi } from '../api'
import MobileMarkdown from './MobileMarkdown.vue'

const props = defineProps({
  scene: { type: String, required: true },
  payload: { type: Object, default: () => ({}) },
  label: { type: String, default: 'AI 分析' }
})

const show = ref(false)
const loading = ref(false)
const result = ref(null)
const sceneLabelMap = { stock: '股票', fund: '基金', finance: '财务', decision: '决策', holding: '资产负债', watchlist: '自选组合', 'stock-batch': '股票对比', 'fund-batch': '基金对比' }
const sceneLabel = sceneLabelMap[props.scene] || props.scene

async function open() {
  show.value = true
  result.value = null
}
async function run() {
  loading.value = true
  try {
    const res = await aiApi.analyze(props.scene, props.payload)
    result.value = res.data
  } catch (e) {
    showToast('分析失败,请检查 AI 模型配置')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.ai-page { height: 100%; background: #f5f7fa; }
.ai-body { padding: 14px; overflow-y: auto; }
</style>
