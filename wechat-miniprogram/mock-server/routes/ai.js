const express = require('express')
const router = express.Router()

// AI分析入口
router.post('/analyze', (req, res) => {
  let { scene, payload } = req.body
  
  // 处理stringify的payload
  if (typeof payload === 'string') {
    try {
      payload = JSON.parse(payload)
    } catch (e) {
      payload = {}
    }
  }
  
  if (!scene || !payload) {
    return res.status(400).json({
      code: 400,
      message: '缺少必要参数'
    })
  }
  
  const scenes = {
    'stock': '单只股票分析',
    'fund': '单只基金分析',
    'stock-batch': '多只股票横向对比',
    'fund-batch': '多只基金横向对比',
    'finance': '财务诊断',
    'holding': '资产负债点评',
    'watchlist': '自选组合点评',
    'decision': '五阶决策分析'
  }
  
  const adviceTemplates = {
    'stock': `根据提供的数据分析，该股票估值${Math.random() > 0.5 ? '合理' : '偏高'}，建议${Math.random() > 0.5 ? '分批建仓' : '观望等待'}。`,
    'fund': `该基金历史表现${Math.random() > 0.5 ? '稳健' : '波动较大'}，适合${Math.random() > 0.5 ? '长期持有' : '波段操作'}。`,
    'finance': `财务状况${Math.random() > 0.5 ? '健康' : '有待改善'}，建议${Math.random() > 0.5 ? '增加储蓄率' : '控制消费'}，优化资产配置。`,
    'holding': `资产配置${Math.random() > 0.5 ? '均衡' : '集中'}，建议${Math.random() > 0.5 ? '适当分散风险' : '聚焦核心资产'}。`,
    'decision': `决策考虑${Math.random() > 0.5 ? '周全' : '片面'}，建议${Math.random() > 0.5 ? '谨慎推进' : '重新评估'}。`
  }
  
  const analysis = {
    scene: scene,
    sceneName: scenes[scene] || '通用分析',
    input: payload,
    analysis: adviceTemplates[scene] || `根据${scenes[scene] || '通用'}场景分析，建议审慎决策，注重风险控制。`,
    recommendations: [
      '关注宏观经济环境变化',
      '设定明确的投资目标和期限',
      '定期复盘调整策略',
      '保持情绪稳定，避免冲动决策'
    ],
    confidence: Math.floor(Math.random() * 30) + 70,
    timestamp: new Date().toISOString()
  }
  
  res.json({
    code: 0,
    message: 'success',
    data: analysis
  })
})

module.exports = router