<template>
  <div class="page">
    <div class="head">
      <div>
        <div class="m-title">个人财务系统</div>
        <div class="m-sub">随手记 · 资产负债 · 现金流</div>
      </div>
      <AiDialog scene="finance" :payload="finPayload" label="AI 概况" />
    </div>

    <!-- 关键指标 -->
    <div class="grid">
      <div class="m-card s"><div class="l">本期收入</div><div class="v up">¥{{ fmt(sum.totalIncome) }}</div><div class="f">被动 <b class="up">¥{{ fmt(sum.passiveIncome) }}</b></div></div>
      <div class="m-card s"><div class="l">本期支出</div><div class="v down">¥{{ fmt(sum.totalExpense) }}</div><div class="f">资产型 {{ sum.assetRatio }}%</div></div>
      <div class="m-card s"><div class="l">净现金流</div><div class="v" :class="sum.netCashflow >= 0 ? 'up' : 'down'">¥{{ fmt(sum.netCashflow) }}</div><div class="f">结余转化资产</div></div>
      <div class="m-card s"><div class="l">自由覆盖率</div><div class="v" :style="{ color: covColor(sum.coverage) }">{{ sum.coverage }}%</div><div class="f">被动/总支出</div></div>
    </div>

    <van-notice-bar v-if="sum.diagnosis" :text="'富爸爸诊断: ' + sum.diagnosis" color="#e6a23c" background="#fdf6ec" wrapable />

    <!-- 家底(资产负债) -->
    <div class="m-card">
      <div class="m-title">
        我的家底
        <van-button size="mini" type="primary" @click="openHold()">添加</van-button>
        <AiDialog scene="holding" :payload="holdPayload" label="AI 诊断" />
      </div>
      <div class="grid2">
        <div class="mini"><span>总资产</span><b class="up">¥{{ fmt(hsum.totalAsset) }}</b></div>
        <div class="mini"><span>总负债</span><b class="down">¥{{ fmt(hsum.totalLiability) }}</b></div>
        <div class="mini"><span>净资产</span><b :class="hsum.netWorth >= 0 ? 'up' : 'down'">¥{{ fmt(hsum.netWorth) }}</b></div>
        <div class="mini"><span>真资产占比</span><b :style="{ color: covColor(hsum.realAssetRatio) }">{{ hsum.realAssetRatio }}%</b></div>
      </div>
      <van-notice-bar v-if="hsum.diagnosis" :text="hsum.diagnosis" color="#409eff" background="#ecf5ff" wrapable />
      <div v-for="h in holdings" :key="h.id" class="item">
        <div class="it-top">
          <van-tag :type="h.bigType === '资产' ? 'success' : 'danger'" plain>{{ h.bigType }}</van-tag>
          <b>{{ h.name }}</b>
          <span class="code">{{ fmt(h.amount) }}</span>
        </div>
        <div class="it-bot">
          <span>月现金流 <b :class="h.monthlyCashflow >= 0 ? 'up' : 'down'">{{ h.monthlyCashflow >= 0 ? '+' : '' }}{{ fmt(h.monthlyCashflow) }}</b></span>
          <van-tag size="medium" :type="verdictColor(h.verdict)">{{ h.verdict }}</van-tag>
          <span class="ops">
            <van-icon name="edit" @click="openHold(h)" />
            <van-icon name="delete-o" @click="delHold(h.id)" />
          </span>
        </div>
      </div>
      <van-empty v-if="!holdings.length" description="还没有家底,点添加" :image-size="60" />
    </div>

    <!-- 记一笔 -->
    <div class="m-card">
      <div class="m-title">记一笔</div>
      <van-radio-group v-model="form.type" direction="horizontal">
        <van-radio name="expense">支出</van-radio>
        <van-radio name="income">收入</van-radio>
      </van-radio-group>
      <van-field v-model.number="form.amount" type="number" label="金额" placeholder="0.00">
        <template #left-icon><span>¥</span></template>
      </van-field>
      <van-field v-model="form.desc" label="描述" placeholder="如:报Python课 / 买名牌包" />
      <van-field v-model="form.date" label="日期" readonly @click="showDate = true" :placeholder="form.date" />
      <van-popup v-model:show="showDate" position="bottom"><van-date-picker v-model="datePick" title="选择日期" @confirm="onDate" @cancel="showDate = false" :min-date="minDate" :max-date="maxDate" /></van-popup>
      <van-button type="primary" block :loading="saving" @click="saveLedger">记下这一笔</van-button>
    </div>

    <!-- 流水 -->
    <div class="m-card">
      <div class="m-title">记账流水</div>
      <div v-for="r in records" :key="r.id" class="ledger">
        <div class="ld-top">
          <van-tag :type="r.type === 'income' ? 'success' : 'info'" plain>{{ r.type === 'income' ? '收入' : '支出' }}</van-tag>
          <span class="desc">{{ r.desc }}</span>
          <b :class="r.type === 'income' ? 'up' : 'down'">{{ r.type === 'income' ? '+' : '-' }}{{ fmt(r.amount) }}</b>
        </div>
        <div class="ld-bot">
          <span class="date">{{ r.date }}</span>
          <van-tag size="medium">{{ r.category }}</van-tag>
          <span class="advice">{{ r.advice }}</span>
          <van-icon name="delete-o" @click="delLedger(r.id)" />
        </div>
      </div>
      <van-empty v-if="!records.length" description="还没有流水" :image-size="60" />
    </div>

    <!-- 家底编辑弹框 -->
    <van-popup v-model:show="holdDlg" position="bottom" round :style="{ height: '80%' }">
      <div class="dlg">
        <van-nav-bar :title="holdForm.id ? '编辑条目' : '添加资产/负债'" left-text="取消" left-arrow @click-left="holdDlg = false" />
        <van-radio-group v-model="holdForm.bigType" direction="horizontal" style="padding:10px 16px">
          <van-radio name="资产">资产</van-radio>
          <van-radio name="负债">负债</van-radio>
        </van-radio-group>
        <van-field v-model="holdForm.name" label="名称" placeholder="如:出租房产 / 信用卡欠款" />
        <van-field v-model.number="holdForm.amount" type="number" label="现值金额" placeholder="资产现值或负债余额" />
        <van-field v-model.number="holdForm.monthlyCashflow" type="number" label="每月现金流" placeholder="收入正/支出负" />
        <van-field v-model="holdForm.note" type="textarea" label="备注" rows="2" />
        <van-button type="primary" block :loading="holdSaving" @click="saveHold">保存</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { ledgerApi, holdingApi } from '../api'
import AiDialog from '../components/AiDialog.vue'

const sum = ref({})
const records = ref([])
const holdings = ref([])
const hsum = ref({})
const saving = ref(false)
const holdDlg = ref(false)
const holdSaving = ref(false)
const showDate = ref(false)
const datePick = ref(['2026', '7', '28'])
const minDate = new Date(2018, 0, 1)
const maxDate = new Date(2030, 11, 31)

const form = reactive({ type: 'expense', amount: null, desc: '', date: new Date().toISOString().slice(0, 10), category: '' })
const holdForm = reactive({ id: null, bigType: '资产', name: '', amount: null, monthlyCashflow: null, note: '' })

const finPayload = computed(() => ({
  name: '个人财务概况(随手记)', totalIncome: sum.value.totalIncome, passiveIncome: sum.value.passiveIncome,
  totalExpense: sum.value.totalExpense, assetExpense: sum.value.assetExpense, liabilityExpense: sum.value.liabilityExpense,
  coverage: sum.value.coverage, diagnosis: sum.value.diagnosis
}))
const holdPayload = computed(() => ({
  name: '存量资产负债表(家底)', totalAsset: hsum.value.totalAsset, totalLiability: hsum.value.totalLiability,
  netWorth: hsum.value.netWorth, realAssetRatio: hsum.value.realAssetRatio,
  items: holdings.value.map((h) => ({ 大类: h.bigType, 名称: h.name, 金额: h.amount, 月现金流: h.monthlyCashflow, 判定: h.verdict }))
}))

function fmt(n) { return n == null ? '0' : Number(n).toLocaleString('zh-CN') }
function covColor(p) { return p >= 100 ? '#43a047' : p >= 50 ? '#e6a23c' : '#e53935' }
function verdictColor(v) { return { '真资产': 'success', '伪资产': 'warning', '投资性负债': 'info', '消费性负债': 'danger' }[v] || 'info' }

function onDate() { form.date = datePick.value.join('-'); showDate.value = false }

async function saveLedger() {
  if (!form.amount || form.amount <= 0) { showToast('请输入金额'); return }
  if (!form.desc.trim()) { showToast('请填写描述'); return }
  saving.value = true
  try {
    const res = await ledgerApi.add({ ...form })
    showSuccessToast('已记账,归类为「' + res.data.category + '」')
    form.amount = null; form.desc = ''; form.category = ''
    await loadAll()
  } finally { saving.value = false }
}
async function delLedger(id) { await ledgerApi.remove(id); showSuccessToast('已删除'); await loadAll() }

function openHold(row) {
  if (row) Object.assign(holdForm, { id: row.id, bigType: row.bigType, name: row.name, amount: row.amount, monthlyCashflow: row.monthlyCashflow, note: row.note })
  else Object.assign(holdForm, { id: null, bigType: '资产', name: '', amount: null, monthlyCashflow: null, note: '' })
  holdDlg.value = true
}
async function saveHold() {
  if (!holdForm.name.trim()) { showToast('请填写名称'); return }
  if (holdForm.amount == null || holdForm.amount < 0) { showToast('请填写现值金额'); return }
  holdSaving.value = true
  try {
    const body = { bigType: holdForm.bigType, name: holdForm.name, amount: holdForm.amount, monthlyCashflow: holdForm.monthlyCashflow || 0, note: holdForm.note }
    if (holdForm.id) await holdingApi.update(holdForm.id, body); else await holdingApi.add(body)
    showSuccessToast(holdForm.id ? '已更新' : '已添加')
    holdDlg.value = false
    await loadHoldings()
  } finally { holdSaving.value = false }
}
async function delHold(id) {
  await showConfirmDialog({ title: '提示', message: '确认删除该条目?' })
  await holdingApi.remove(id); showSuccessToast('已删除'); await loadHoldings()
}
async function loadHoldings() {
  const [l, s] = await Promise.all([holdingApi.list(), holdingApi.summary()])
  holdings.value = l.data; hsum.value = s.data
}
async function loadAll() {
  const [s, l] = await Promise.all([ledgerApi.summary(), ledgerApi.list()])
  sum.value = s.data; records.value = l.data
  await loadHoldings()
}
onMounted(loadAll)
</script>

<style scoped>
.page { padding-bottom: 12px; }
.head { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px 0; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 0 10px; }
.s { margin: 0; }
.l { color: #909399; font-size: 12px; }
.v { font-size: 18px; font-weight: 700; margin: 4px 0; }
.f { font-size: 11px; color: #909399; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 10px 0; }
.mini { background: #f6f8fa; border-radius: 10px; padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.mini span { font-size: 12px; color: #909399; }
.mini b { font-size: 17px; }
.item { padding: 10px 0; border-bottom: 1px dashed #ebeef5; }
.it-top { display: flex; align-items: center; gap: 8px; }
.it-top .code { color: #909399; font-size: 12px; margin-left: auto; }
.it-bot { display: flex; align-items: center; gap: 8px; margin-top: 6px; font-size: 12px; color: #606266; }
.ops { margin-left: auto; display: flex; gap: 12px; color: #2b6cb0; }
.ledger { padding: 9px 0; border-bottom: 1px dashed #ebeef5; }
.ld-top { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.ld-top .desc { flex: 1; }
.ld-bot { display: flex; align-items: center; gap: 6px; margin-top: 4px; font-size: 11px; color: #909399; }
.ld-bot .advice { flex: 1; }
.m-title { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.m-title :deep(.van-button) { margin-left: auto; }
.dlg { height: 100%; overflow-y: auto; }
</style>
