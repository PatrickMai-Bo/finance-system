import { baseURL, apiEndpoints } from '../config/api.config'

const request = (module, action, params = {}, config = {}) => {
  const endpoint = apiEndpoints[module][action]
  let url = endpoint
  
  // 处理路径参数
  Object.keys(params).forEach(key => {
    if (url.includes(`:${key}`)) {
      url = url.replace(`:${key}`, params[key])
      delete params[key]
    }
  })

  return new Promise((resolve, reject) => {
    wx.request({
      url: baseURL + url,
      method: config.method || 'GET',
      data: params,
      header: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${wx.getStorageSync('token')}`
      },
      success: (res) => {
        if (res.data.code === 0) {
          resolve(res.data.data)
        } else {
          reject(res.data.message)
        }
      },
      fail: (err) => {
        reject(err.errMsg)
      }
    })
  })
}

// 首页相关
export const getHomeOverview = () => request('home', 'overview')
export const getUserInfo = () => request('auth', 'info')
export const login = (data) => request('auth', 'login', data, { method: 'POST' })

// 股票相关
export const getStockList = (params) => request('stock', 'list', params)

// 基金相关
export const getFundList = (params) => request('fund', 'list', params)

// 财务相关
export const getFinanceBalanceSheet = () => request('finance', 'balanceSheet')
export const getFinanceCashflow = () => request('finance', 'cashflow')
export const getFinanceFreedom = () => request('finance', 'freedom')

// 记账相关
export const getLedgerList = () => request('ledger', 'list')
export const getLedgerSummary = () => request('ledger', 'summary')
export const addLedger = (data) => request('ledger', 'add', data, { method: 'POST' })
export const deleteLedger = (id) => request('ledger', 'delete', { id }, { method: 'DELETE' })

// 存量资产相关
export const getHoldingList = () => request('holding', 'list')
export const getHoldingSummary = () => request('holding', 'summary')
export const addHolding = (data) => request('holding', 'add', data, { method: 'POST' })
export const updateHolding = (id, data) => request('holding', 'update', { id, ...data }, { method: 'PUT' })
export const deleteHolding = (id) => request('holding', 'delete', { id }, { method: 'DELETE' })

// 自选清单相关
export const getWatchlistList = (params) => request('watchlist', 'list', params)
export const getWatchlistSummary = (params) => request('watchlist', 'summary', params)
export const addWatchlist = (data) => request('watchlist', 'add', data, { method: 'POST' })
export const updateWatchlist = (id, data) => request('watchlist', 'update', { id, ...data }, { method: 'PUT' })
export const deleteWatchlist = (id) => request('watchlist', 'delete', { id }, { method: 'DELETE' })

// 决策思维相关
export const getDecisionModels = () => request('decision', 'models')
export const getDecisionFramework = () => request('decision', 'framework')
export const searchDecision = (data) => request('decision', 'search', data, { method: 'POST' })
export const getDecisionLogs = () => request('decision', 'logs')

// AI分析相关
export const analyzeAI = (data) => request('ai', 'analyze', data, { method: 'POST' })

// LLM配置相关
export const getLLMConfigs = () => request('llm', 'configs')
export const addLLMConfig = (data) => request('llm', 'addConfig', data, { method: 'POST' })
export const updateLLMConfig = (id, data) => request('llm', 'updateConfig', { id, ...data }, { method: 'PUT' })
export const deleteLLMConfig = (id) => request('llm', 'deleteConfig', { id }, { method: 'DELETE' })
export const activateLLM = (id) => request('llm', 'activate', { id }, { method: 'POST' })
export const testLLM = (id) => request('llm', 'test', { id }, { method: 'POST' })