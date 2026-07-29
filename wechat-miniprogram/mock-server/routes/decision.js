const express = require('express')
const router = express.Router()
const { faker } = require('@faker-js/faker/locale/zh_CN')

// 思维模型库
router.get('/models', (req, res) => {
  res.json({
    code: 0,
    message: 'success',
    data: [
      { id: 1, name: '逆向思维排雷模型', description: '先想最坏情况，排除可能失败的投资' },
      { id: 2, name: '价值定性模型', description: '判断是否是真资产还是伪资产' },
      { id: 3, name: '能力圈评估', description: '评估是否在自己的认知和能力范围内' },
      { id: 4, name: '替代方案比较', description: '与其他投资机会进行对比' },
      { id: 5, name: '情绪冷却期', description: '设置冷静期避免冲动决策' },
      { id: 6, name: '安全边际计算', description: '格雷厄姆公式估算内在价值' },
      { id: 7, name: '护城河分析', description: '分析企业的竞争优势和壁垒' },
      { id: 8, name: '财务健康检查', description: '检查企业的财务指标和现金流' }
    ]
  })
})

// 五阶框架
router.get('/framework', (req, res) => {
  res.json({
    code: 0,
    message: 'success',
    data: {
      steps: [
        { 
          step: 1, 
          title: '逆向排雷',
          description: '先考虑最坏情况，排除可能失败的投资',
          checklist: ['行业是否在衰退？', '企业护城河是否坚固？', '负债率是否过高？', '管理层是否诚信？']
        },
        { 
          step: 2, 
          title: '价值定性',
          description: '判断是否是真资产还是伪资产',
          checklist: ['是否产生正向现金流？', '是否需要持续投入维护？', '长期价值是否增长？']
        },
        { 
          step: 3, 
          title: '能力圈',
          description: '评估是否在自己的认知和能力范围内',
          checklist: ['是否理解业务模式？', '是否了解行业趋势？', '是否有持续跟踪的时间？']
        },
        { 
          step: 4, 
          title: '替代方案',
          description: '与其他投资机会进行对比',
          checklist: ['有无更好的投资标的？', '风险收益比是否合适？', '资金机会成本？']
        },
        { 
          step: 5, 
          title: '情绪冷却',
          description: '设置冷静期避免冲动决策',
          checklist: ['是否受到市场情绪影响？', '决策是否客观理性？', '是否设置了冷静观察期？']
        }
      ],
      scenarios: [
        '买入新股票',
        '卖出亏损持仓',
        '基金定投调整',
        '大额消费决策',
        '职业发展选择',
        '房产投资决策'
      ]
    }
  })
})

// 决策分析
router.post('/search', (req, res) => {
  const { question, scene } = req.body
  
  if (!question) {
    return res.status(400).json({
      code: 400,
      message: '请输入问题'
    })
  }
  
  const analysis = {
    question: question,
    scene: scene || '通用决策',
    steps: [
      {
        step: 1,
        analysis: `关于"${question}"，最坏情况可能是市场大幅回调、企业基本面恶化、行业政策变化等。建议关注潜在风险点。`,
        recommendation: '设置止损位，分散投资'
      },
      {
        step: 2,
        analysis: `"${question}"涉及的投资是否产生稳定现金流？是否具有长期增值潜力？`,
        recommendation: '评估现金回报率和长期价值'
      },
      {
        step: 3,
        analysis: `是否充分理解"${question}"涉及的行业和企业？是否在自身能力圈内？`,
        recommendation: '建议深入学习或寻求专家意见'
      },
      {
        step: 4,
        analysis: `相比其他投资机会，"${question}"的风险收益比如何？有无更好的替代方案？`,
        recommendation: '对比分析多个备选方案'
      },
      {
        step: 5,
        analysis: `关于"${question}"的决策是否受到市场情绪或他人影响？是否需要冷静期？`,
        recommendation: '建议设置24小时冷静期后再做决定'
      }
    ],
    finalVerdict: '根据五阶分析，建议谨慎评估后决策，优先考虑风险控制。'
  }
  
  res.json({
    code: 0,
    message: 'success',
    data: analysis
  })
})

// 决策日志
router.get('/logs', (req, res) => {
  res.json({
    code: 0,
    message: 'success',
    data: [
      {
        id: 1,
        scene: '股票投资',
        question: '是否应该买入贵州茅台？',
        answer: '五阶分析后决定分批建仓',
        verdict: '通过',
        model: '五阶决策框架',
        createdAt: '2024-03-15 10:30:00'
      },
      {
        id: 2,
        scene: '基金调整',
        question: '是否应该赎回部分科技基金？',
        answer: '建议保留核心仓位，调整比例',
        verdict: '部分通过',
        model: '能力圈评估',
        createdAt: '2024-03-10 14:20:00'
      },
      {
        id: 3,
        scene: '消费决策',
        question: '是否应该换购新车？',
        answer: '建议延迟消费，资金优先用于投资',
        verdict: '否决',
        model: '现金流评估',
        createdAt: '2024-03-05 09:45:00'
      }
    ]
  })
})

module.exports = router