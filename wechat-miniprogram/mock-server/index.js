const express = require('express')
const cors = require('cors')
const app = express()
const PORT = 3003

// 中间件配置
app.use(cors())
app.use(express.json())
app.use(express.urlencoded({ extended: true }))

// 导入路由
const authRouter = require('./routes/auth')
const fundRouter = require('./routes/fund')
const stockRouter = require('./routes/stock')
const homeRouter = require('./routes/home')
const financeRouter = require('./routes/finance')
const ledgerRouter = require('./routes/ledger')
const holdingRouter = require('./routes/holding')
const watchlistRouter = require('./routes/watchlist')
const decisionRouter = require('./routes/decision')
const aiRouter = require('./routes/ai')
const llmRouter = require('./routes/llm')

// 路由挂载
app.use('/api/auth', authRouter)
app.use('/api/fund', fundRouter)
app.use('/api/stock', stockRouter)
app.use('/api/home', homeRouter)
app.use('/api/finance', financeRouter)
app.use('/api/ledger', ledgerRouter)
app.use('/api/holding', holdingRouter)
app.use('/api/watchlist', watchlistRouter)
app.use('/api/decision', decisionRouter)
app.use('/api/ai', aiRouter)
app.use('/api/llm', llmRouter)

// 健康检查
app.get('/ping', (req, res) => {
  res.send('pong')
})

// 启动服务器
app.listen(PORT, () => {
  console.log(`📡 Mock API server running at http://localhost:${PORT}`)
  console.log(`🛠  Available endpoints:`)
  console.log(`   - GET    /api/home/overview`)
  console.log(`   - GET    /api/fund/list`)
  console.log(`   - GET    /api/stock/list`)
  console.log(`   - GET    /api/finance/balance-sheet`)
  console.log(`   - GET    /api/finance/cashflow`)
  console.log(`   - GET    /api/finance/freedom`)
  console.log(`   - GET    /api/ledger/list`)
  console.log(`   - GET    /api/holding/list`)
  console.log(`   - GET    /api/watchlist/list`)
  console.log(`   - GET    /api/decision/models`)
  console.log(`   - POST   /api/auth/login`)
  console.log(`   - POST   /api/ai/analyze`)
  console.log(`   - GET    /api/llm/configs`)
})