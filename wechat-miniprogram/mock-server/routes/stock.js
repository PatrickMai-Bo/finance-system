const express = require('express')
const router = express.Router()
const { generateStocks } = require('../mockData')

// 股票列表
router.get('/list', (req, res) => {
  const { page = 1, size = 10 } = req.query
  const pageNum = parseInt(page)
  const pageSize = parseInt(size)
  
  // 生成足够多的数据（30条，支持3页）
  const allData = generateStocks(30)
  
  // 计算分页数据
  const startIdx = (pageNum - 1) * pageSize
  const endIdx = startIdx + pageSize
  const paginatedData = allData.slice(startIdx, endIdx)
  
  res.json({
    code: 0,
    message: 'success',
    data: {
      list: paginatedData,
      pagination: {
        page: pageNum,
        size: pageSize,
        total: 30
      }
    }
  })
})

// 股票详情
router.get('/detail/:code', (req, res) => {
  const stock = generateStocks(1)[0]
  stock.code = req.params.code
  
  res.json({
    code: 0,
    message: 'success',
    data: stock
  })
})

module.exports = router