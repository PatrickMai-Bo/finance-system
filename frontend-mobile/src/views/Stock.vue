<template>
  <div class="page">
    <div class="head">
      <div class="m-title">股票筛选</div>
      <div class="head-ops">
        <van-button size="small" type="primary" :loading="running" @click="run">刷新行情</van-button>
        <van-button size="small" type="success" @click="showWatch = true">自选股</van-button>
      </div>
    </div>

    <div v-if="pipeline.length" class="scan">
      已扫描 <b>{{ scanned }}</b> · 通过 <b class="up">{{ total }}</b> · {{ updatedAt }}
    </div>

    <div class="tag-line" v-if="dataSource">
      <van-tag v-if="dataSource === 'real'" type="success">真实数据</van-tag>
      <van-tag v-else type="default">演示数据</van-tag>
      <span class="muted">第 {{ page }}/{{ totalPages }} 页</span>
    </div>

    <van-loading v-if="loading" class="center" />
    <div v-for="s in list" :key="s.code" class="m-card scard">
      <div class="sc-top">
        <b class="nm">{{ s.name }}</b>
        <span class="code">{{ s.code }} · {{ s.industry }}</span>
        <span :class="ratingClass(s.rating)">{{ s.rating }}</span>
      </div>
      <div class="sc-fin">
        <span>PE分位 <b :class="s.peQuantile < 30 ? 'up' : ''">{{ s.peQuantile }}%</b></span>
        <span>ROE {{ s.roe }}%</span>
        <span>毛利 {{ s.grossMargin }}%</span>
        <span>负债 {{ s.debtRatio }}%</span>
      </div>
      <div class="sc-val">
        <span>内在值 <b>¥{{ s.intrinsicValue }}</b></span>
        <span>现价 ¥{{ s.price }}</span>
        <span>安全边际 <b :class="s.safetyMargin >= 30 ? 'up' : 'down'">{{ s.safetyMargin }}%</b></span>
        <Sparkline :data="s.fcfTrend" />
      </div>
      <div class="moat">
        <van-rate :model-value="s.moatScore / 20" disabled size="12" /> <span class="ms">{{ s.moatScore }}</span>
        <van-tag v-for="t in s.moatTags" :key="t" size="mini" type="warning" plain>{{ t }}</van-tag>
      </div>
      <div class="reason">{{ s.reason }}</div>
      <div v-if="adviceMap[s.code]" class="adv">
        <div class="adv-row"><span class="at short">短期</span> {{ adviceMap[s.code].short?.horizon }} · 预计 <b :class="retClass(adviceMap[s.code].short?.returnRange)">{{ adviceMap[s.code].short?.returnRange }}</b></div>
        <div class="adv-row"><span class="at mid">中期</span> {{ adviceMap[s.code].mid?.horizon }} · 预计 <b :class="retClass(adviceMap[s.code].mid?.returnRange)">{{ adviceMap[s.code].mid?.returnRange }}</b></div>
        <div class="adv-row"><span class="at long">长期</span> {{ adviceMap[s.code].long?.horizon }} · 预计 <b :class="retClass(adviceMap[s.code].long?.returnRange)">{{ adviceMap[s.code].long?.returnRange }}</b></div>
      </div>
      <AiDialog scene="stock" :payload="s" label="AI 分析" />
      <van-button size="small" type="warning" plain block style="margin-top:6px;font-size:11px" :loading="deepLoadingMap[s.code]" @click="openDeep(s)">
        详细分析（7段深度评估）
      </van-button>
    </div>

    <!-- 深度分析弹框 -->
    <van-popup v-model:show="deepVisible" position="right" :style="{ width: '100%', height: '100%' }">
      <div class="deep-page">
        <van-nav-bar title="AI 深度分析" left-text="返回" left-arrow @click-left="deepVisible = false" />
        <div class="deep-body">
          <div v-if="deepName" style="margin-bottom:8px">
            <b>{{ deepName }}</b> <span class="code">{{ deepCode }}</span>
          </div>
          <van-tag v-for="t in deepTags" :key="t" size="mini" type="warning" plain style="margin-right:4px">{{ t }}</van-tag>
          <van-tag v-if="deepMode==='real'" size="mini" type="success" style="margin-top:4px">{{ deepModel }}</van-tag>
          <van-loading v-if="deepLoading" style="margin-top:20px;display:block;text-align:center" />
          <MobileMarkdown v-else-if="deepResult" :source="deepResult" />
          <van-empty v-else description="点击下方按钮开始 AI 深度分析" :image-size="60" style="margin-top:20px" />
        </div>
        <div class="deep-foot">
          <van-button type="warning" size="small" :loading="deepLoading" @click="runDeep(false)">开始分析</van-button>
          <van-button type="primary" size="small" :loading="deepLoading" @click="runDeep(true)">强制刷新</van-button>
        </div>
      </div>
    </van-popup>

    <div class="pager">
      <van-button size="small" :disabled="page <= 1" @click="onPage(page - 1)">上一页</van-button>
      <span class="muted">{{ page }}/{{ totalPages }}</span>
      <van-button size="small" :disabled="page >= totalPages" @click="onPage(page + 1)">下一页</van-button>
    </div>

    <!-- 自选股弹框 -->
    <van-popup v-model:show="showWatch" position="bottom" round :style="{ height: '85%' }">
      <div class="dlg">
        <van-nav-bar title="我的自选股" left-text="关闭" left-arrow @click-left="showWatch = false" />
        <div class="wl-head">
          <span class="muted">共 {{ watch.length }} 只</span>
          <van-button size="mini" type="primary" @click="openWl()">添加</van-button>
        </div>
        <div v-for="w in watch" :key="w.id" class="wl-item">
          <div><b>{{ w.name }}</b> <span class="code">{{ w.code }} · {{ w.category }}</span></div>
          <div class="muted" style="font-size:12px">成本¥{{ w.cost }} · 目标¥{{ w.targetPrice }} · 持仓¥{{ fmt(w.amount) }}</div>
          <div class="ops">
            <van-icon name="edit" @click="openWl(w)" />
            <van-icon name="delete-o" @click="delWl(w.id)" />
          </div>
        </div>
        <van-empty v-if="!watch.length" description="还没有自选股" :image-size="60" />
      </div>
    </van-popup>

    <van-popup v-model:show="wlDlg" position="bottom" round :style="{ height: '80%' }">
      <div class="dlg">
        <van-nav-bar :title="wlForm.id ? '编辑股票' : '添加自选股'" left-text="取消" left-arrow @click-left="wlDlg = false" />
        <van-field v-model="wlForm.name" label="名称" placeholder="如:贵州茅台" />
        <van-field v-model="wlForm.code" label="代码" placeholder="如:600519" />
        <van-field v-model="wlForm.category" label="分类" placeholder="如:白酒 / 银行" />
        <van-field v-model.number="wlForm.cost" type="number" label="成本价" />
        <van-field v-model.number="wlForm.targetPrice" type="number" label="目标价" />
        <van-field v-model.number="wlForm.amount" type="number" label="持仓额" />
        <van-field v-model="wlForm.note" type="textarea" label="备注" rows="2" />
        <van-button type="primary" block :loading="wlSaving" @click="saveWl">保存</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { screenApi, watchlistApi, aiApi } from '../api'
import Sparkline from '../components/Sparkline.vue'
import AiDialog from '../components/AiDialog.vue'
import MobileMarkdown from '../components/MobileMarkdown.vue'

// 深度分析
const deepVisible = ref(false)
const deepLoading = ref(false)
const deepName = ref('')
const deepCode = ref('')
const deepTags = ref([])
const deepResult = ref('')
const deepMode = ref('')
const deepModel = ref('')
const deepLoadingMap = ref({})

function openDeep(s) {
  deepName.value = s.name; deepCode.value = s.code
  deepTags.value = s.moatTags || []; deepResult.value = ''
  deepMode.value = ''; deepModel.value = ''
  deepVisible.value = true
}
async function runDeep(invalidate) {
  if (!deepCode.value) return
  deepLoading.value = true
  deepLoadingMap.value[deepCode.value] = true
  try {
    const res = await screenApi.analyzeStock(deepCode.value, invalidate)
    deepResult.value = res.data.analysis || ''
    deepMode.value = res.data.mode || ''
    deepModel.value = res.data.model || ''
  } catch (e) {
    deepResult.value = '⚠️ 分析失败: ' + (e?.message || e)
  } finally {
    deepLoading.value = false
    deepLoadingMap.value[deepCode.value] = false
  }
}

const loading = ref(false)
const running = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const pipeline = ref([])
const scanned = ref(0)
const updatedAt = ref('')
const adviceMap = ref({})
const dataSource = ref('')

const showWatch = ref(false)
const watch = ref([])
const wlDlg = ref(false)
const wlSaving = ref(false)
const wlForm = reactive({ id: null, name: '', code: '', category: '', cost: null, targetPrice: null, amount: null, note: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function fmt(n) { return n == null ? '0' : Number(n).toLocaleString('zh-CN') }
function ratingClass(r) { return { '强烈推荐': 'tag-strong', '推荐': 'tag-rec', '观察': 'tag-watch', '回避': 'tag-avoid' }[r] || 'tag-watch' }
function retClass(rr) { if (!rr) return ''; const t = String(rr).trim(); if (t.startsWith('+')) return 'up'; if (t.startsWith('-')) return 'down'; return '' }

async function load() {
  loading.value = true
  try {
    const res = await screenApi.stock(page.value, pageSize)
    list.value = res.data.list
    total.value = res.data.total
    dataSource.value = list.value.length ? list.value[0].dataSource : ''
  } finally { loading.value = false }
  loadAdvice(false)
}
async function loadAdvice(invalidate) {
  try {
    const r = await screenApi.adviceStock(page.value, pageSize, invalidate)
    const m = {}
    ;(r.data || []).forEach((a) => { if (a.code) m[a.code] = a })
    adviceMap.value = m
  } catch (e) { /* ignore */ }
}
function onPage(p) { page.value = p; load() }
async function run() {
  running.value = true
  try {
    const res = await screenApi.runStock()
    pipeline.value = res.data.pipeline
    scanned.value = res.data.scanned
    updatedAt.value = res.data.updatedAt
    page.value = 1
    await load()
    await loadAdvice(true)
  } finally { running.value = false }
}

async function loadWatch() { const res = await watchlistApi.list('stock'); watch.value = res.data }
function openWl(row) {
  if (row) Object.assign(wlForm, { id: row.id, name: row.name, code: row.code, category: row.category, cost: row.cost, targetPrice: row.targetPrice, amount: row.amount, note: row.note })
  else Object.assign(wlForm, { id: null, name: '', code: '', category: '', cost: null, targetPrice: null, amount: null, note: '' })
  wlDlg.value = true
}
async function saveWl() {
  if (!wlForm.name.trim()) { showToast('请填写名称'); return }
  wlSaving.value = true
  try {
    const body = { type: 'stock', name: wlForm.name, code: wlForm.code, category: wlForm.category, cost: wlForm.cost || 0, amount: wlForm.amount || 0, targetPrice: wlForm.targetPrice || 0, note: wlForm.note }
    if (wlForm.id) await watchlistApi.update(wlForm.id, body); else await watchlistApi.add(body)
    showSuccessToast(wlForm.id ? '已更新' : '已添加')
    wlDlg.value = false
    await loadWatch()
  } finally { wlSaving.value = false }
}
async function delWl(id) { await showConfirmDialog({ title: '提示', message: '确认删除?' }); await watchlistApi.remove(id); showSuccessToast('已删除'); await loadWatch() }

onMounted(() => { load(); loadWatch() })
</script>

<style scoped>
.page { padding-bottom: 12px; }
.head { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px 0; }
.head-ops { display: flex; gap: 8px; }
.scan { text-align: center; color: #606266; font-size: 12px; margin: 6px 0; }
.tag-line { padding: 0 14px 6px; display: flex; gap: 8px; align-items: center; }
.center { text-align: center; padding: 40px 0; }
.scard { margin: 10px; }
.sc-top { display: flex; align-items: center; gap: 8px; }
.sc-top .nm { font-weight: 700; }
.sc-top .code { color: #909399; font-size: 12px; }
.sc-top > span:last-child { margin-left: auto; }
.sc-fin { display: flex; flex-wrap: wrap; gap: 4px 12px; font-size: 12px; color: #606266; margin: 6px 0; }
.sc-val { display: flex; flex-wrap: wrap; gap: 4px 12px; font-size: 12px; align-items: center; margin: 6px 0; }
.moat { display: flex; align-items: center; gap: 6px; font-size: 12px; margin: 6px 0; }
.ms { color: #e6a23c; font-weight: 700; }
.reason { font-size: 12px; color: #606266; line-height: 1.6; }
.adv { background: #f9fbfd; border-radius: 8px; padding: 8px; margin: 8px 0; font-size: 12px; }
.adv-row { display: flex; align-items: center; gap: 6px; }
.at { display: inline-block; padding: 0 6px; border-radius: 4px; font-size: 11px; color: #fff; }
.at.short { background: #67c23a; } .at.mid { background: #409eff; } .at.long { background: #909399; }
.pager { display: flex; justify-content: center; align-items: center; gap: 16px; margin: 12px 0; }
.dlg { height: 100%; overflow-y: auto; }
.wl-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; }
.wl-item { padding: 10px 16px; border-bottom: 1px dashed #ebeef5; }
.wl-item .code { color: #909399; font-size: 12px; }
.wl-item .ops { display: flex; gap: 12px; color: #2b6cb0; margin-top: 4px; }
/* 深度分析弹框 */
.deep-page { height: 100%; display: flex; flex-direction: column; background: #f5f7fa; }
.deep-body { flex: 1; overflow-y: auto; padding: 14px; }
.deep-foot { padding: 10px 14px; display: flex; gap: 10px; justify-content: center; border-top: 1px solid #ebeef5; background: #fff; }
</style>
