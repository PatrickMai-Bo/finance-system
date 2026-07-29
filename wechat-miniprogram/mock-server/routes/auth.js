const express = require('express')
const router = express.Router()
const { generateUser } = require('../mockData')

// 登录接口
router.post('/login', (req, res) => {
  const { username, password } = req.body
  
  if (username === 'demo' && password === '123456') {
    const user = generateUser()
    res.json({
      code: 0,
      message: '登录成功',
      data: {
        token: user.token,
        userInfo: {
          nickname: user.nickname,
          avatar: user.avatar
        }
      }
    })
  } else {
    res.status(401).json({
      code: 401,
      message: '用户名或密码错误'
    })
  }
})

// 获取用户信息
router.get('/info', (req, res) => {
  try {
    const user = generateUser()
    res.json({
      code: 0,
      message: 'success',
      data: user
    })
  } catch (e) {
    console.error('/info error:', e.message)
    res.json({
      code: 0,
      message: 'success',
      data: {
        userId: 'demo-001',
        username: 'demo',
        nickname: '投资者',
        avatar: 'https://api.dicebear.com/7.x/thumbs/svg?seed=finance',
        token: 'mock-token-' + Date.now()
      }
    })
  }
})

module.exports = router