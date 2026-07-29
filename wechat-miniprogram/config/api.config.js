// API配置
export const baseURL = 'http://localhost:3003/api'

// 接口映射配置
export const apiEndpoints = {
  home: {
    overview: '/home/overview'
  },
  stock: {
    list: '/stock/list',
    detail: '/stock/detail/:code'
  },
  fund: {
    list: '/fund/list'
  },
  auth: {
    login: '/auth/login',
    info: '/auth/info'
  },
  finance: {
    balanceSheet: '/finance/balance-sheet',
    cashflow: '/finance/cashflow',
    freedom: '/finance/freedom'
  },
  ledger: {
    list: '/ledger/list',
    summary: '/ledger/summary',
    add: '/ledger/add',
    delete: '/ledger/:id'
  },
  holding: {
    list: '/holding/list',
    summary: '/holding/summary',
    add: '/holding/add',
    update: '/holding/:id',
    delete: '/holding/:id'
  },
  watchlist: {
    list: '/watchlist/list',
    summary: '/watchlist/summary',
    add: '/watchlist/add',
    update: '/watchlist/:id',
    delete: '/watchlist/:id'
  },
  decision: {
    models: '/decision/models',
    framework: '/decision/framework',
    search: '/decision/search',
    logs: '/decision/logs'
  },
  ai: {
    analyze: '/ai/analyze'
  },
  llm: {
    configs: '/llm/configs',
    addConfig: '/llm/configs',
    updateConfig: '/llm/configs/:id',
    deleteConfig: '/llm/configs/:id',
    activate: '/llm/active/:id',
    test: '/llm/test/:id'
  }
}