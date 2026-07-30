<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <div>
        <h2 class="page-title">AI 深度分析 · 大模型设置</h2>
        <p class="page-sub">全局 AI 中枢 · OpenAI 兼容 · 配置好的模型可在 财务/基金/股票/决策 各板块就地调用做细粒度分析</p>
      </div>
      <div class="head-actions">
        <el-button type="warning" plain :icon="Refresh" :loading="refreshing" @click="refreshCfg">刷新配置</el-button>
        <el-button type="primary" :icon="Plus" @click="openAdd">新增模型</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom:16px">
      首批优先接入 <b>DeepSeek</b> 与 <b>阿里百炼(qwen)</b>:编辑对应卡片填入 API Key → 点「测试连接」验证 → 设为默认即可全局生效。
      百炼 qwen 系列支持原生联网搜索(开启开关即可,无需额外搜索接口)。也兼容 Kimi / 智谱 / 混元 / OpenAI 等所有 OpenAI 兼容接口。
    </el-alert>

    <el-alert v-if="lastRefreshAt" type="success" :closable="true" style="margin-bottom:16px" @close="lastRefreshAt=''">
      ✓ 已从磁盘重新加载最新配置({{ lastRefreshAt }})，共 {{ lastRefreshCount }} 个模型，当前默认 {{ lastRefreshActive }}。若仍提示 Key 无效，请先在卡片编辑里填入新 Key 后再点「刷新配置」。
    </el-alert>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="8" v-for="c in configs" :key="c.id" style="margin-bottom:16px">
        <el-card class="model-card" shadow="hover" :class="{ active: c.active }">
          <div class="mc-head">
            <span class="mc-name">{{ c.name }}</span>
            <el-tag v-if="c.active" type="success" size="small" effect="dark">当前使用</el-tag>
          </div>
          <div class="mc-row"><span>模型</span><b>{{ c.model }}</b></div>
          <div class="mc-row"><span>接口</span><span class="mc-url">{{ c.baseUrl }}</span></div>
          <div class="mc-row"><span>API Key</span><span :class="c.apiKeyMasked==='(未配置)'?'down':''">{{ c.apiKeyMasked }}</span></div>
          <div class="mc-row"><span>联网搜索</span>
            <el-tag v-if="c.enableSearch" type="success" size="small">已开启(百炼原生)</el-tag>
            <span v-else style="color:#909399">未开启</span>
          </div>
          <div class="mc-actions">
            <el-button v-if="!c.active" size="small" type="primary" plain @click="setActive(c.id)">设为默认</el-button>
            <el-button size="small" type="success" plain :loading="testingId===c.id" @click="testConn(c)">测试连接</el-button>
            <el-button size="small" :icon="Edit" @click="openEdit(c)">编辑</el-button>
            <el-button size="small" type="danger" plain :icon="Delete" @click="remove(c.id)" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dlg" :title="editing ? '编辑模型' : '新增模型'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="如 DeepSeek" /></el-form-item>
        <el-form-item label="Base URL"><el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" /></el-form-item>
        <el-form-item label="模型名"><el-input v-model="form.model" placeholder="deepseek-chat" /></el-form-item>
        <el-form-item label="API Key"><el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." /></el-form-item>
        <el-form-item label="联网搜索">
          <el-switch v-model="form.enableSearch" />
          <span style="margin-left:10px;font-size:12px;color:#909399">仅阿里百炼 qwen 系列生效(原生 enable_search)</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, Edit, Delete, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { llmApi } from '../api'

const loading = ref(false)
const configs = ref([])
const dlg = ref(false)
const editing = ref(false)
const testingId = ref(null)
const refreshing = ref(false)
const lastRefreshAt = ref('')
const lastRefreshCount = ref(0)
const lastRefreshActive = ref('')
const form = ref({ name: '', baseUrl: '', model: '', apiKey: '', enableSearch: false })
let editId = null

async function load() {
  loading.value = true
  try { configs.value = (await llmApi.configs()).data } finally { loading.value = false }
}
function openAdd() { editing.value = false; form.value = { name: '', baseUrl: '', model: '', apiKey: '', enableSearch: false }; dlg.value = true }
function openEdit(c) { editing.value = true; editId = c.id; form.value = { name: c.name, baseUrl: c.baseUrl, model: c.model, apiKey: '', enableSearch: !!c.enableSearch }; dlg.value = true }

async function testConn(c) {
  testingId.value = c.id
  try {
    const res = await llmApi.test(c.id)
    ElMessage.success(`${c.name} 连接成功(${res.data.latencyMs}ms):${res.data.reply}`)
  } catch (e) { /* 拦截器已弹错误 */ } finally {
    testingId.value = null
  }
}
/** 从磁盘重新加载最新配置到内存;用于外部改文件/怀疑 PUT 写盘失败/云端 volume 同步 */
async function refreshCfg() {
  refreshing.value = true
  try {
    const res = await llmApi.refresh()
    lastRefreshAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
    lastRefreshCount.value = res.data.count
    const actId = res.data.activeId
    lastRefreshActive.value = (configs.value.find(x => x.id === actId) || {}).name || ('#' + actId)
    ElMessage.success(`已刷新:${res.data.count} 个模型,默认 ${lastRefreshActive.value}`)
    await load()
  } catch (e) { /* 拦截器已弹错误 */ } finally {
    refreshing.value = false
  }
}
async function save() {
  if (editing.value) await llmApi.update(editId, form.value)
  else await llmApi.add(form.value)
  ElMessage.success('已保存'); dlg.value = false; load()
}
async function setActive(id) { await llmApi.setActive(id); ElMessage.success('已切换默认模型'); load() }
async function remove(id) {
  await ElMessageBox.confirm('确认删除该模型配置?', '提示', { type: 'warning' })
  await llmApi.remove(id); ElMessage.success('已删除'); load()
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.head-actions { display: flex; gap: 8px; }
.model-card { border-radius: 12px; }
.model-card.active { border: 2px solid #67c23a; }
.mc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mc-name { font-weight: 700; font-size: 17px; }
.mc-row { display: flex; justify-content: space-between; font-size: 13px; color: #606266; padding: 5px 0; border-bottom: 1px dashed #f0f0f0; }
.mc-url { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mc-actions { margin-top: 12px; display: flex; gap: 6px; }
</style>
