const express = require('express')
const router = express.Router()
const { generateFunds } = require('../mockData')

// 基金列表
router.get('/list', (req, res) => {
  const { type = '全部', page = 1, size = 10 } = req.query
  const pageNum = parseInt(page)
  const pageSize = parseInt(size)
  
  // 生成足够多的数据（30条，支持3页）
  let allData = generateFunds(30)
  
  if (type !== '全部') {
    allData = allData.filter(fund => fund.type === type)
  }
  
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
        total: allData.length
      }
    }
  })
})

module.exports = router