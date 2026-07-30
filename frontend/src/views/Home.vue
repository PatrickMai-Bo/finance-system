<template>
  <div class="page" v-loading="loading">
    <div class="title-row">
      <div>
        <h2 class="page-title">总览看板</h2>
        <p class="page-sub">{{ data.marketNote }}</p>
      </div>
      <div class="online-chip" :class="onlineState">
        <el-icon><UserFilled /></el-icon>
        <span>在线 <b>{{ online }}</b> 人</span>
        <span v-if="warming" class="warm-tag">板块预热中…</span>
      </div>
    </div>

    <!-- 顶部核心指标 -->
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">净资产</div>
          <div class="stat-value up">¥{{ fmt(data.finance?.netWorth) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">月被动收入</div>
          <div class="stat-value">¥{{ fmt(data.finance?.passiveIncome) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">财务自由进度</div>
          <div class="stat-value" style="color:#2b6cb0">{{ data.finance?.freedomCoverage }}%</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">思维模型</div>
          <div class="stat-value">{{ data.mentalModelCount }} 个</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <!-- 板块二 基金 TOP -->
      <el-col :xs="24" :md="12">
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="card-head">
              <span><el-icon><Coin /></el-icon> 基金 TOP 推荐</span>
              <div class="card-tools">
                <el-tag v-if="fundWarmed" size="small" type="success" effect="plain" class="ready-tag">⚡ 板块已就绪</el-tag>
                <el-button link type="primary" @click="$router.push('/fund')">进入板块 →</el-button>
              </div>
            </div>
          </template>
          <div v-for="f in data.topFunds" :key="f.code" class="rank-row">
            <div class="rank-left">
              <el-tag size="small" round>{{ f.rank }}</el-tag>
              <span class="rank-name">{{ f.name }}</span>
              <span class="rank-code">{{ f.code }}</span>
            </div>
            <div class="rank-right">
              <span :class="ratingClass(f.rating)">{{ f.rating }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 板块三 股票 TOP -->
      <el-col :xs="24" :md="12">
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="card-head">
              <span><el-icon><Histogram /></el-icon> 股票 TOP 推荐</span>
              <div class="card-tools">
                <el-tag v-if="stockWarmed" size="small" type="success" effect="plain" class="ready-tag">⚡ 板块已就绪</el-tag>
                <el-button link type="primary" @click="$router.push('/stock')">进入板块 →</el-button>
              </div>
            </div>
          </template>
          <div v-for="s in data.topStocks" :key="s.code" class="rank-row">
            <div class="rank-left">
              <el-tag size="small" round>{{ s.rank }}</el-tag>
              <span class="rank-name">{{ s.name }}</span>
              <span class="rank-code">{{ s.code }}</span>
            </div>
            <div class="rank-right">
              <span class="mini">安全边际 <b :class="s.safetyMargin>=30?'up':''">{{ s.safetyMargin }}%</b></span>
              <span :class="ratingClass(s.rating)">{{ s.rating }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 板块入口 -->
    <el-row :gutter="16" style="margin-top: 4px">
      <el-col :xs="24" :sm="8" v-for="e in entries" :key="e.path">
        <el-card class="entry-card" shadow="hover" @click="$router.push(e.path)">
          <el-icon :size="26" :color="e.color"><component :is="e.icon" /></el-icon>
          <div class="entry-title">{{ e.title }}</div>
          <div class="entry-book">{{ e.book }}</div>
          <div class="entry-desc">{{ e.desc }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { Wallet, Compass, MagicStick, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { homeApi, systemApi } from '../api'

const loading = ref(false)
const data = ref({})

// 在线人数 + 板块预热状态
const online = ref(0)
const warming = ref(false)
const fundWarmed = ref(false)   // 首次成功拿到 fund 缓存视为已就绪
const stockWarmed = ref(false)  // 首次成功拿到 stock 缓存视为已就绪
let pingTimer = null
let warmPollTimer = null

const onlineState = ref('') // '' | 'hot'

const entries = [
  { path: '/finance', title: '个人财务系统', book: '资产 / 负债 · 现金流', desc: '资产负债表 · 现金流 · 财务自由', icon: markRaw(Wallet), color: '#e6a23c' },
  { path: '/decision', title: '决策思维系统', book: '五阶避错框架', desc: '逆向思维 · 安全边际 · 决策复盘', icon: markRaw(Compass), color: '#409eff' },
  { path: '/ai', title: 'AI 深度分析', book: '全局 AI 中枢', desc: '打通各板块 · 多模型可切换', icon: markRaw(MagicStick), color: '#9c27b0' }
]

function fmt(n) { return n == null ? '-' : Number(n).toLocaleString('zh-CN') }
function ratingClass(r) {
  return { '强烈推荐': 'rating-strong', '推荐': 'rating-rec', '观察': 'rating-watch', '回避': 'rating-avoid' }[r] || 'rating-watch'
}

/** 后台预热基金+股票深度分析(命中缓存就秒返,首次冷启动 ~60s) */
async function tryWarmup(triggeredByPing) {
  if (warming.value) return
  warming.value = true
  try {
    const res = await systemApi.warmup()
    const status = res?.data?.status
    if (triggeredByPing) {
      // 心跳触发的,无需打扰用户
      if (status === 'started' || status === 'running') {
        // 后台预热刚启动,启动轮询直到看见已就绪
        pollWarmed()
      } else if (status === 'throttled') {
        // 缓存可用,直接认为已就绪
        fundWarmed.value = stockWarmed.value = true
      }
    } else {
      // 首次进入触发,只在状态变化时给一个非打扰提示
      if (status === 'started') {
        ElMessage.info('正在后台预热基金/股票板块(首次 ~60s),点击板块时秒出')
        pollWarmed()
      } else if (status === 'throttled') {
        fundWarmed.value = stockWarmed.value = true
      }
    }
  } catch (e) {
    // 后台预热失败不影响首屏
  } finally {
    warming.value = false
  }
}

/** 轮询板块缓存是否就绪(精排) */
async function pollWarmed() {
  clearInterval(warmPollTimer)
  let n = 0
  warmPollTimer = setInterval(async () => {
    n++
    try {
      const [fs, ss] = await Promise.all([
        homeApi.overview().catch(() => null), // 维持心跳顺手刷数据
        // 真正确认 fund/stock 已就绪:用列表接口打通缓存链
        Promise.all([
          (await import('../api')).screenApi.fund('全部', 1, 1).catch(() => null),
          (await import('../api')).screenApi.stock(1, 1).catch(() => null)
        ])
      ])
      const [fRes, sRes] = ss
      const fList = fRes?.data?.list || []
      const sList = sRes?.data?.list || []
      if (fList.length && fList[0].deepAnalysis) fundWarmed.value = true
      if (sList.length && sList[0].deepAnalysis) stockWarmed.value = true
      if (fundWarmed.value && stockWarmed.value) {
        clearInterval(warmPollTimer)
      }
    } catch (e) {}
    if (n > 120) clearInterval(warmPollTimer) // 最多轮询 10 分钟
  }, 8000)
}

async function refreshOnline() {
  try {
    const res = await systemApi.online()
    online.value = res?.data?.online || 0
    onlineState.value = online.value > 1 ? 'hot' : ''
  } catch (e) {}
}

async function ping() {
  try {
    const res = await systemApi.ping()
    online.value = res?.data?.online || 0
    // 在线人数 > 0 时,如果还没预热过,触发一次
    if (online.value > 0 && !fundWarmed.value && !stockWarmed.value) {
      tryWarmup(true)
    }
  } catch (e) {}
}

onMounted(async () => {
  // 1. 先并行加载概览数据 + 在线人数
  loading.value = true
  try {
    const [ov] = await Promise.all([
      homeApi.overview().catch(() => null),
      refreshOnline()
    ])
    if (ov) data.value = ov.data
  } finally { loading.value = false }

  // 2. 在线人数 > 0 → 后台启动板块预热(把精排分析结果写进缓存)
  if (online.value > 0) tryWarmup(false)

  // 3. 启动定时心跳(30s):同时让「在线人数」面板实时更新
  pingTimer = setInterval(ping, 30000)
})

onBeforeUnmount(() => {
  clearInterval(pingTimer); pingTimer = null
  clearInterval(warmPollTimer); warmPollTimer = null
})
</script>

<style scoped>
.title-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.online-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 12px; border-radius: 16px; font-size: 12px;
  background: #f4f4f5; color: #606266; border: 1px solid transparent;
}
.online-chip.hot { background: #f0f9eb; color: #67c23a; border-color: #e1f3d8; }
.online-chip b { font-size: 14px; font-weight: 700; margin: 0 2px; }
.warm-tag { color: #e6a23c; margin-left: 4px; }
.card-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.card-tools { display: flex; align-items: center; gap: 10px; }
.ready-tag { font-weight: 600; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
.rank-row { display: flex; justify-content: space-between; align-items: center; padding: 9px 0; border-bottom: 1px dashed #ebeef5; }
.rank-row:last-child { border-bottom: none; }
.rank-left { display: flex; align-items: center; gap: 8px; }
.rank-name { font-weight: 600; }
.rank-code { color: #909399; font-size: 12px; }
.rank-right { display: flex; align-items: center; gap: 10px; }
.mini { font-size: 12px; color: #606266; }
.entry-card { cursor: pointer; text-align: center; padding: 8px; border-radius: 12px; transition: transform 0.15s; }
.entry-card:hover { transform: translateY(-3px); }
.entry-title { font-weight: 700; margin-top: 8px; }
.entry-book { font-size: 12px; color: #2b6cb0; margin: 2px 0 6px; }
.entry-desc { font-size: 12px; color: #909399; }
</style>
