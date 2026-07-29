const express = require('express')
const router = express.Router()
const { faker } = require('@faker-js/faker/locale/zh_CN')

const generateHoldings = (count = 8) => {
  const bigTypes = ['资产', '负债']
  const names = {
    '资产': ['股票投资', '基金定投', '出租房产', '银行存款', '黄金储备', '数字资产'],
    '负债': ['房贷', '车贷', '信用卡债务', '消费贷款', '助学贷款']
  }
  
  return Array.from({ length: count }, (_, i) => {
    const bigType = i < 6 ? '资产' : '负债'
    const nameOptions = names[bigType]
    const name = nameOptions[i % nameOptions.length]
    
    return {
      id: i + 1,
      bigType: bigType,
      name: name,
      amount: bigType === '资产' ? 
        faker.finance.amount(50000, 500000, 0) : 
        faker.finance.amount(10000, 300000, 0),
      monthlyCashflow: bigType === '资产' ? 
        faker.finance.amount(100, 5000, 0) : 
        -faker.finance.amount(100, 3000, 0),
      note: `${bigType === '资产' ? '优质' : '待优化'}资产，${bigType === '资产' ? '年化收益率' : '年化利率'}约${bigType === '资产' ? '5-8%' : '4-6%'}`
    }
  })
}

// 存量资产列表
router.get('/list', (req, res) => {
  const holdings = generateHoldings()
  res.json({
    code: 0,
    message: 'success',
    data: holdings
  })
})

// 存量资产汇总
router.get('/summary', (req, res) => {
  const holdings = generateHoldings()
  const totalAsset = holdings.filter(h => h.bigType === '资产').reduce((sum, h) => sum + parseFloat(h.amount), 0)
  const totalLiability = holdings.filter(h => h.bigType === '负债').reduce((sum, h) => sum + parseFloat(h.amount), 0)
  const monthlyCashflow = holdings.reduce((sum, h) => sum + parseFloat(h.monthlyCashflow), 0)
  
  res.json({
    code: 0,
    message: 'success',
    data: {
      totalAsset: totalAsset,
      totalLiability: totalLiability,
      netWorth: totalAsset - totalLiability,
      monthlyCashflow: monthlyCashflow,
      assetTypes: [
        { type: '股票基金', amount: totalAsset * 0.4 },
        { type: '房产', amount: totalAsset * 0.35 },
        { type: '现金类', amount: totalAsset * 0.15 },
        { type: '其他', amount: totalAsset * 0.1 }
      ],
      liabilityTypes: [
        { type: '房贷', amount: totalLiability * 0.7 },
        { type: '消费贷', amount: totalLiability * 0.2 },
        { type: '其他', amount: totalLiability * 0.1 }
      ]
    }
  })
})

// 添加存量资产
router.post('/add', (req, res) => {
  const { bigType, name, amount, monthlyCashflow, note } = req.body
  
  if (!bigType || !name || !amount) {
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
      ...req.body
    }
  })
})

// 修改存量资产
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

// 删除存量资产
router.delete('/:id', (req, res) => {
  const { id } = req.params
  res.json({
    code: 0,
    message: '删除成功'
  })
})

module.exports = router