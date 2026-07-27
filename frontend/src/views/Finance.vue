<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <div>
        <h2 class="page-title">个人财务系统 · 随手记</h2>
        <p class="page-sub">手动记一笔 → 自动按「资产 / 负债」分类 → 看清钱是让你更富还是更穷</p>
      </div>
      <AiAnalyze scene="finance" :payload="aiPayload" />
    </div>

    <!-- 关键指标 -->
    <el-row :gutter="16">
      <el-col :xs="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">本期收入</div>
          <div class="stat-value up">¥{{ fmt(sum.totalIncome) }}</div>
          <div class="stat-foot">主动 ¥{{ fmt(sum.activeIncome) }} · 被动 <b class="up">¥{{ fmt(sum.passiveIncome) }}</b></div>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">本期支出</div>
          <div class="stat-value down">¥{{ fmt(sum.totalExpense) }}</div>
          <div class="stat-foot">资产型 {{ sum.assetRatio }}% · 负债型 {{ sum.liabRatio }}%</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">净现金流</div>
          <div class="stat-value" :class="sum.netCashflow>=0?'up':'down'">¥{{ fmt(sum.netCashflow) }}</div>
          <div class="stat-foot">{{ sum.netCashflow>=0?'结余,记得转化为资产':'入不敷出,需先止血' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">财务自由覆盖率</div>
          <div class="stat-value" :style="{color: freedomColor(sum.coverage)}">{{ sum.coverage }}%</div>
          <div class="stat-foot">被动收入 / 总支出</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 富爸爸诊断 -->
    <el-alert v-if="sum.diagnosis" type="warning" :closable="false" show-icon style="margin:14px 0">
      <template #title><b>富爸爸诊断(流水):</b>{{ sum.diagnosis }}</template>
    </el-alert>

    <!-- 存量资产负债表(家底)· 自行填写 + 增删改查 + AI -->
    <el-card class="section-card" shadow="hover">
      <template #header>
        <div class="card-head">
          <div>
            <b>存量资产负债表 · 我的家底</b>
            <span class="head-sub">自行填入你现在拥有的资产 / 背负的负债 → 系统按富爸爸「真资产 vs 伪资产」判定</span>
          </div>
          <div class="head-ops">
            <AiAnalyze scene="holding" :payload="holdingAiPayload" label="AI 资产诊断" />
            <el-button type="primary" :icon="Plus" @click="openHold()">添加资产 / 负债</el-button>
          </div>
        </div>
      </template>

      <!-- 家底关键指标 -->
      <el-row :gutter="12" class="hold-stats">
        <el-col :xs="12" :md="6">
          <div class="mini-stat"><span>总资产</span><b class="up">¥{{ fmt(hsum.totalAsset) }}</b></div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="mini-stat"><span>总负债</span><b class="down">¥{{ fmt(hsum.totalLiability) }}</b></div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="mini-stat"><span>净资产</span><b :class="hsum.netWorth>=0?'up':'down'">¥{{ fmt(hsum.netWorth) }}</b></div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="mini-stat"><span>真资产占比</span><b :style="{color: freedomColor(hsum.realAssetRatio)}">{{ hsum.realAssetRatio }}%</b></div>
        </el-col>
      </el-row>
      <el-alert v-if="hsum.diagnosis" type="info" :closable="false" show-icon style="margin:6px 0 12px">
        <template #title><b>富爸爸家底诊断:</b>{{ hsum.diagnosis }}</template>
      </el-alert>

      <el-table :data="holdings" size="default" stripe max-height="420">
        <el-table-column label="大类" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.bigType==='资产'?'success':'danger'" effect="plain">{{ row.bigType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column label="现值金额" width="130" align="right">
          <template #default="{ row }"><b>¥{{ fmt(row.amount) }}</b></template>
        </el-table-column>
        <el-table-column label="每月现金流" width="120" align="right">
          <template #default="{ row }">
            <b :class="row.monthlyCashflow>=0?'up':'down'">{{ row.monthlyCashflow>=0?'+':'' }}¥{{ fmt(row.monthlyCashflow) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="富爸爸判定" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="verdictColor(row.verdict)">{{ row.verdict }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openHold(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="delHold(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 存量条目 增/改 弹窗 -->
    <el-dialog v-model="holdDlg" :title="holdForm.id?'编辑条目':'添加资产 / 负债'" width="460px" append-to-body>
      <el-form :model="holdForm" label-width="92px">
        <el-form-item label="大类">
          <el-radio-group v-model="holdForm.bigType">
            <el-radio-button value="资产">资产</el-radio-button>
            <el-radio-button value="负债">负债</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="holdForm.name" placeholder="如:出租房产 / 股票基金 / 信用卡欠款" />
        </el-form-item>
        <el-form-item label="现值金额">
          <el-input v-model.number="holdForm.amount" type="number" placeholder="资产现值或负债余额(填正数)">
            <template #prepend>¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="每月现金流">
          <el-input v-model.number="holdForm.monthlyCashflow" type="number" placeholder="收入填正、支出填负,如出租+2500 / 房贷-6000">
            <template #prepend>¥</template>
          </el-input>
          <div class="form-hint">正数=每月能生钱(真资产判定关键) · 负数=每月要掏钱</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="holdForm.note" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="holdDlg=false">取消</el-button>
        <el-button type="primary" :loading="holdSaving" @click="saveHold">保存</el-button>
      </template>
    </el-dialog>

    <el-row :gutter="16">
      <!-- 记一笔 -->
      <el-col :xs="24" :md="9">
        <el-card class="section-card" shadow="hover">
          <template #header><b>记一笔</b> · 系统自动帮你分类</template>
          <el-form :model="form" label-width="64px" size="default">
            <el-form-item label="类型">
              <el-radio-group v-model="form.type">
                <el-radio-button value="expense">支出</el-radio-button>
                <el-radio-button value="income">收入</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="金额">
              <el-input v-model.number="form.amount" type="number" placeholder="0.00">
                <template #prepend>¥</template>
              </el-input>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.desc" placeholder="如:报名Python课程 / 买名牌包 / 房租收入" />
            </el-form-item>
            <el-form-item label="日期">
              <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
            <el-form-item label="分类">
              <el-select v-model="form.category" clearable placeholder="留空→系统按富爸爸逻辑自动分类" style="width:100%">
                <el-option v-for="c in catOptions(form.type)" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-button type="primary" style="width:100%" :loading="saving" @click="save">记下这一笔</el-button>
          </el-form>
          <div class="tip">
            <p><el-tag size="small" type="success">消费资产型</el-tag> 学习/工具/健康/投资本金 — 会增值</p>
            <p><el-tag size="small" type="danger">消费负债型</el-tag> 奢侈/冲动/烟酒 — 持续消耗</p>
            <p><el-tag size="small" type="info">中性刚需</el-tag> 房租/餐饮/交通 — 维持生活</p>
            <p><el-tag size="small" type="warning">被动收入</el-tag> 房租/分红/利息 — 财务自由关键</p>
          </div>
        </el-card>
      </el-col>

      <!-- 图表 -->
      <el-col :xs="24" :md="15">
        <el-card class="section-card" shadow="hover">
          <template #header><b>现金流象限</b>(收入来源 vs 支出结构)· 富爸爸看的是结构不是数字</template>
          <div ref="cashChart" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 流水明细 -->
    <el-card class="section-card" shadow="hover">
      <template #header><b>记账流水</b> · 每一笔都已按资产/负债标尺归类</template>
      <el-table :data="records" size="default" stripe max-height="440">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column label="收支" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type==='income'?'success':'info'" effect="plain">{{ row.type==='income'?'收入':'支出' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="desc" label="描述" min-width="160" />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <b :class="row.type==='income'?'up':'down'">{{ row.type==='income'?'+':'-' }}¥{{ fmt(row.amount) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="富爸爸分类" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="catColor(row.category)">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="点评" min-width="240">
          <template #default="{ row }"><span class="advice">{{ row.advice }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="del(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import AiAnalyze from '../components/AiAnalyze.vue'
import { ledgerApi, holdingApi } from '../api'

const loading = ref(false)
const saving = ref(false)
const sum = ref({})
const records = ref([])
const cashChart = ref(null)
let chartInst = null

// ===== 存量资产负债表(家底) =====
const holdings = ref([])
const hsum = ref({})
const holdDlg = ref(false)
const holdSaving = ref(false)
const holdForm = reactive({ id: null, bigType: '资产', name: '', amount: null, monthlyCashflow: null, note: '' })

const holdingAiPayload = computed(() => ({
  name: '存量资产负债表(家底)',
  totalAsset: hsum.value.totalAsset,
  totalLiability: hsum.value.totalLiability,
  netWorth: hsum.value.netWorth,
  realAssetRatio: hsum.value.realAssetRatio,
  monthlyPassive: hsum.value.monthlyPassive,
  monthlyOutflow: hsum.value.monthlyOutflow,
  diagnosis: hsum.value.diagnosis,
  items: holdings.value.map(h => ({ 大类: h.bigType, 名称: h.name, 金额: h.amount, 月现金流: h.monthlyCashflow, 判定: h.verdict }))
}))

function verdictColor(v) {
  return { '真资产': 'success', '伪资产': 'warning', '投资性负债': 'info', '消费性负债': 'danger' }[v] || 'info'
}

function openHold(row) {
  if (row) {
    Object.assign(holdForm, { id: row.id, bigType: row.bigType, name: row.name, amount: row.amount, monthlyCashflow: row.monthlyCashflow, note: row.note })
  } else {
    Object.assign(holdForm, { id: null, bigType: '资产', name: '', amount: null, monthlyCashflow: null, note: '' })
  }
  holdDlg.value = true
}

async function saveHold() {
  if (!holdForm.name.trim()) { ElMessage.warning('请填写名称'); return }
  if (holdForm.amount == null || holdForm.amount < 0) { ElMessage.warning('请填写现值金额(填正数)'); return }
  holdSaving.value = true
  try {
    const body = { bigType: holdForm.bigType, name: holdForm.name, amount: holdForm.amount, monthlyCashflow: holdForm.monthlyCashflow || 0, note: holdForm.note }
    if (holdForm.id) await holdingApi.update(holdForm.id, body)
    else await holdingApi.add(body)
    ElMessage.success(holdForm.id ? '已更新' : '已添加')
    holdDlg.value = false
    await loadHoldings()
  } finally { holdSaving.value = false }
}

async function delHold(id) {
  await ElMessageBox.confirm('确认删除该条目?', '提示', { type: 'warning' })
  await holdingApi.remove(id)
  ElMessage.success('已删除')
  await loadHoldings()
}

async function loadHoldings() {
  const [l, s] = await Promise.all([holdingApi.list(), holdingApi.summary()])
  holdings.value = l.data; hsum.value = s.data
}

const form = reactive({
  type: 'expense',
  amount: null,
  desc: '',
  date: new Date().toISOString().slice(0, 10),
  category: ''
})

const aiPayload = computed(() => ({
  name: '个人财务概况(随手记)',
  totalIncome: sum.value.totalIncome,
  passiveIncome: sum.value.passiveIncome,
  totalExpense: sum.value.totalExpense,
  assetExpense: sum.value.assetExpense,
  liabilityExpense: sum.value.liabilityExpense,
  coverage: sum.value.coverage,
  diagnosis: sum.value.diagnosis
}))

function fmt(n) { return n == null ? '0' : Number(n).toLocaleString('zh-CN') }
function freedomColor(p) { return p >= 100 ? '#43a047' : p >= 50 ? '#e6a23c' : '#e53935' }
function catColor(c) {
  return { '消费资产型': 'success', '被动收入': 'warning', '消费负债型': 'danger', '主动收入': 'primary', '中性刚需': 'info' }[c] || 'info'
}
function catOptions(type) {
  return type === 'income' ? ['主动收入', '被动收入'] : ['消费资产型', '消费负债型', '中性刚需']
}

async function loadAll() {
  loading.value = true
  try {
    const [s, l] = await Promise.all([ledgerApi.summary(), ledgerApi.list()])
    sum.value = s.data; records.value = l.data
    await loadHoldings()
    await nextTick()
    renderCash()
  } finally { loading.value = false }
}

async function save() {
  if (!form.amount || form.amount <= 0) { ElMessage.warning('请输入金额'); return }
  if (!form.desc.trim()) { ElMessage.warning('请填写描述,便于系统自动分类'); return }
  saving.value = true
  try {
    const res = await ledgerApi.add({ ...form })
    ElMessage.success('已记账,富爸爸归类为「' + res.data.category + '」')
    form.amount = null; form.desc = ''; form.category = ''
    await loadAll()
  } finally { saving.value = false }
}

async function del(id) {
  await ledgerApi.remove(id)
  ElMessage.success('已删除')
  await loadAll()
}

function renderCash() {
  if (!chartInst) chartInst = echarts.init(cashChart.value)
  const s = sum.value
  chartInst.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [
      {
        name: '收入来源', type: 'pie', radius: ['30%', '52%'], center: ['27%', '45%'],
        label: { formatter: '{b}\n{d}%', fontSize: 11 },
        data: [
          { name: '主动收入', value: s.activeIncome || 0, itemStyle: { color: '#409eff' } },
          { name: '被动收入', value: s.passiveIncome || 0, itemStyle: { color: '#e6a23c' } }
        ]
      },
      {
        name: '支出结构', type: 'pie', radius: ['30%', '52%'], center: ['73%', '45%'],
        label: { formatter: '{b}\n{d}%', fontSize: 11 },
        data: [
          { name: '消费资产型', value: s.assetExpense || 0, itemStyle: { color: '#43a047' } },
          { name: '消费负债型', value: s.liabilityExpense || 0, itemStyle: { color: '#e53935' } },
          { name: '中性刚需', value: s.neutralExpense || 0, itemStyle: { color: '#909399' } }
        ]
      }
    ]
  })
}

onMounted(loadAll)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 22px; font-weight: 700; margin: 6px 0; }
.stat-foot { font-size: 11px; color: #909399; }
.tip { margin-top: 14px; border-top: 1px dashed #ebeef5; padding-top: 12px; }
.tip p { margin: 6px 0; font-size: 12px; color: #606266; }
.advice { font-size: 12px; color: #909399; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.head-sub { font-size: 12px; color: #909399; margin-left: 10px; font-weight: normal; }
.head-ops { display: flex; gap: 10px; align-items: center; }
.hold-stats { margin-bottom: 10px; }
.mini-stat { background: #f6f8fa; border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 4px; }
.mini-stat span { font-size: 12px; color: #909399; }
.mini-stat b { font-size: 19px; }
.form-hint { font-size: 11px; color: #909399; line-height: 1.4; margin-top: 4px; }
</style>
