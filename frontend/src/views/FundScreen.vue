<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <div>
        <h2 class="page-title">基金筛选系统</h2>
        <p class="page-sub">优先看 PE 估值(低估/合理)· 护城河 · 财务 · 安全边际 · 按类型分类</p>
      </div>
      <div class="head-actions">
        <el-button type="success" :disabled="!selectedRows.length" :loading="batchLoading" @click="runBatch">
          批量深度对比({{ selectedRows.length }})
        </el-button>
        <el-button type="primary" :icon="Refresh" :loading="refining" @click="runRefine">刷新行情+精排</el-button>
      </div>
    </div>

    <!-- 我的自选基 · 增删改查 + AI 组合点评 -->
    <WatchlistPanel type="fund" title="我的自选基" />

    <el-tabs v-model="category" @tab-change="onCategoryChange">
      <el-tab-pane v-for="c in categories" :key="c" :label="c" :name="c" />
    </el-tabs>

    <el-card class="section-card" shadow="hover">
      <template #header>
        <div class="card-head">
          <span class="ch-left">
            <b>{{ category }}</b> · 已按 PE估值优先逻辑筛出 {{ total }} 只(最优排最上)· 每页 {{ pageSize }} 只
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
        <el-table-column label="基金 / 代码" min-width="150">
          <template #default="{ row }">
            <div class="name">{{ row.name }}</div>
            <div class="code">{{ row.code }} · <el-tag size="small" effect="plain">{{ row.category }}</el-tag></div>
          </template>
        </el-table-column>
        <el-table-column label="护城河 / 特性" min-width="170">
          <template #default="{ row }">
            <div><span class="moat-score">{{ row.moatScore }}</span> 分</div>
            <el-tag v-for="t in row.moatTags" :key="t" size="small" class="moat-tag" type="warning" effect="plain">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="估值 / 业绩" min-width="180">
          <template #default="{ row }">
            <div class="fin-line" v-if="row.peQuantile>0">PE分位 <b :class="row.peQuantile<30?'up':''">{{ row.peQuantile }}%</b></div>
            <div class="fin-line">近1年 <b :class="row.return1y>=0?'up':'down'">{{ row.return1y }}%</b> · 近3年 <b :class="row.return3y>=0?'up':'down'">{{ row.return3y }}%</b></div>
            <div class="fin-line">最大回撤 <b class="down">{{ row.maxDrawdown }}%</b> · 费率 {{ row.fee }}%</div>
            <div class="fin-line">规模 {{ row.scale }}亿</div>
          </template>
        </el-table-column>
        <el-table-column label="推荐" width="100" align="center">
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
        <el-table-column label="推荐理由 / 操作" width="300">
          <template #default="{ row }">
            <div class="reason-text">{{ row.reason }}</div>
            <div class="ops-row">
              <AiAnalyze scene="fund" :payload="row" />
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
      <el-dialog v-model="batchVisible" title="AI 批量深度对比分析(基金)" width="80%" append-to-body class="batch-dialog">
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
            <MarkdownView :source="deepContent" />
          </template>
          <el-empty v-else description="请先运行「精排分析」或点击「强制刷新」生成分析" :image-size="80" />
        </div>
        <template #footer>
          <el-button @click="deepVisible = false">关闭</el-button>
          <el-button type="warning" :loading="deepLoading" @click="runDeep">强制刷新(重新调用LLM)</el-button>
        </template>
      </el-dialog>

      <el-empty v-if="!list.length && !loading" description="该类型暂无符合条件的基金" />
      <div v-if="list.length" class="pager">
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
import { Refresh, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AiAnalyze from '../components/AiAnalyze.vue'
import WatchlistPanel from '../components/WatchlistPanel.vue'
import { screenApi, aiApi } from '../api'
import MarkdownView from '../components/MarkdownView.vue'

const loading = ref(false)
const refining = ref(false)
const refinedReady = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const category = ref('全部')
const categories = ref(['全部'])
const adviceMap = ref({})
const adviceLoading = ref(false)
const selectedRows = ref([])
const batchVisible = ref(false)
const batchLoading = ref(false)
const batchResult = ref(null)

const deepVisible = ref(false)
const deepLoading = ref(false)
const deepTarget = ref(null)
const deepMap = ref({})

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
}
async function runDeep() {
  if (!deepTarget.value) return
  deepLoading.value = true
  try {
    const code = deepTarget.value.code
    const res = await screenApi.analyzeFund(code, true)
    deepMap.value[code] = res.data
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
    await screenApi.runFund()
    const res = await screenApi.refinedFund(category.value, page.value, pageSize, true)
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
    const res = await screenApi.fund(category.value, page.value, pageSize)
    list.value = res.data.list
    total.value = res.data.total
    if (list.value.length && list.value[0].deepAnalysis) refinedReady.value = true
  } finally { loading.value = false }
  loadAdvice(false)
}

async function loadAdvice(invalidate) {
  adviceLoading.value = true
  try {
    const r = await screenApi.adviceFund(category.value, page.value, pageSize, invalidate)
    const m = {}
    ;(r.data || []).forEach(a => { if (a.code) m[a.code] = a })
    adviceMap.value = m
  } catch (e) {
    // 建议失败不影响主列表
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
  if (!selectedRows.value.length) { ElMessage.warning('请先勾选要对比的基金(可跨页)'); return }
  batchVisible.value = true
  batchLoading.value = true
  try {
    const items = selectedRows.value.map(r => ({
      name: r.name, code: r.code, category: r.category,
      nav: r.nav, return1y: r.return1y, return3y: r.return3y,
      fee: r.fee, scale: r.scale,
      moatScore: r.moatScore, moatTags: r.moatTags,
      score: r.score, rating: r.rating, reason: r.reason
    }))
    const res = await aiApi.analyze('fund-batch', { items, count: items.length })
    batchResult.value = res.data
  } finally { batchLoading.value = false }
}

function onPageChange(p) { page.value = p; load() }
function onCategoryChange() { page.value = 1; load() }

async function run() {
  running.value = true
  try {
    await screenApi.runFund()
    ElMessage.success('已刷新真实行情,正在重算 AI 持有建议...')
    page.value = 1
    await load()
    await loadAdvice(true)
  } finally { running.value = false }
}

onMounted(async () => {
  const c = await screenApi.categories()
  categories.value = c.data
  await load()
})
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.ch-left { display: inline-flex; align-items: center; }
.ds-tag { margin-left: 8px; }
.name { font-weight: 700; }
.code { color: #909399; font-size: 12px; }
.moat-score { color: #e6a23c; font-weight: 700; font-size: 16px; }
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
