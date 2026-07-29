const express = require('express')
const router = express.Router()
const { faker } = require('@faker-js/faker/locale/zh_CN')

const generateLedger = (count =17) => {
  const types = ['income', 'expense']
  const categories = {
    income: ['salary', 'bonus', 'investment', 'rent', 'dividend'],
    expense: ['food', 'transport', 'housing', 'education', 'entertainment', 'health']
  }
  
  return Array.from({ length: count }, (_, i) => {
    const type = types[i % 2]
    const categoryOptions = categories[type]
    const category = categoryOptions[i % categoryOptions.length]
    
    return {
      id: i + 1,
      date: faker.date.recent().toISOString().split('T')[0],
      type: type,
      amount: type === 'income' ? 
        faker.finance.amount(1000, 20000, 0) : 
        faker.finance.amount(50, 5000, 0),
      description: type === 'income' ?
        `${['工资', '奖金', '投资收益', '房租', '分红'][i % 5]}` :
        `${['餐饮', '交通', '房租', '学费', '娱乐', '医疗'][i % 6]}`,
      category: category
    }
  })
}

// 记账列表
router.get('/list', (req, res) => {
  const data = generateLedger(15)
  res.json({
    code: 0,
    message: 'success',
    data: data
  })
})

// 记账汇总
router.get('/summary', (req, res) => {
  const ledger = generateLedger(20)
  const totalIncome = ledger.filter(l => l.type === 'income').reduce((sum, l) => sum + parseFloat(l.amount), 0)
  const totalExpense = ledger.filter(l => l.type === 'expense').reduce((sum, l) => sum + parseFloat(l.amount), 0)
  
  res.json({
    code: 0,
    message: 'success',
    data: {
      totalIncome: totalIncome,
      totalExpense: totalExpense,
      netCashflow: totalIncome - totalExpense,
      incomeByCategory: [
        { category: 'salary', amount: totalIncome * 0.6 },
        { category: 'bonus', amount: totalIncome * 0.2 },
        { category: 'investment', amount: totalIncome * 0.15 },
        { category: 'rent', amount: totalIncome * 0.05 }
      ],
      expenseByCategory: [
        { category: 'housing', amount: totalExpense * 0.4 },
        { category: 'food', amount: totalExpense * 0.25 },
        { category: 'transport', amount: totalExpense * 0.15 },
        { category: 'education', amount: totalExpense * 0.1 },
        { category: 'entertainment', amount: totalExpense * 0.1 }
      ]
    }
  })
})

// 添加记账
router.post('/add', (req, res) => {
  const { date, type, amount, description, category } = req.body
  
  if (!date || !type || !amount || !description) {
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

// 删除记账
router.delete('/:id', (req, res) => {
  const { id } = req.params
  res.json({
    code: 0,
    message: '删除成功'
  })
})

module.exports = router