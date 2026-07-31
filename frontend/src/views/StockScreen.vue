<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <div>
        <h2 class="page-title">股票筛选系统</h2>
        <p class="page-sub">估值分位 → 护城河 → 十年FCF → DCF内在价值 → 安全边际 → 综合评分排序</p>
      </div>
      <div class="head-actions">
        <el-button type="success" :disabled="!selectedRows.length" :loading="batchLoading" @click="runBatch">
          批量深度对比({{ selectedRows.length }})
        </el-button>
        <el-button type="primary" :icon="Refresh" :loading="refining" @click="runRefine">刷新行情+精排</el-button>
      </div>
    </div>

    <el-alert v-if="aiPolling" type="warning" :closable="false" show-icon class="ai-pending-banner">
      AI 精排分析正在后台计算(约 20-30 秒),完成后本页会自动刷新为完整 AI 结果。
    </el-alert>

    <!-- 我的自选股 · 增删改查 + AI 组合点评 -->
    <WatchlistPanel type="stock" title="我的自选股" />

    <el-card v-if="pipeline.length" class="pipeline-card" shadow="never">
      <el-steps :active="pipeline.length" finish-status="success" simple>
        <el-step v-for="p in pipeline" :key="p" :title="p" />
      </el-steps>
      <div class="scan-info">已扫描 <b>{{ scanned }}</b> 只 · 通过 <b class="up">{{ total }}</b> 只 · {{ updatedAt }}</div>
    </el-card>

    <el-card class="section-card" shadow="hover">
      <template #header>
        <div class="card-head">
          <span class="ch-left">
            <b>筛选结果</b> · 已按估值逻辑默认筛出前 {{ total }} 只(最优排最上)· 每页 {{ pageSize }} 只
            <el-tag v-if="dataSource==='real'" type="success" size="small" effect="dark" class="ds-tag">真实数据</el-tag>
            <el-tag v-else-if="dataSource==='mock'" type="info" size="small" effect="plain" class="ds-tag">演示数据</el-tag>
          </span>
          <span class="page-hint">第 {{ page }}/{{ totalPages }} 页</span>
        </div>
      </template>

      <el-table :data="list" size="default" stripe @selection-change="onSelChange">
        <el-table-column type="selection" width="48" />
        <el-table-column label="#" width="55">
          <template #default="{ row }">
            <span :class="{ 'refined-rank': refinedReady }">{{ row.refinedRank || row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column label="股票 / 代码" min-width="130">
          <template #default="{ row }">
            <div class="name">{{ row.name }}</div>
            <div class="code">{{ row.code }} · {{ row.industry }}</div>
          </template>
        </el-table-column>
        <el-table-column label="护城河" min-width="170">
          <template #default="{ row }">
            <div><el-rate :model-value="row.moatScore/20" disabled size="small" /> <span class="moat-score">{{ row.moatScore }}</span></div>
            <el-tag v-for="t in row.moatTags" :key="t" size="small" class="moat-tag" type="warning" effect="plain">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关键财务" min-width="180">
          <template #default="{ row }">
            <div class="fin-line">PE分位 <b :class="row.peQuantile<30?'up':''">{{ row.peQuantile }}%</b> · ROE <b>{{ row.roe }}%</b></div>
            <div class="fin-line">毛利 {{ row.grossMargin }}% · 负债 {{ row.debtRatio }}%</div>
            <div class="fin-line">经营现金流 {{ row.operatingCashflow }}亿</div>
          </template>
        </el-table-column>
        <el-table-column label="估值 / 安全边际" min-width="150">
          <template #default="{ row }">
            <div class="fin-line">内在值 <b>¥{{ row.intrinsicValue }}</b></div>
            <div class="fin-line">现价 ¥{{ row.price }}</div>
            <div class="fin-line">安全边际 <b :class="row.safetyMargin>=30?'up':'down'">{{ row.safetyMargin }}%</b></div>
          </template>
        </el-table-column>
        <el-table-column label="十年FCF" width="120">
          <template #default="{ row }">
            <FcfSpark :data="row.fcfTrend" />
          </template>
        </el-table-column>
        <el-table-column label="推荐" width="135" align="center">
          <template #default="{ row }">
            <span :class="ratingClass(row.rating)">{{ row.rating }}</span>
            <div class="score">评分 {{ row.score }}</div>
            <div v-if="refinedReady && row.refinedScore" class="refined-score">
              <el-tag :type="refinedTagType(row.refinedRating)" size="small" effect="dark" class="refined-tag">
                精排 {{ row.refinedScore }} · {{ row.refinedRating }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <div class="reason-text">{{ row.reason }}</div>
            <div class="ops-row">
              <AiAnalyze scene="stock" :payload="row" />
              <el-link type="warning" :underline="false" @click="openDeep(row)" class="deep-link">
                <el-icon><View /></el-icon> 详细分析
              </el-link>
              <el-tag v-if="deepMap[row.code]?.mode==='real'" type="success" size="small" effect="plain">已分析</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="建议持有时间 (AI)" min-width="240">
          <template #default="{ row }">
            <div v-if="row.advice" class="advice">
              <div class="adv-row"><span class="adv-tag short">短期</span> {{ row.advice.short?.horizon || '—' }} · 预计 <b :class="retClass(row.advice.short?.returnRange)">{{ row.advice.short?.returnRange || '—' }}</b></div>
              <div class="adv-row"><span class="adv-tag mid">中期</span> {{ row.advice.mid?.horizon || '—' }} · 预计 <b :class="retClass(row.advice.mid?.returnRange)">{{ row.advice.mid?.returnRange || '—' }}</b></div>
              <div class="adv-row"><span class="adv-tag long">长期</span> {{ row.advice.long?.horizon || '—' }} · 预计 <b :class="retClass(row.advice.long?.returnRange)">{{ row.advice.long?.returnRange || '—' }}</b></div>
              <div class="adv-mode">{{ row.advice.mode === 'real' ? 'AI 推算 · ' + (row.advice.model || '') : row.advice.mode === 'rule' ? '规则估算(非AI)' : '—' }}</div>
            </div>
            <span v-else class="no-adv">—</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 批量深度对比分析弹框 -->
      <el-dialog v-model="batchVisible" title="AI 批量深度对比分析" width="80%" append-to-body class="batch-dialog">
        <div class="batch-head">
          <el-tag size="small" type="info">已选 {{ selectedRows.length }} 只</el-tag>
          <span class="batch-name" v-for="r in selectedRows" :key="r.code">{{ r.name }} ({{ r.code }})</span>
        </div>
        <el-divider />
        <div v-loading="batchLoading" style="min-height: 200px">
          <template v-if="batchResult">
            <el-alert type="warning" :closable="false" style="margin-bottom: 12px">{{ batchResult.disclaimer }}</el-alert>
            <div class="batch-mode">
              <el-tag size="small" :type="batchResult.mode === 'real' ? 'success' : 'warning'" effect="dark">
                {{ batchResult.mode === 'real' ? '真实模型' : '演示模式' }}
              </el-tag>
              <span class="batch-model">使用模型:{{ batchResult.model }}</span>
            </div>
            <MarkdownView :source="batchResult.analysis" />
          </template>
          <el-empty v-else description="点击下方按钮开始对比分析" :image-size="80" />
        </div>
        <template #footer>
          <el-button @click="batchVisible = false">关闭</el-button>
          <el-button type="primary" :loading="batchLoading" @click="runBatch">开始对比分析</el-button>
        </template>
      </el-dialog>

      <!-- 深度分析弹框 -->
      <el-dialog v-model="deepVisible" :title="'深度分析 · ' + deepTarget?.name + ' (' + deepTarget?.code + ')'" width="80%" append-to-body>
        <div v-if="deepTarget" style="margin-bottom:10px">
          <el-tag v-for="t in deepTarget.moatTags" :key="t" size="small" type="warning" effect="plain" style="margin-right:4px">{{ t }}</el-tag>
          <el-tag :type="deepTarget.dataSource==='real'?'success':'info'" size="small">{{ deepTarget.dataSource==='real'?'真实数据':'演示数据' }}</el-tag>
          <el-tag v-if="deepModeTag" size="small" type="success" style="margin-left:4px">{{ deepModeTag }}</el-tag>
          <el-tag v-if="deepTarget.refinedScore" size="small" type="danger" effect="dark" style="margin-left:4px">精排{{ deepTarget.refinedScore }}·{{ deepTarget.refinedRating }}</el-tag>
        </div>
        <div style="min-height:300px">
          <template v-if="deepContent">
            <el-alert v-if="deepTarget.confidence==='中'||deepTarget.confidence==='低'" type="warning" :closable="false" style="margin-bottom:10px">
              置信度：{{ deepTarget.confidence }}（该分析存在不确定性，建议结合自身判断）
            </el-alert>
            <MarkdownView :source="deepContent" />
          </template>
          <el-empty v-else description="请先运行「精排分析」或点击「强制刷新」生成分析" :image-size="80" />
        </div>
        <template #footer>
          <el-button @click="deepVisible = false">关闭</el-button>
          <el-button type="warning" :loading="deepLoading" @click="runDeep">强制刷新(重新调用LLM)</el-button>
        </template>
      </el-dialog>

      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AiAnalyze from '../components/AiAnalyze.vue'
import FcfSpark from '../components/FcfSpark.vue'
import WatchlistPanel from '../components/WatchlistPanel.vue'
import { screenApi, aiApi } from '../api'
import MarkdownView from '../components/MarkdownView.vue'

const loading = ref(false)
const aiPolling = ref(false)
const running = ref(false)
const refining = ref(false)
const refinedReady = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const pipeline = ref([])
const scanned = ref(0)
const updatedAt = ref('')
const selectedRows = ref([])
const batchVisible = ref(false)
const batchLoading = ref(false)
const batchResult = ref(null)

// 深度分析
const deepVisible = ref(false)
const deepLoading = ref(false)
const deepTarget = ref(null)
const deepMap = ref({}) // API刷新兜底：openDeep先用row.deepAnalysis，强制刷新时写入这里

const deepContent = computed(() => {
  if (!deepTarget.value) return ''
  return deepTarget.value.deepAnalysis || deepMap.value[deepTarget.value.code]?.analysis || ''
})
const deepModeTag = computed(() => {
  if (!deepTarget.value) return ''
  const m = deepTarget.value.deepMode || deepMap.value[deepTarget.value.code]?.mode
  if (m === 'real') return deepTarget.value.deepModel || 'AI 分析'
  if (m === 'cached') return '缓存命中'
  return ''
})

function openDeep(row) {
  deepTarget.value = row
  deepVisible.value = true
  // 列表精简分析不含长文;若为空自动拉取完整分析(结果会写入缓存)
  if (!row.deepAnalysis) runDeep()
}
async function runDeep() {
  if (!deepTarget.value) return
  deepLoading.value = true
  try {
    const code = deepTarget.value.code
    const res = await screenApi.analyzeStock(code, true) // 强制刷新调用LLM
    deepMap.value[code] = res.data
    // 同步到当前 target（对话框直接显示）
    deepTarget.value.deepAnalysis = res.data.analysis
    deepTarget.value.deepMode = res.data.mode
    deepTarget.value.deepModel = res.data.model
  } catch (e) {
    ElMessage.error('刷新失败: ' + (e?.message || e))
  } finally { deepLoading.value = false }
}

function refinedTagType(r) {
  return { '强烈推荐': 'danger', '推荐': 'warning', '观察': 'info', '回避': 'success' }[r] || 'info'
}

async function runRefine() {
  refining.value = true
  try {
    await screenApi.runStock() // 先刷新定量数据
    const res = await screenApi.refinedStock(page.value, pageSize, true)
    list.value = res.data.list; total.value = res.data.total
    refinedReady.value = true
    ElMessage.success('行情+精排分析完成')
  } catch (e) {
    ElMessage.error('刷新失败: ' + (e?.message || e))
  } finally { refining.value = false }
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const dataSource = computed(() => (list.value.length ? list.value[0].dataSource : ''))

function ratingClass(r) {
  return { '强烈推荐': 'rating-strong', '推荐': 'rating-rec', '观察': 'rating-watch', '回避': 'rating-avoid' }[r] || 'rating-watch'
}

async function load() {
  loading.value = true
  try {
    const res = await screenApi.stock(page.value, pageSize)
    list.value = res.data.list
    total.value = res.data.total
    // 首次加载自动附带 deepAnalysis + advice（精排时一并算出,直接展示）
    if (list.value.length && list.value[0].deepAnalysis) refinedReady.value = true
    // 冷缓存:后台正在跑 AI 精排,启动轮询补全;否则停止轮询
    if (list.value.some(r => r.aiPending)) startAiPoll()
    else stopAiPoll()
  } finally { loading.value = false }
}

let aiPollTimer = null
function startAiPoll() {
  if (aiPolling.value) return
  aiPolling.value = true
  let tries = 0
  aiPollTimer = setInterval(async () => {
    tries++
    if (tries > 8) { stopAiPoll(); return }  // 最多轮询 ~64s
    try {
      const res = await screenApi.stock(page.value, pageSize)
      if (!res.data.list.some(r => r.aiPending)) {
        list.value = res.data.list
        total.value = res.data.total
        if (res.data.list.length && res.data.list[0].deepAnalysis) refinedReady.value = true
        stopAiPoll()
        ElMessage.success('AI 精排已完成')
      }
    } catch (e) { /* 轮询期间忽略错误,继续等待 */ }
  }, 8000)
}
function stopAiPoll() {
  aiPolling.value = false
  if (aiPollTimer) { clearInterval(aiPollTimer); aiPollTimer = null }
}

function retClass(rr) {
  if (!rr) return ''
  const t = String(rr).trim()
  if (t.startsWith('+')) return 'up'
  if (t.startsWith('-')) return 'down'
  return ''
}

function onSelChange(rows) { selectedRows.value = rows; batchResult.value = null }

async function runBatch() {
  if (!selectedRows.value.length) { ElMessage.warning('请先勾选要对比的股票(可跨页)'); return }
  batchVisible.value = true
  batchLoading.value = true
  try {
    const items = selectedRows.value.map(r => ({
      name: r.name, code: r.code, industry: r.industry,
      pe: r.pe, roe: r.roe, grossMargin: r.grossMargin,
      moatScore: r.moatScore, moatTags: r.moatTags,
      intrinsicValue: r.intrinsicValue, price: r.price, safetyMargin: r.safetyMargin,
      score: r.score, rating: r.rating, reason: r.reason
    }))
    const res = await aiApi.analyze('stock-batch', { items, count: items.length })
    batchResult.value = res.data
  } finally { batchLoading.value = false }
}

function onPageChange(p) { page.value = p; load() }

async function run() {
  running.value = true
  try {
    const res = await screenApi.runStock()
    pipeline.value = res.data.pipeline
    scanned.value = res.data.scanned
    updatedAt.value = res.data.updatedAt
    ElMessage.success('已刷新真实行情,正在重算 AI 深度分析+精排...')
    page.value = 1
    await load()
  } finally { running.value = false }
}

onMounted(load)
onUnmounted(stopAiPoll)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.pipeline-card { margin-bottom: 14px; border-radius: 12px; }
.scan-info { text-align: center; margin-top: 10px; color: #606266; font-size: 13px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.ch-left { display: inline-flex; align-items: center; }
.ds-tag { margin-left: 8px; }
.page-hint { font-size: 12px; color: #909399; font-weight: normal; }
.pager { display: flex; justify-content: center; margin-top: 16px; }
.name { font-weight: 700; }
.code { color: #909399; font-size: 12px; }
.moat-score { color: #e6a23c; font-weight: 700; }
.fin-line { font-size: 12px; color: #606266; line-height: 1.6; }
.score { font-size: 11px; color: #909399; margin-top: 3px; }
.up { color: #f56c6c; font-weight: 600; }
.down { color: #67c23a; font-weight: 600; }
.advice { font-size: 12px; line-height: 1.7; }
.adv-row { display: flex; align-items: center; gap: 4px; white-space: nowrap; }
.adv-tag { display: inline-block; padding: 0 6px; border-radius: 4px; font-size: 11px; color: #fff; }
.adv-tag.short { background: #67c23a; }
.adv-tag.mid { background: #409eff; }
.adv-tag.long { background: #909399; }
.adv-mode { margin-top: 4px; font-size: 11px; color: #909399; }
.no-adv { color: #c0c4cc; }
.head-actions { display: flex; gap: 10px; }
.ai-pending-banner { margin-bottom: 14px; }
.batch-head { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 4px; }
.batch-name { font-size: 12px; color: #2b6cb0; background: #f0f6ff; padding: 2px 10px; border-radius: 10px; }
.batch-mode { margin-bottom: 10px; display: flex; align-items: center; gap: 10px; }
.batch-model { font-size: 12px; color: #606266; }
.ops-row { display: flex; align-items: center; gap: 8px; margin-top: 6px; flex-wrap: wrap; }
.deep-link { font-size: 12px; cursor: pointer; }
.deep-result { max-height: 70vh; overflow-y: auto; }
.refined-rank { color: #e6a23c; font-weight: 800; }
.refined-score { margin-top: 4px; }
.refined-tag { width: 100%; white-space: normal; line-height: 1.4; }
.no-adv.loading { color: #e6a23c; }
</style>
