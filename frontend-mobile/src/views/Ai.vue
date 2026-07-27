<template>
  <div class="page">
    <van-loading v-if="loading" class="center" />
    <van-notice-bar text="首批优先接入 DeepSeek 与阿里百炼(qwen):填 Key → 测试连接 → 设为默认即可全局生效。也兼容 Kimi/智谱/混元/OpenAI 等 OpenAI 兼容接口。" wrapable />

    <div v-for="c in configs" :key="c.id" class="m-card mc" :class="{ active: c.active }">
      <div class="mc-h">
        <b>{{ c.name }}</b>
        <van-tag v-if="c.active" type="success">当前使用</van-tag>
      </div>
      <div class="mc-row"><span>模型</span><b>{{ c.model }}</b></div>
      <div class="mc-row"><span>接口</span><span class="url">{{ c.baseUrl }}</span></div>
      <div class="mc-row"><span>Key</span><b :class="c.apiKeyMasked === '(未配置)' ? 'down' : ''">{{ c.apiKeyMasked }}</b></div>
      <div class="mc-row"><span>联网搜索</span><van-tag v-if="c.enableSearch" type="success" size="mini">已开启(百炼)</van-tag><span v-else class="muted">未开启</span></div>
      <div class="mc-ops">
        <van-button v-if="!c.active" size="mini" type="primary" plain @click="setActive(c.id)">设为默认</van-button>
        <van-button size="mini" type="success" plain :loading="testingId === c.id" @click="test(c)">测试连接</van-button>
        <van-button size="mini" @click="openEdit(c)">编辑</van-button>
        <van-button size="mini" type="danger" plain @click="remove(c.id)">删除</van-button>
      </div>
    </div>

    <van-button type="primary" block round style="margin:14px" @click="openAdd">新增模型</van-button>

    <van-popup v-model:show="dlg" position="bottom" round :style="{ height: '85%' }">
      <div class="dlg">
        <van-nav-bar :title="editing ? '编辑模型' : '新增模型'" left-text="取消" left-arrow @click-left="dlg = false" />
        <van-field v-model="form.name" label="名称" placeholder="如 DeepSeek" />
        <van-field v-model="form.baseUrl" label="Base URL" placeholder="https://api.deepseek.com" />
        <van-field v-model="form.model" label="模型名" placeholder="deepseek-chat" />
        <van-field v-model="form.apiKey" type="password" label="API Key" placeholder="sk-..." />
        <van-cell title="联网搜索(仅百炼)" center>
          <template #value><van-switch v-model="form.enableSearch" /></template>
        </van-cell>
        <van-button type="primary" block :loading="saving" @click="save">保存</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { llmApi } from '../api'

const loading = ref(false)
const configs = ref([])
const dlg = ref(false)
const editing = ref(false)
const saving = ref(false)
const testingId = ref(null)
const form = reactive({ name: '', baseUrl: '', model: '', apiKey: '', enableSearch: false })
let editId = null

async function load() { loading.value = true; try { configs.value = (await llmApi.configs()).data } finally { loading.value = false } }
function openAdd() { editing.value = false; Object.assign(form, { name: '', baseUrl: '', model: '', apiKey: '', enableSearch: false }); dlg.value = true }
function openEdit(c) { editing.value = true; editId = c.id; Object.assign(form, { name: c.name, baseUrl: c.baseUrl, model: c.model, apiKey: '', enableSearch: !!c.enableSearch }); dlg.value = true }

async function test(c) {
  testingId.value = c.id
  try { const res = await llmApi.test(c.id); showSuccessToast(`${c.name} 连接成功(${res.data.latencyMs}ms):${res.data.reply}`) } catch (e) { /* toast */ } finally { testingId.value = null }
}
async function save() {
  saving.value = true
  try {
    if (editing.value) await llmApi.update(editId, form); else await llmApi.add(form)
    showSuccessToast('已保存'); dlg.value = false; await load()
  } finally { saving.value = false }
}
async function setActive(id) { await llmApi.setActive(id); showSuccessToast('已切换默认模型'); await load() }
async function remove(id) { await showConfirmDialog({ title: '提示', message: '确认删除该模型配置?' }); await llmApi.remove(id); showSuccessToast('已删除'); await load() }

onMounted(load)
</script>

<style scoped>
.page { padding-bottom: 12px; }
.center { text-align: center; padding: 60px 0; }
.mc { border: 1px solid #ebeef5; }
.mc.active { border: 2px solid #67c23a; }
.mc-h { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.mc-row { display: flex; justify-content: space-between; font-size: 13px; color: #606266; padding: 5px 0; border-bottom: 1px dashed #f0f0f0; }
.mc-row .url { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mc-ops { margin-top: 10px; display: flex; gap: 6px; flex-wrap: wrap; }
.dlg { height: 100%; overflow-y: auto; }
</style>
