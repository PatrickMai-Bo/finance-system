<template>
  <div class="page" v-loading="loading">
    <h2 class="page-title">总览看板</h2>
    <p class="page-sub">{{ data.marketNote }}</p>

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
              <el-button link type="primary" @click="$router.push('/fund')">进入板块 →</el-button>
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
              <el-button link type="primary" @click="$router.push('/stock')">进入板块 →</el-button>
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
import { ref, onMounted, markRaw } from 'vue'
import { Wallet, Compass, MagicStick } from '@element-plus/icons-vue'
import { homeApi } from '../api'

const loading = ref(false)
const data = ref({})

const entries = [
  { path: '/finance', title: '个人财务系统', book: '资产 / 负债 · 现金流', desc: '资产负债表 · 现金流 · 财务自由', icon: markRaw(Wallet), color: '#e6a23c' },
  { path: '/decision', title: '决策思维系统', book: '五阶避错框架', desc: '逆向思维 · 安全边际 · 决策复盘', icon: markRaw(Compass), color: '#409eff' },
  { path: '/ai', title: 'AI 深度分析', book: '全局 AI 中枢', desc: '打通各板块 · 多模型可切换', icon: markRaw(MagicStick), color: '#9c27b0' }
]

function fmt(n) { return n == null ? '-' : Number(n).toLocaleString('zh-CN') }
function ratingClass(r) {
  return { '强烈推荐': 'rating-strong', '推荐': 'rating-rec', '观察': 'rating-watch', '回避': 'rating-avoid' }[r] || 'rating-watch'
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await homeApi.overview()
    data.value = res.data
  } finally { loading.value = false }
})
</script>

<style scoped>
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
.card-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
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
