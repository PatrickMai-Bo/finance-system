const express = require('express')
const router = express.Router()

// 资产负债表
router.get('/balance-sheet', (req, res) => {
  res.json({
    code: 0,
    message: 'success',
    data: {
      assets: [
        { name: '指数基金定投', type: '真资产', amount: 180000, yield: 5.2 },
        { name: '股票组合', type: '真资产', amount: 220000, yield: 8.0 },
        { name: '出租房产', type: '真资产', amount: 1200000, yield: 3.5 },
        { name: '货币基金', type: '真资产', amount: 80000, yield: 2.1 },
        { name: '自住房(自用)', type: '中性', amount: 1800000, yield: 0 }
      ],
      liabilities: [
        { name: '房贷', type: '真负债', amount: 900000, yield: -4.1 },
        { name: '车贷', type: '真负债', amount: 60000, yield: -5.0 },
        { name: '信用卡', type: '真负债', amount: 15000, yield: -18.0 }
      ],
      totalAsset: 3480000,
      totalLiability: 975000,
      netWorth: 2505000,
      netWorthTrend: [2100000, 2180000, 2260000, 2350000, 2480000, 2605000],
      trendMonths: ['2月', '3月', '4月', '5月', '6月', '7月']
    }
  })
})

// 现金流
router.get('/cashflow', (req, res) => {
  res.json({
    code: 0,
    message: 'success',
    data: {
      activeIncome: 28000,
      passiveIncome: 6800,
      totalExpense: 18500,
      incomeItems: [
        { name: '工资', type: '主动', amount: 28000, yield: 0 },
        { name: '房租收入', type: '被动', amount: 4500, yield: 0 },
        { name: '基金分红', type: '被动', amount: 1500, yield: 0 },
        { name: '利息', type: '被动', amount: 800, yield: 0 }
      ],
      expenseItems: [
        { name: '房贷月供', type: '刚性', amount: 6000, yield: 0 },
        { name: '生活开销', type: '刚性', amount: 7000, yield: 0 },
        { name: '教育', type: '投资型', amount: 3000, yield: 0 },
        { name: '娱乐', type: '弹性', amount: 2500, yield: 0 }
      ]
    }
  })
})

// 财务自由度
router.get('/freedom', (req, res) => {
  res.json({
    code: 0,
    message: 'success',
    data: {
      passiveIncome: 6800,
      totalExpense: 18500,
      coverage: 36.8,
      targetPassive: 18500,
      gap: 11700,
      advice: '当前被动收入覆盖支出的36.8%,距财务自由还需每月增加被动现金流11700元。可优先扩大高股息资产与出租类真资产配置。'
    }
  })
})

module.exports = router