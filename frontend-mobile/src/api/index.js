import axios from 'axios'
import { showToast } from 'vant'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

http.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data && typeof data.code !== 'undefined' && data.code !== 0) {
      showToast(data.msg || '请求失败')
      return Promise.reject(data)
    }
    return data
  },
  (err) => {
    showToast(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default http

export const authApi = {
  login: (body) => http.post('/auth/login', body),
  me: () => http.get('/auth/me'),
  forgotPassword: (phone) => http.post('/auth/forgot-password', { phone }),
  resetPassword: (resetToken, newPassword) => http.post('/auth/reset-password', { resetToken, newPassword })
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
  stock: (page, size) => http.get('/screen/stock', { params: { page, size } }),
  fund: (category, page, size) => http.get('/screen/fund', { params: { category, page, size } }),
  runStock: () => http.post('/screen/stock/run'),
  runFund: () => http.post('/screen/fund/run'),
  adviceStock: (page, size, invalidate) => http.post('/screen/stock/advice', null, { params: { page, size, invalidate }, timeout: 120000 }),
  adviceFund: (category, page, size, invalidate) => http.post('/screen/fund/advice', null, { params: { category, page, size, invalidate }, timeout: 120000 }),
  detail: (code) => http.get('/screen/detail/' + code),
  categories: () => http.get('/screen/fund/categories')
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
  test: (id) => http.post('/llm/test/' + id, null, { timeout: 150000 })
}
export const aiApi = {
  analyze: (scene, payload) => http.post('/ai/analyze', { scene, payload }, { timeout: 150000 })
}
