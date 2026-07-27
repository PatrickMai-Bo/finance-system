<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <div>
        <h2 class="page-title">决策思维系统</h2>
        <p class="page-sub">五阶避错思维框架 · 覆盖投资 / 职业 / 消费 / 关系 / 健康 / 学习等人生方方面面</p>
      </div>
      <div class="head-ai">
        <el-select v-model="scene" size="default" style="width:130px" placeholder="选择场景">
          <el-option v-for="s in fw?.scenes || []" :key="s.key" :label="s.name" :value="s.key" />
        </el-select>
        <el-input v-model="quickDecision" size="default" placeholder="直接输入一个决策,让 AI 给建议" style="width:280px" />
        <AiAnalyze scene="decision" :payload="quickPayload" label="AI 决策咨询" />
      </div>
    </div>

    <!-- 框架总览 -->
    <el-card v-if="fw" class="section-card motto-card" shadow="hover">
      <div class="motto">宗旨 · {{ fw.motto }}</div>
      <div class="pillars">
        <div class="pillar" v-for="p in fw.pillars" :key="p.name">
          <div class="pillar-name">{{ p.name }}</div>
          <div class="pillar-point">{{ p.point }}</div>
          <el-tag size="small" effect="plain" type="info">{{ p.book }}</el-tag>
        </div>
      </div>
    </el-card>

    <!-- 五步决策向导 -->
    <el-card v-if="fw" class="section-card" shadow="hover">
      <template #header><b>AI 五阶决策分析</b> · 写下你的决策,让 AI 按五阶避错框架自动分析并给出最终建议</template>

      <div class="wizard-top">
        <el-select v-model="scene" placeholder="选择决策场景" style="width:150px">
          <el-option v-for="s in fw.scenes" :key="s.key" :label="s.name" :value="s.key" />
        </el-select>
        <el-input v-model="decision" size="large" :placeholder="scenePlaceholder" class="wizard-input" @keyup.enter="startWizard" />
        <el-button type="primary" size="large" @click="startWizard">开始排查</el-button>
      </div>

      <div v-if="started" class="wizard-body">
        <el-alert type="info" :closable="false" style="margin-bottom: 14px">
          <div><b>决策:</b>{{ decision }} <el-tag size="small" effect="plain" style="margin-left:6px">{{ sceneName }}</el-tag></div>
          <div style="margin-top:4px;color:#909399;font-size:12px">AI 将按『五阶避错框架』自动分析,并给出最终建议及理由</div>
        </el-alert>
        <div v-loading="aiLoading" style="min-height: 220px">
          <MarkdownView v-if="aiResult" :source="aiResult" />
          <el-empty v-else-if="!aiLoading" description="点击下方「让 AI 重新分析」开始" :image-size="80" />
        </div>
        <div class="step-actions">
          <el-button @click="resetWizard">清空结果</el-button>
          <el-button type="primary" :loading="aiLoading" @click="startWizard">让 AI 重新分析</el-button>
        </div>
      </div>
    </el-card>

    <!-- 决策检查清单 + 3秒速查 -->
    <el-row :gutter="16" v-if="fw">
      <el-col :xs="24" :md="14">
        <el-card class="section-card" shadow="hover">
          <template #header><b>决策检查清单</b> · 拍板前逐条过一遍</template>
          <div class="check-list">
            <div class="check-row" v-for="(c, i) in fw.checklist" :key="i">
              <el-icon color="#67c23a"><CircleCheck /></el-icon> {{ c }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card class="section-card quick-card" shadow="hover">
          <template #header><b>3秒速查版</b> · 来不及细想时用</template>
          <div class="quick-row" v-for="(c, i) in fw.quickCheck" :key="i">{{ c }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 思维模型库 -->
    <el-card class="section-card" shadow="hover">
      <template #header><b>思维模型库</b> · 三本书核心心智模型</template>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" v-for="m in models" :key="m.name" style="margin-bottom:16px">
          <div class="model-card">
            <div class="model-head">
              <span class="model-name">{{ m.name }}</span>
              <el-tag size="small" type="primary" effect="plain">{{ m.book }}</el-tag>
            </div>
            <p class="model-desc">{{ m.desc }}</p>
            <div class="model-usage"><el-icon><MagicStick /></el-icon> {{ m.usage }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 每周错误复盘 + 决策日志 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="9" v-if="fw">
        <el-card class="section-card" shadow="hover">
          <template #header><b>每周错误复盘模板</b></template>
          <ol class="review-tpl">
            <li v-for="(r, i) in fw.reviewTemplate" :key="i">{{ r }}</li>
          </ol>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="15">
        <el-card class="section-card" shadow="hover">
          <template #header><b>决策日志与复盘</b></template>
          <el-table :data="logs" size="default">
            <el-table-column prop="date" label="日期" width="105" />
            <el-table-column prop="title" label="决策" min-width="150" />
            <el-table-column prop="model" label="依据模型" min-width="130" />
            <el-table-column prop="review" label="复盘" min-width="150">
              <template #default="{ row }">
                <el-tag size="small" :type="row.review.includes('正确')?'success':'info'">{{ row.review }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { MagicStick, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AiAnalyze from '../components/AiAnalyze.vue'
import MarkdownView from '../components/MarkdownView.vue'
import { decisionApi, aiApi } from '../api'

const loading = ref(false)
const models = ref([])
const logs = ref([])
const fw = ref(null)

const scene = ref('invest')
const decision = ref('')
const quickDecision = ref('')
const started = ref(false)
const aiLoading = ref(false)
const aiResult = ref('')

const sceneName = computed(() => {
  const s = fw.value?.scenes?.find(x => x.key === scene.value)
  return s ? s.name : ''
})
const scenePlaceholder = computed(() => {
  const s = fw.value?.scenes?.find(x => x.key === scene.value)
  return s ? '例如:' + s.example : '输入你的决策问题'
})
const totalAsks = computed(() => fw.value ? fw.value.steps.reduce((n, s) => n + s.asks.length, 0) : 0)
const checkedCount = computed(() => 0)

const quickPayload = computed(() => ({
  decision: quickDecision.value || '(未填写具体决策,请就该场景给出通用避错要点)',
  scene: sceneName.value,
  mode: '快速咨询(未走五步向导)',
  checks: []
}))

const wizardPayload = computed(() => ({
  decision: decision.value,
  scene: sceneName.value
}))

async function startWizard() {
  if (!decision.value.trim()) { ElMessage.warning('请先输入你的决策问题'); return }
  started.value = true
  aiResult.value = ''
  aiLoading.value = true
  try {
    const res = await aiApi.analyze('decision', { decision: decision.value, scene: sceneName.value })
    aiResult.value = res.data.analysis
  } catch (e) {
    aiResult.value = '⚠️ AI 分析失败:' + (e?.message || e) + '\n请检查 AI 模型配置或稍后重试。'
  } finally { aiLoading.value = false }
}
function resetWizard() { aiResult.value = ''; aiLoading.value = false }

onMounted(async () => {
  loading.value = true
  try {
    const [m, l, f] = await Promise.all([decisionApi.models(), decisionApi.logs(), decisionApi.framework()])
    models.value = m.data
    logs.value = l.data
    fw.value = f.data
  } finally { loading.value = false }
})
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; margin-bottom: 6px; }
.head-ai { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.motto-card { background: linear-gradient(135deg, #f5f7fa, #eef4ff); }
.motto { font-size: 18px; font-weight: 800; color: #2b4a8b; margin-bottom: 14px; }
.pillars { display: flex; gap: 14px; flex-wrap: wrap; }
.pillar { flex: 1; min-width: 200px; background: #fff; border-radius: 10px; padding: 14px; }
.pillar-name { font-weight: 700; font-size: 15px; margin-bottom: 6px; }
.pillar-point { font-size: 13px; color: #606266; line-height: 1.6; margin-bottom: 8px; }
.wizard-top { display: flex; gap: 10px; align-items: center; }
.wizard-input { flex: 1; }
.wizard-body { margin-top: 8px; }
.step-panel { background: #fafbfc; border: 1px solid #ebeef5; border-radius: 12px; padding: 18px; }
.step-title { font-size: 16px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.step-no { display: inline-flex; width: 26px; height: 26px; border-radius: 50%; background: #2b6cb0; color: #fff; align-items: center; justify-content: center; font-size: 14px; }
.step-guide { color: #606266; font-size: 13px; margin: 10px 0 14px; line-height: 1.6; }
.asks { display: flex; flex-direction: column; gap: 12px; }
.ask-item { display: flex; align-items: center; gap: 12px; }
.ask-note { max-width: 320px; }
.step-actions { margin-top: 18px; display: flex; gap: 10px; justify-content: flex-end; }
.wizard-body { margin-top: 8px; }
.check-list { display: flex; flex-direction: column; gap: 10px; }
.check-row { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #303133; }
.quick-card { background: #fff8f0; }
.quick-row { font-size: 13px; color: #7a4a00; padding: 7px 0; border-bottom: 1px dashed #f0d9b5; }
.quick-row:last-child { border-bottom: none; }
.model-card { border: 1px solid #ebeef5; border-radius: 12px; padding: 16px; height: 100%; transition: box-shadow 0.15s; }
.model-card:hover { box-shadow: 0 6px 18px rgba(0,0,0,0.08); }
.model-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.model-name { font-weight: 700; font-size: 16px; }
.model-desc { font-size: 13px; color: #606266; line-height: 1.6; min-height: 60px; }
.model-usage { font-size: 12px; color: #2b6cb0; background: #f0f6ff; padding: 8px; border-radius: 8px; }
.review-tpl { padding-left: 18px; color: #606266; font-size: 13px; line-height: 2; }
</style>
