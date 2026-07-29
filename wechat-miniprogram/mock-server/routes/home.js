const express = require('express')
const router = express.Router()
const { generateHomeOverview } = require('../mockData')

// 首页概览
router.get('/overview', (req, res) => {
  const data = generateHomeOverview()
  
  res.json({
    code: 0,
    message: 'success',
    data: data
  })
})

module.exports = router