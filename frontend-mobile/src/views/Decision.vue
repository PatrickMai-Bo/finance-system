<template>
  <div class="page">
    <van-loading v-if="loading" class="center" />
    <template v-if="fw">
      <div class="m-card">
        <div class="motto">宗旨 · {{ fw.motto }}</div>
        <div class="pillars">
          <div v-for="p in fw.pillars" :key="p.name" class="pillar">
            <div class="pn">{{ p.name }}</div>
            <div class="pp">{{ p.point }}</div>
            <van-tag size="mini" plain type="info">{{ p.book }}</van-tag>
          </div>
        </div>
      </div>

      <div class="m-card">
        <div class="m-title">AI 五阶决策分析</div>
        <van-field v-model="scene" label="场景" readonly @click="showScene = true" :placeholder="sceneName" />
        <van-popup v-model:show="showScene" position="bottom" round>
          <van-picker :columns="sceneCols" @confirm="onScene" @cancel="showScene = false" show-toolbar title="选择场景" />
        </van-popup>
        <van-field v-model="decision" type="textarea" rows="2" label="决策" :placeholder="scenePlaceholder" />
        <van-button type="primary" block :loading="aiLoading" @click="start">开始排查</van-button>
        <div v-if="aiResult" class="ai-res">
          <MobileMarkdown :source="aiResult" />
        </div>
        <van-empty v-else-if="!aiLoading" description="输入决策,让 AI 按五阶框架分析" :image-size="70" />
      </div>

      <div class="m-card">
        <div class="m-title">决策检查清单</div>
        <div v-for="(c, i) in fw.checklist" :key="i" class="check">✅ {{ c }}</div>
      </div>

      <div class="m-card">
        <div class="m-title">3 秒速查版</div>
        <div v-for="(c, i) in fw.quickCheck" :key="i" class="quick">{{ c }}</div>
      </div>

      <div class="m-card">
        <div class="m-title">思维模型库</div>
        <div v-for="m in models" :key="m.name" class="model">
          <div class="mh"><b>{{ m.name }}</b></div>
          <p class="md">{{ m.desc }}</p>
          <div class="mu">💡 {{ m.usage }}</div>
        </div>
      </div>

      <div class="m-card">
        <div class="m-title">历史决策记录</div>
        <div v-if="logs.length === 0" style="color:#909399;font-size:12px;text-align:center;padding:12px">暂无记录，完成一次决策分析后自动记录</div>
        <div v-for="l in logs" :key="l.title" class="log-item">
          <div class="log-top">
            <span class="log-dec">{{ l.title }}</span>
          </div>
          <div class="log-verdict">{{ l.basis }} · {{ l.review }}</div>
          <div style="font-size:11px;color:#909399;margin-top:4px">{{ l.date }} · 模型: {{ l.model }}</div>
        </div>
      </div>

      <div class="m-card">
        <div class="m-title">每周错误复盘模板</div>
        <ol class="review"><li v-for="(r, i) in fw.reviewTemplate" :key="i">{{ r }}</li></ol>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { decisionApi, aiApi } from '../api'
import MobileMarkdown from '../components/MobileMarkdown.vue'

const loading = ref(true)
const fw = ref(null)
const models = ref([])
const logs = ref([])
const scene = ref('invest')
const decision = ref('')
const showScene = ref(false)
const aiLoading = ref(false)
const aiResult = ref('')

const sceneName = computed(() => fw.value?.scenes?.find((s) => s.key === scene.value)?.name || '')
const scenePlaceholder = computed(() => {
  const s = fw.value?.scenes?.find((x) => x.key === scene.value)
  return s ? '例如:' + s.example : '输入你的决策问题'
})
const sceneCols = computed(() => (fw.value?.scenes || []).map((s) => ({ text: s.name, value: s.key })))

function onScene({ selectedValues }) { scene.value = selectedValues[0]; showScene.value = false }

async function start() {
  if (!decision.value.trim()) { showToast('请先输入决策问题'); return }
  aiLoading.value = true
  aiResult.value = ''
  try {
    const res = await aiApi.analyze('decision', { decision: decision.value, scene: sceneName.value })
    aiResult.value = res.data.analysis
  } catch (e) {
    aiResult.value = '⚠️ AI 分析失败:' + (e?.message || e)
  } finally { aiLoading.value = false }
}

onMounted(async () => {
  try {
    const [m, l, f] = await Promise.all([decisionApi.models(), decisionApi.logs(), decisionApi.framework()])
    models.value = m.data; logs.value = l.data; fw.value = f.data
  } catch (e) {
    // 部分失败也要加载能用的
    try { const r = await decisionApi.framework(); fw.value = r.data } catch (_) {}
    try { const r = await decisionApi.models(); models.value = r.data } catch (_) {}
    try { const r = await decisionApi.logs(); logs.value = r.data } catch (_) {}
  } finally { loading.value = false }
})
</script>

<style scoped>
.page { padding-bottom: 12px; }
.center { text-align: center; padding: 60px 0; }
.motto { font-size: 16px; font-weight: 800; color: #2b4a8b; margin-bottom: 10px; }
.pillars { display: flex; flex-direction: column; gap: 8px; }
.pillar { background: #f6f8fa; border-radius: 10px; padding: 10px 12px; }
.pn { font-weight: 700; font-size: 14px; }
.pp { font-size: 12px; color: #606266; line-height: 1.6; margin: 4px 0; }
.check { font-size: 13px; color: #303133; padding: 4px 0; }
.quick { font-size: 13px; color: #7a4a00; padding: 6px 0; border-bottom: 1px dashed #f0d9b5; }
.quick:last-child { border-bottom: none; }
.model { border: 1px solid #ebeef5; border-radius: 10px; padding: 10px 12px; margin-bottom: 8px; }
.mh { display: flex; justify-content: space-between; align-items: center; }
.md { font-size: 12px; color: #606266; line-height: 1.6; margin: 6px 0; }
.mu { font-size: 12px; color: #2b6cb0; background: #f0f6ff; padding: 6px; border-radius: 8px; }
.review { padding-left: 18px; color: #606266; font-size: 13px; line-height: 2; }
.ai-res { margin-top: 12px; }
.log-item { background: #fafbfc; border-radius: 8px; padding: 10px 12px; margin-bottom: 8px; }
.log-top { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.log-dec { font-size: 13px; font-weight: 600; color: #303133; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.log-verdict { font-size: 12px; color: #e67e22; background: #fef5e7; padding: 4px 8px; border-radius: 6px; line-height: 1.6; }
</style>
