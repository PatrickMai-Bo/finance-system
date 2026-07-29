const express = require('express')
const router = express.Router()
const { faker } = require('@faker-js/faker/locale/zh_CN')

let configs = [
  {
    id: 1,
    name: 'DeepSeek',
    baseUrl: 'https://api.deepseek.com/v1',
    model: 'deepseek-chat',
    apiKey: 'sk-********' + faker.string.hexadecimal({ length: 16 }),
    enableSearch: true,
    isActive: true
  },
  {
    id: 2,
    name: '阿里百炼',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'qwen-max',
    apiKey: 'sk-********' + faker.string.hexadecimal({ length: 16 }),
    enableSearch: false,
    isActive: false
  },
  {
    id: 3,
    name: 'Kimi',
    baseUrl: 'https://api.moonshot.cn/v1',
    model: 'moonshot-v1-8k',
    apiKey: 'sk-********' + faker.string.hexadecimal({ length: 16 }),
    enableSearch: true,
    isActive: false
  },
  {
    id: 4,
    name: '智谱AI',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    model: 'glm-4',
    apiKey: 'sk-********' + faker.string.hexadecimal({ length: 16 }),
    enableSearch: false,
    isActive: false
  }
]

// 获取所有配置
router.get('/configs', (req, res) => {
  res.json({
    code: 0,
    message: 'success',
    data: configs
  })
})

// 添加配置
router.post('/configs', (req, res) => {
  const { name, baseUrl, model, apiKey, enableSearch } = req.body
  
  if (!name || !baseUrl || !model || !apiKey) {
    return res.status(400).json({
      code: 400,
      message: '缺少必要参数'
    })
  }
  
  const newConfig = {
    id: configs.length + 1,
    name,
    baseUrl,
    model,
    apiKey,
    enableSearch: enableSearch || false,
    isActive: false
  }
  
  configs.push(newConfig)
  
  res.json({
    code: 0,
    message: '添加成功',
    data: newConfig
  })
})

// 修改配置
router.put('/configs/:id', (req, res) => {
  const { id } = req.params
  const { name, baseUrl, model, apiKey, enableSearch } = req.body
  
  const index = configs.findIndex(c => c.id === parseInt(id))
  if (index === -1) {
    return res.status(404).json({
      code: 404,
      message: '配置不存在'
    })
  }
  
  configs[index] = {
    ...configs[index],
    name: name || configs[index].name,
    baseUrl: baseUrl || configs[index].baseUrl,
    model: model || configs[index].model,
    apiKey: apiKey || configs[index].apiKey,
    enableSearch: enableSearch !== undefined ? enableSearch : configs[index].enableSearch
  }
  
  res.json({
    code: 0,
    message: '修改成功',
    data: configs[index]
  })
})

// 删除配置
router.delete('/configs/:id', (req, res) => {
  const { id } = req.params
  
  const index = configs.findIndex(c => c.id === parseInt(id))
  if (index === -1) {
    return res.status(404).json({
      code: 404,
      message: '配置不存在'
    })
  }
  
  configs.splice(index, 1)
  
  res.json({
    code: 0,
    message: '删除成功'
  })
})

// 设为激活
router.post('/active/:id', (req, res) => {
  const { id } = req.params
  
  const index = configs.findIndex(c => c.id === parseInt(id))
  if (index === -1) {
    return res.status(404).json({
      code: 404,
      message: '配置不存在'
    })
  }
  
  // 取消所有激活状态
  configs.forEach(c => { c.isActive = false })
  
  // 激活指定配置
  configs[index].isActive = true
  
  res.json({
    code: 0,
    message: '激活成功',
    data: configs[index]
  })
})

// 测试连接
router.post('/test/:id', (req, res) => {
  const { id } = req.params
  
  const config = configs.find(c => c.id === parseInt(id))
  if (!config) {
    return res.status(404).json({
      code: 404,
      message: '配置不存在'
    })
  }
  
  // 模拟测试结果
  setTimeout(() => {
    res.json({
      code: 0,
      message: '连接测试成功',
      data: {
        config: config,
        latency: Math.floor(Math.random() * 200) + 50,
        testTime: new Date().toISOString(),
        status: 'available'
      }
    })
  }, 500)
})

module.exports = router