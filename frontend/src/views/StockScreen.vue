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
        <el-button type="warning" :icon="RefreshRight" :loading="refining" @click="runRefine">
          精排分析(5规则+LLM重评分)
        </el-button>
        <el-button type="primary" :icon="Refresh" :loading="running" @click="run">刷新行情</el-button>
      </div>
    </div>

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
        <el-table-column label="推荐" width="110" align="center">
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
        <el-table-column label="建议持有时间 (AI)" min-width="230">
          <template #default="{ row }">
            <div v-if="adviceMap[row.code]" class="advice">
              <div class="adv-row"><span class="adv-tag short">短期</span> {{ adviceMap[row.code].short?.horizon }} · 预计 <b :class="retClass(adviceMap[row.code].short?.returnRange)">{{ adviceMap[row.code].short?.returnRange }}</b></div>
              <div class="adv-row"><span class="adv-tag mid">中期</span> {{ adviceMap[row.code].mid?.horizon }} · 预计 <b :class="retClass(adviceMap[row.code].mid?.returnRange)">{{ adviceMap[row.code].mid?.returnRange }}</b></div>
              <div class="adv-row"><span class="adv-tag long">长期</span> {{ adviceMap[row.code].long?.horizon }} · 预计 <b :class="retClass(adviceMap[row.code].long?.returnRange)">{{ adviceMap[row.code].long?.returnRange }}</b></div>
              <div class="adv-mode">{{ adviceMap[row.code].mode === 'real' ? 'AI 推算 · ' + (adviceMap[row.code].model || '') : adviceMap[row.code].mode === 'rule' ? '规则估算(非AI)' : '' }}</div>
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
          <el-tag v-if="deepMap[deepTarget.code]?.mode==='real'" size="small" type="success" style="margin-left:4px">
            {{ deepMap[deepTarget.code]?.model || 'AI 分析' }}
          </el-tag>
        </div>
        <div v-loading="deepLoading" style="min-height:300px">
          <div v-if="deepMap[deepTarget?.code]" class="deep-result">
            <MarkdownView v-if="deepMap[deepTarget.code].analysis" :source="deepMap[deepTarget.code].analysis" />
            <el-empty v-else description="分析结果为空" :image-size="80" />
          </div>
          <el-empty v-else description="点击下方按钮开始AI深度分析" :image-size="80" />
        </div>
        <template #footer>
          <el-button @click="deepVisible = false">关闭</el-button>
          <el-button type="warning" :loading="deepLoading" @click="runDeep(false)">开始分析</el-button>
          <el-button type="primary" :loading="deepLoading" @click="runDeep(true)">强制刷新</el-button>
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
import { ref, computed, onMounted } from 'vue'
import { Refresh, View, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AiAnalyze from '../components/AiAnalyze.vue'
import FcfSpark from '../components/FcfSpark.vue'
import WatchlistPanel from '../components/WatchlistPanel.vue'
import { screenApi, aiApi } from '../api'
import MarkdownView from '../components/MarkdownView.vue'

const loading = ref(false)
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
const adviceMap = ref({})
const adviceLoading = ref(false)
const selectedRows = ref([])
const batchVisible = ref(false)
const batchLoading = ref(false)
const batchResult = ref(null)

// 深度分析
const deepVisible = ref(false)
const deepLoading = ref(false)
const deepTarget = ref(null)
const deepMap = ref({})

function openDeep(row) {
  deepTarget.value = row
  deepVisible.value = true
}
async function runDeep(invalidate) {
  if (!deepTarget.value) return
  deepLoading.value = true
  try {
    const code = deepTarget.value.code
    const res = await screenApi.analyzeStock(code, invalidate)
    deepMap.value[code] = res.data
  } catch (e) {
    ElMessage.error('分析失败: ' + (e?.message || e))
  } finally { deepLoading.value = false }
}

function refinedTagType(r) {
  return { '强烈推荐': 'danger', '推荐': 'warning', '观察': 'info', '回避': 'success' }[r] || 'info'
}

async function runRefine() {
  refining.value = true
  refinedReady.value = false
  try {
    const res = await screenApi.refinedStock(page.value, pageSize, true)
    list.value = res.data.list
    total.value = res.data.total
    refinedReady.value = true
    ElMessage.success('精排分析完成！已按5条优化规则+LLM重新评分排序')
  } catch (e) {
    ElMessage.error('精排分析失败: ' + (e?.message || e))
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
  } finally { loading.value = false }
  loadAdvice(false)
}

async function loadAdvice(invalidate) {
  adviceLoading.value = true
  try {
    const r = await screenApi.adviceStock(page.value, pageSize, invalidate)
    const m = {}
    ;(r.data || []).forEach(a => { if (a.code) m[a.code] = a })
    adviceMap.value = m
  } catch (e) {
    // 建议生成失败不影响主列表展示
  } finally { adviceLoading.value = false }
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
    ElMessage.success('已刷新真实行情,正在重算 AI 持有建议...')
    page.value = 1
    await load()
    await loadAdvice(true)
  } finally { running.value = false }
}

onMounted(load)
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
.batch-head { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 4px; }
.batch-name { font-size: 12px; color: #2b6cb0; background: #f0f6ff; padding: 2px 10px; border-radius: 10px; }
.batch-mode { margin-bottom: 10px; display: flex; align-items: center; gap: 10px; }
.batch-model { font-size: 12px; color: #606266; }
.ops-row { display: flex; align-items: center; gap: 8px; margin-top: 6px; flex-wrap: wrap; }
.deep-link { font-size: 12px; cursor: pointer; }
.deep-result { max-height: 70vh; overflow-y: auto; }
.refined-rank { color: #e6a23c; font-weight: 800; }
.refined-score { margin-top: 4px; }
.refined-tag { max-width: 140px; white-space: normal; line-height: 1.4; }
</style>
