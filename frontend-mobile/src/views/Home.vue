<template>
  <div class="page">
    <div v-if="loading" class="center"><van-loading /></div>
    <template v-else>
      <p class="m-sub" style="padding:0 14px">{{ data.marketNote }}</p>

      <!-- 核心指标 -->
      <div class="grid">
        <div class="m-card stat">
          <div class="lbl">净资产</div>
          <div class="val up">¥{{ fmt(data.finance?.netWorth) }}</div>
        </div>
        <div class="m-card stat">
          <div class="lbl">月被动收入</div>
          <div class="val">¥{{ fmt(data.finance?.passiveIncome) }}</div>
        </div>
        <div class="m-card stat">
          <div class="lbl">财务自由进度</div>
          <div class="val" style="color:#2b6cb0">{{ data.finance?.freedomCoverage }}%</div>
        </div>
        <div class="m-card stat">
          <div class="lbl">思维模型</div>
          <div class="val">{{ data.mentalModelCount }} 个</div>
        </div>
      </div>

      <!-- 基金 TOP -->
      <div class="m-card">
        <div class="m-title">基金 TOP 推荐 <van-icon name="arrow" @click="go('/fund')" /></div>
        <div v-for="f in data.topFunds" :key="f.code" class="row">
          <span class="rank">{{ f.rank }}</span>
          <span class="nm">{{ f.name }}</span>
          <span class="code">{{ f.code }}</span>
          <span :class="ratingClass(f.rating)">{{ f.rating }}</span>
        </div>
        <van-empty v-if="!data.topFunds?.length" description="暂无数据" :image-size="60" />
      </div>

      <!-- 股票 TOP -->
      <div class="m-card">
        <div class="m-title">股票 TOP 推荐 <van-icon name="arrow" @click="go('/stock')" /></div>
        <div v-for="s in data.topStocks" :key="s.code" class="row">
          <span class="rank">{{ s.rank }}</span>
          <span class="nm">{{ s.name }}</span>
          <span class="code">{{ s.code }}</span>
          <span class="mini">安全边际 <b :class="s.safetyMargin >= 30 ? 'up' : ''">{{ s.safetyMargin }}%</b></span>
          <span :class="ratingClass(s.rating)">{{ s.rating }}</span>
        </div>
        <van-empty v-if="!data.topStocks?.length" description="暂无数据" :image-size="60" />
      </div>

      <!-- 模块入口 -->
      <div class="m-card">
        <div class="m-title">功能模块</div>
        <van-grid :column-num="3" :border="false">
          <van-grid-item icon="balance-o" text="个人财务" @click="go('/finance')" />
          <van-grid-item icon="completed" text="决策思维" @click="go('/decision')" />
          <van-grid-item icon="smile-o" text="AI 设置" @click="go('/ai')" />
        </van-grid>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { homeApi } from '../api'

const router = useRouter()
const loading = ref(true)
const data = ref({ finance: {}, topFunds: [], topStocks: [] })

function fmt(n) { return n == null ? '-' : Number(n).toLocaleString('zh-CN') }
function ratingClass(r) {
  return { '强烈推荐': 'tag-strong', '推荐': 'tag-rec', '观察': 'tag-watch', '回避': 'tag-avoid' }[r] || 'tag-watch'
}
function go(p) { router.push(p) }

onMounted(async () => {
  try {
    const res = await homeApi.overview()
    data.value = res.data
  } finally { loading.value = false }
})
</script>

<style scoped>
.page { padding-bottom: 12px; }
.center { text-align: center; padding: 60px 0; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 0 10px; }
.stat { margin: 0; }
.lbl { color: #909399; font-size: 12px; }
.val { font-size: 20px; font-weight: 700; margin-top: 4px; }
.row { display: flex; align-items: center; gap: 8px; padding: 9px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
.row:last-child { border-bottom: none; }
.rank { background: #2b6cb0; color: #fff; border-radius: 50%; width: 20px; height: 20px; text-align: center; line-height: 20px; font-size: 11px; flex-shrink: 0; }
.nm { font-weight: 600; flex: 1; }
.code { color: #909399; font-size: 11px; }
.mini { font-size: 11px; color: #606266; }
.m-title :deep(.van-icon) { float: right; color: #2b6cb0; }
</style>
