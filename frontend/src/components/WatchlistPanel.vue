<template>
  <el-card class="section-card watchlist-card" shadow="hover">
    <template #header>
      <div class="card-head">
        <div>
          <b>{{ title }} · 增删改查</b>
          <span class="head-sub">自行维护你关注/持有的{{ typeLabel }},可整体交给 AI 做组合点评</span>
        </div>
        <div class="head-ops">
          <AiAnalyze scene="watchlist" :payload="aiPayload" label="AI 组合点评" />
          <el-button type="primary" :icon="Plus" @click="openDlg()">添加{{ typeLabel }}</el-button>
        </div>
      </div>
    </template>

    <div class="wl-stat">
      <span>共 <b>{{ list.length }}</b> 只 · 合计持仓 <b class="up">¥{{ fmt(totalAmount) }}</b></span>
    </div>

    <el-table :data="list" size="default" stripe max-height="360">
      <el-table-column label="名称 / 代码" min-width="150">
        <template #default="{ row }">
          <div class="name">{{ row.name }}</div>
          <div class="code">{{ row.code }} · <el-tag size="small" effect="plain">{{ row.category }}</el-tag></div>
        </template>
      </el-table-column>
      <el-table-column label="成本价" width="100" align="right">
        <template #default="{ row }">¥{{ row.cost }}</template>
      </el-table-column>
      <el-table-column label="持仓金额" width="120" align="right">
        <template #default="{ row }"><b>¥{{ fmt(row.amount) }}</b></template>
      </el-table-column>
      <el-table-column label="目标价" width="100" align="right">
        <template #default="{ row }"><b class="up">¥{{ row.targetPrice }}</b></template>
      </el-table-column>
      <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDlg(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="del(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!list.length" :description="'还没有自选' + typeLabel + ',点右上角添加'" :image-size="70" />

    <el-dialog v-model="dlg" :title="form.id ? ('编辑' + typeLabel) : ('添加' + typeLabel)" width="480px" append-to-body>
      <el-form :model="form" label-width="88px">
        <el-row :gutter="10">
          <el-col :span="14"><el-form-item label="名称"><el-input v-model="form.name" :placeholder="'如:' + (type==='stock'?'贵州茅台':'沪深300ETF')" /></el-form-item></el-col>
          <el-col :span="10"><el-form-item label="代码"><el-input v-model="form.code" placeholder="如:600519" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="分类">
          <el-input v-model="form.category" :placeholder="type==='stock'?'如:白酒 / 银行 / 家电':'如:指数基金 / 债券型 / QDII'" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="8"><el-form-item label="成本价"><el-input v-model.number="form.cost" type="number" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="目标价"><el-input v-model.number="form.targetPrice" type="number" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="持仓额"><el-input v-model.number="form.amount" type="number" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="如:护城河强,回调分批 / 收息为主" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AiAnalyze from './AiAnalyze.vue'
import { watchlistApi } from '../api'

const props = defineProps({
  type: { type: String, required: true },  // stock | fund
  title: { type: String, default: '我的自选' }
})

const typeLabel = computed(() => (props.type === 'stock' ? '股票' : '基金'))
const list = ref([])
const dlg = ref(false)
const saving = ref(false)
const form = reactive({ id: null, name: '', code: '', category: '', cost: null, amount: null, targetPrice: null, note: '' })

const totalAmount = computed(() => list.value.reduce((s, r) => s + (Number(r.amount) || 0), 0))
const aiPayload = computed(() => ({
  name: props.title + '(' + typeLabel.value + ')',
  count: list.value.length,
  totalAmount: totalAmount.value,
  items: list.value.map(r => ({ 名称: r.name, 代码: r.code, 分类: r.category, 成本价: r.cost, 持仓金额: r.amount, 目标价: r.targetPrice, 备注: r.note }))
}))

function fmt(n) { return n == null ? '0' : Number(n).toLocaleString('zh-CN') }

function openDlg(row) {
  if (row) {
    Object.assign(form, { id: row.id, name: row.name, code: row.code, category: row.category, cost: row.cost, amount: row.amount, targetPrice: row.targetPrice, note: row.note })
  } else {
    Object.assign(form, { id: null, name: '', code: '', category: '', cost: null, amount: null, targetPrice: null, note: '' })
  }
  dlg.value = true
}

async function save() {
  if (!form.name.trim()) { ElMessage.warning('请填写名称'); return }
  saving.value = true
  try {
    const body = { type: props.type, name: form.name, code: form.code, category: form.category, cost: form.cost || 0, amount: form.amount || 0, targetPrice: form.targetPrice || 0, note: form.note }
    if (form.id) await watchlistApi.update(form.id, body)
    else await watchlistApi.add(body)
    ElMessage.success(form.id ? '已更新' : '已添加')
    dlg.value = false
    await load()
  } finally { saving.value = false }
}

async function del(id) {
  await ElMessageBox.confirm('确认从自选中删除?', '提示', { type: 'warning' })
  await watchlistApi.remove(id)
  ElMessage.success('已删除')
  await load()
}

async function load() {
  const res = await watchlistApi.list(props.type)
  list.value = res.data
}

onMounted(load)
</script>

<style scoped>
.watchlist-card { margin-bottom: 14px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.head-sub { font-size: 12px; color: #909399; margin-left: 10px; font-weight: normal; }
.head-ops { display: flex; gap: 10px; align-items: center; }
.wl-stat { font-size: 13px; color: #606266; margin-bottom: 10px; }
.name { font-weight: 700; }
.code { color: #909399; font-size: 12px; }
</style>
