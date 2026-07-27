<template>
  <span>
    <el-button :size="size" type="primary" plain :icon="MagicStick" @click="open">{{ label }}</el-button>
    <el-dialog v-model="visible" title="AI 深度分析" width="80%" append-to-body>
      <div class="ai-head">
        <span>使用模型:</span>
        <el-select v-model="modelId" size="small" style="width: 220px" placeholder="选择已配置模型">
          <el-option v-for="c in configs" :key="c.id" :label="c.name + ' / ' + c.model" :value="c.id" />
        </el-select>
        <el-tag size="small" type="info">场景:{{ sceneLabel }}</el-tag>
        <el-tag v-if="result && result.mode" size="small" :type="result.mode === 'real' ? 'success' : 'warning'" effect="dark">
          {{ result.mode === 'real' ? '真实模型' : '演示模式' }}
        </el-tag>
      </div>
      <el-divider />
      <div v-loading="loading" style="min-height: 160px">
        <template v-if="result">
          <el-alert type="warning" :closable="false" style="margin-bottom: 12px">{{ result.disclaimer }}</el-alert>
          <MarkdownView :source="result.analysis" />
          <el-collapse style="margin-top: 14px">
            <el-collapse-item title="查看发送给模型的分析上下文(Prompt)">
              <pre class="ai-prompt">{{ result.prompt }}</pre>
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-empty v-else description="点击下方按钮开始分析" :image-size="80" />
      </div>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" :loading="loading" @click="run">开始分析</el-button>
      </template>
    </el-dialog>
  </span>
</template>

<script setup>
import { ref } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { aiApi, llmApi } from '../api'
import MarkdownView from './MarkdownView.vue'

const props = defineProps({
  scene: { type: String, required: true }, // stock|fund|finance|decision|holding|watchlist
  payload: { type: Object, default: () => ({}) },
  size: { type: String, default: 'small' },
  label: { type: String, default: 'AI 分析' }
})

const visible = ref(false)
const loading = ref(false)
const result = ref(null)
const configs = ref([])
const modelId = ref(null)

const sceneLabelMap = { stock: '股票', fund: '基金', finance: '财务', decision: '决策', holding: '资产负债', watchlist: '自选组合' }
const sceneLabel = sceneLabelMap[props.scene] || props.scene

async function open() {
  visible.value = true
  result.value = null
  try {
    const res = await llmApi.configs()
    configs.value = res.data
    const active = configs.value.find((c) => c.active) || configs.value[0]
    if (active) modelId.value = active.id
  } catch (e) { /* ignore */ }
}

async function run() {
  loading.value = true
  try {
    if (modelId.value) await llmApi.setActive(modelId.value)
    const res = await aiApi.analyze(props.scene, props.payload)
    result.value = res.data
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.ai-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ai-prompt { white-space: pre-wrap; word-break: break-word; color: #909399; font-size: 12px; }
</style>
