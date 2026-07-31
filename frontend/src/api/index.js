import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

http.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data && typeof data.code !== 'undefined' && data.code !== 0) {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(data)
    }
    return data
  },
  (err) => {
    ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default http

// ===== 各板块 API =====
export const authApi = {
  login: (body) => http.post('/auth/login', body),
  me: () => http.get('/auth/me')
}
export const homeApi = {
  overview: () => http.get('/home/overview')
}
export const financeApi = {
  balanceSheet: () => http.get('/finance/balance-sheet'),
  cashflow: () => http.get('/finance/cashflow'),
  freedom: () => http.get('/finance/freedom')
}
export const ledgerApi = {
  add: (rec) => http.post('/ledger/add', rec),
  remove: (id) => http.delete('/ledger/' + id),
  list: () => http.get('/ledger/list'),
  summary: () => http.get('/ledger/summary')
}
export const holdingApi = {
  add: (rec) => http.post('/holding/add', rec),
  update: (id, rec) => http.put('/holding/' + id, rec),
  remove: (id) => http.delete('/holding/' + id),
  list: () => http.get('/holding/list'),
  summary: () => http.get('/holding/summary')
}
export const watchlistApi = {
  add: (rec) => http.post('/watchlist/add', rec),
  update: (id, rec) => http.put('/watchlist/' + id, rec),
  remove: (id) => http.delete('/watchlist/' + id),
  list: (type) => http.get('/watchlist/list', { params: { type } }),
  summary: (type) => http.get('/watchlist/summary', { params: { type } })
}
export const screenApi = {
  // 列表接口默认会附带 deepAnalysis(LLM 精排),30+ 只股票 + DeepSeek 慢响应会超过默认 30s
  stock: (page, size) => http.get('/screen/stock', { params: { page, size }, timeout: 90000 }),
  fund: (category, page, size) => http.get('/screen/fund', { params: { category, page, size }, timeout: 90000 }),
  runStock: () => http.post('/screen/stock/run'),
  runFund: () => http.post('/screen/fund/run'),
  // 建议持有时间(AI 推算),耗时较长,放宽超时
  adviceStock: (page, size, invalidate) => http.post('/screen/stock/advice', null, { params: { page, size, invalidate }, timeout: 120000 }),
  adviceFund: (category, page, size, invalidate) => http.post('/screen/fund/advice', null, { params: { category, page, size, invalidate }, timeout: 120000 }),
  detail: (code) => http.get('/screen/detail/' + code),
  categories: () => http.get('/screen/fund/categories'),
  // 第二阶段深度分析(LLM),耗时较长
  analyzeStock: (code, invalidate) => http.post('/screen/stock/analyze/' + code, null, { params: { invalidate }, timeout: 180000 }),
  analyzeFund: (code, invalidate) => http.post('/screen/fund/analyze/' + code, null, { params: { invalidate }, timeout: 180000 }),
  // 全量精排(5条优化规则+LLM二次评分排序),耗时极长
  refinedStock: (page, size, force) => http.post('/screen/stock/refined', null, { params: { page, size, force }, timeout: 600000 }),
  refinedFund: (category, page, size, force) => http.post('/screen/fund/refined', null, { params: { category, page, size, force }, timeout: 600000 })
}
export const decisionApi = {
  models: () => http.get('/decision/models'),
  framework: () => http.get('/decision/framework'),
  search: (question, scene) => http.post('/decision/search', { question, scene }),
  logs: () => http.get('/decision/logs')
}
export const llmApi = {
  configs: () => http.get('/llm/configs'),
  add: (cfg) => http.post('/llm/configs', cfg),
  update: (id, cfg) => http.put('/llm/configs/' + id, cfg),
  remove: (id) => http.delete('/llm/configs/' + id),
  setActive: (id) => http.post('/llm/active/' + id),
  test: (id) => http.post('/llm/test/' + id, null, { timeout: 150000 }),
  /** 从磁盘重新加载最新配置到内存(改了文件/怀疑脱钩时点) */
  refresh: () => http.post('/llm/refresh')
}
export const aiApi = {
  // 真实大模型分析耗时较长,单独放宽超时
  analyze: (scene, payload) => http.post('/ai/analyze', { scene, payload }, { timeout: 150000 })
}
export const systemApi = {
  /** 在线人数(2 分钟内有心跳的 session 数) */
  online: () => http.get('/system/online'),
  /** 前端定时心跳 */
  ping: () => http.post('/system/ping'),
  /** 后台预热基金/股票深度分析(命中缓存就秒返) */
  warmup: () => http.post('/system/warmup')
}
