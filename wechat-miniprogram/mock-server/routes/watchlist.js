const express = require('express')
const router = express.Router()
const { faker } = require('@faker-js/faker/locale/zh_CN')

const generateWatchlist = (type = 'stock', count = 5) => {
  const stockNames = ['贵州茅台', '腾讯控股', '宁德时代', '比亚迪', '招商银行', '中国平安', '五粮液', '隆基股份']
  const fundNames = ['天弘沪深300', '易方达消费行业', '华夏新能源', '嘉实核心成长', '南方中证500']
  
  return Array.from({ length: count }, (_, i) => {
    const isStock = type === 'stock'
    const code = isStock ? `600${100 + i}` : `00${8000 + i}`
    const name = isStock ? stockNames[i % stockNames.length] : fundNames[i % fundNames.length]
    
    return {
      id: i + 1,
      type: type,
      name: name,
      code: code,
      category: isStock ? 'A股' : (['股票型', '混合型', '债券型', '指数型'][i % 4]),
      cost: faker.finance.amount(50, 200, 2),
      amount: faker.finance.amount(100, 10000, 0),
      targetPrice: faker.finance.amount(60, 250, 2),
      currentPrice: faker.finance.amount(45, 220, 2),
      note: `${name}是${isStock ? '优质蓝筹股' : '绩优基金'}，长期看好其发展前景`
    }
  })
}

// 自选列表
router.get('/list', (req, res) => {
  const { type = 'stock' } = req.query
  const watchlist = generateWatchlist(type)
  res.json({
    code: 0,
    message: 'success',
    data: watchlist
  })
})

// 自选汇总
router.get('/summary', (req, res) => {
  const { type = 'stock' } = req.query
  const watchlist = generateWatchlist(type, 5)
  
  const totalCost = watchlist.reduce((sum, w) => sum + (parseFloat(w.cost) * parseFloat(w.amount)), 0)
  const totalValue = watchlist.reduce((sum, w) => sum + (parseFloat(w.currentPrice) * parseFloat(w.amount)), 0)
  const totalGainLoss = totalValue - totalCost
  const gainLossPercent = totalCost > 0 ? (totalGainLoss / totalCost * 100).toFixed(2) : 0
  
  res.json({
    code: 0,
    message: 'success',
    data: {
      totalItems: watchlist.length,
      totalCost: totalCost,
      totalValue: totalValue,
      totalGainLoss: totalGainLoss,
      gainLossPercent: gainLossPercent,
      byCategory: [
        { category: '消费', amount: totalValue * 0.3 },
        { category: '科技', amount: totalValue * 0.25 },
        { category: '金融', amount: totalValue * 0.2 },
        { category: '医药', amount: totalValue * 0.15 },
        { category: '其他', amount: totalValue * 0.1 }
      ]
    }
  })
})

// 添加自选
router.post('/add', (req, res) => {
  const { type, name, code, category, cost, amount, targetPrice, note } = req.body
  
  if (!type || !name || !code || !cost || !amount) {
    return res.status(400).json({
      code: 400,
      message: '缺少必要参数'
    })
  }
  
  res.json({
    code: 0,
    message: '添加成功',
    data: {
      id: Math.floor(Math.random() * 1000) + 100,
      ...req.body,
      currentPrice: parseFloat(cost) * (1 + Math.random() * 0.1 - 0.05)
    }
  })
})

// 修改自选
router.put('/:id', (req, res) => {
  const { id } = req.params
  res.json({
    code: 0,
    message: '修改成功',
    data: {
      id: parseInt(id),
      ...req.body
    }
  })
})

// 删除自选
router.delete('/:id', (req, res) => {
  const { id } = req.params
  res.json({
    code: 0,
    message: '删除成功'
  })
})

module.exports = router