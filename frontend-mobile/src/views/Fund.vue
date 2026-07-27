<template>
  <div class="page">
    <div class="head">
      <div class="m-title">基金筛选</div>
      <van-button size="small" type="primary" :loading="running" @click="run">刷新行情</van-button>
    </div>

    <van-tabs v-model:active="catIndex" @change="onCategory" line-width="20">
      <van-tab v-for="c in categories" :key="c" :title="c" />
    </van-tabs>

    <div v-if="pipeline.length" class="scan">
      已扫描 <b>{{ scanned }}</b> · 通过 <b class="up">{{ total }}</b> · {{ updatedAt }}
    </div>

    <div class="tag-line" v-if="dataSource">
      <van-tag v-if="dataSource === 'real'" type="success">真实数据</van-tag>
      <van-tag v-else type="default">演示数据</van-tag>
      <span class="muted">第 {{ page }}/{{ totalPages }} 页</span>
    </div>

    <van-loading v-if="loading" class="center" />
    <div v-for="f in list" :key="f.code" class="m-card scard">
      <div class="sc-top">
        <b class="nm">{{ f.name }}</b>
        <span class="code">{{ f.code }} · {{ f.category }}</span>
        <span :class="ratingClass(f.rating)">{{ f.rating }}</span>
      </div>
      <div class="sc-fin">
        <span v-if="f.peQuantile > 0">PE分位 <b :class="f.peQuantile < 30 ? 'up' : ''">{{ f.peQuantile }}%</b></span>
        <span>ROE {{ f.roe }}%</span>
        <span>毛利 {{ f.grossMargin }}%</span>
        <span>负债 {{ f.debtRatio }}%</span>
      </div>
      <div class="sc-val">
        <span>内在值 <b>¥{{ f.intrinsicValue }}</b></span>
        <span>现价 ¥{{ f.price }}</span>
        <span>安全边际 <b :class="f.safetyMargin >= 30 ? 'up' : 'down'">{{ f.safetyMargin }}%</b></span>
      </div>
      <div class="moat">
        <van-rate :model-value="f.moatScore / 20" disabled size="12" /> <span class="ms">{{ f.moatScore }} 分</span>
        <van-tag v-for="t in f.moatTags" :key="t" size="mini" type="warning" plain>{{ t }}</van-tag>
      </div>
      <div class="reason">{{ f.reason }}</div>
      <div v-if="adviceMap[f.code]" class="adv">
        <div class="adv-row"><span class="at short">短期</span> {{ adviceMap[f.code].short?.horizon }} · 预计 <b :class="retClass(adviceMap[f.code].short?.returnRange)">{{ adviceMap[f.code].short?.returnRange }}</b></div>
        <div class="adv-row"><span class="at mid">中期</span> {{ adviceMap[f.code].mid?.horizon }} · 预计 <b :class="retClass(adviceMap[f.code].mid?.returnRange)">{{ adviceMap[f.code].mid?.returnRange }}</b></div>
        <div class="adv-row"><span class="at long">长期</span> {{ adviceMap[f.code].long?.horizon }} · 预计 <b :class="retClass(adviceMap[f.code].long?.returnRange)">{{ adviceMap[f.code].long?.returnRange }}</b></div>
      </div>
      <AiDialog scene="fund" :payload="f" label="AI 分析" />
    </div>

    <div class="pager">
      <van-button size="small" :disabled="page <= 1" @click="onPage(page - 1)">上一页</van-button>
      <span class="muted">{{ page }}/{{ totalPages }}</span>
      <van-button size="small" :disabled="page >= totalPages" @click="onPage(page + 1)">下一页</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { screenApi } from '../api'
import Sparkline from '../components/Sparkline.vue'
import AiDialog from '../components/AiDialog.vue'

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
const categories = ref(['全部'])
const catIndex = ref(0)
const category = computed(() => categories.value[catIndex.value] || '全部')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function ratingClass(r) { return { '强烈推荐': 'tag-strong', '推荐': 'tag-rec', '观察': 'tag-watch', '回避': 'tag-avoid' }[r] || 'tag-watch' }
function retClass(rr) { if (!rr) return ''; const t = String(rr).trim(); if (t.startsWith('+')) return 'up'; if (t.startsWith('-')) return 'down'; return '' }

async function load() {
  loading.value = true
  try {
    const res = await screenApi.fund(category.value, page.value, pageSize)
    list.value = res.data.list
    total.value = res.data.total
    dataSource.value = list.value.length ? list.value[0].dataSource : ''
  } finally { loading.value = false }
  loadAdvice(false)
}
async function loadAdvice(invalidate) {
  try {
    const r = await screenApi.adviceFund(category.value, page.value, pageSize, invalidate)
    const m = {}
    ;(r.data || []).forEach((a) => { if (a.code) m[a.code] = a })
    adviceMap.value = m
  } catch (e) { /* ignore */ }
}
function onPage(p) { page.value = p; load() }
function onCategory() { page.value = 1; load() }
async function run() {
  running.value = true
  try {
    const res = await screenApi.runFund()
    pipeline.value = res.data.pipeline
    scanned.value = res.data.scanned
    updatedAt.value = res.data.updatedAt
    page.value = 1
    await load()
    await loadAdvice(true)
  } finally { running.value = false }
}
async function loadCats() {
  try { const r = await screenApi.categories(); categories.value = r.data || ['全部'] } catch (e) { /* default */ }
}

onMounted(() => { loadCats(); load() })
</script>

<style scoped>
.page { padding-bottom: 12px; }
.head { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px 0; }
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
</style>
