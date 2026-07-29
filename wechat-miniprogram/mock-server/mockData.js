const { faker } = require('@faker-js/faker/locale/zh_CN')

module.exports = {
  // 生成股票数据
  generateStocks: (count = 10) => {
    const stocks = Array.from({ length: 30 }, (_, i) => ({
      id: i + 1,
      code: `600${100 + i}`,
      name: ['贵州茅台', '腾讯控股', '美团点评', '宁德时代', '比亚迪', '招商银行', '中国平安', '五粮液', '隆基股份', '美的集团'][i % 10],
      price: (50 + i * 5).toFixed(2),
      pe: (10 + (i % 20)).toFixed(1),
      change: ((i % 10) - 5).toFixed(2),
      marketCap: faker.finance.amount(100, 1000, 0) + '亿',
      _mock: true
    }))
    
    return stocks.slice(0, count)
  },

  // 生成基金数据
  generateFunds: (count = 10) => {
    const types = ['股票型', '混合型', '债券型', '指数型', 'QDII']
    const companies = ['天弘', '易方达', '华夏', '嘉实', '南方', '博时', '广发', '汇添富']
    const categories = ['消费', '科技', '医药', '新能源', '白酒', '互联网', '金融', '地产']
    
    // 生成固定的30条数据
    const allFunds = Array.from({ length: 30 }, (_, i) => ({
      id: i + 1,
      code: `00${8000 + i}`,
      name: `${companies[i % companies.length]}${categories[i % categories.length]}${types[i % types.length]}基金`,
      type: types[i % types.length],
      netValue: (1 + (i % 4)).toFixed(4),
      yield: (5 + (i % 15)).toFixed(2) + '%',
      riskLevel: ['低', '中', '高'][i % 3],
      _mock: true
    }))
    
    return allFunds.slice(0, count)
  },

  // 生成用户数据
  generateUser: () => ({
    userId: faker.string.uuid(),
    username: 'demo',
    nickname: faker.person.lastName() + faker.person.firstName(),
    avatar: 'https://api.dicebear.com/7.x/thumbs/svg?seed=finance',
    token: faker.string.hexadecimal({ length: 32 })
  }),

  // 生成首页概览数据
  generateHomeOverview: () => ({
    netWorth: 2568000,
    passiveIncome: 6800,
    freedomCoverage: 36.8,
    topFunds: Array.from({ length: 3 }, (_, i) => ({
      code: `00${8000 + i}`,
      name: `天弘${['消费', '科技', '医药'][i]}股票型基金`,
      yield: (Math.random() * 15 + 5).toFixed(2) + '%',
      type: '股票型'
    })),
    topStocks: Array.from({ length: 3 }, (_, i) => ({
      code: `600${100 + i}`,
      name: ['贵州茅台', '腾讯控股', '宁德时代'][i],
      price: [1825.00, 345.60, 456.80][i],
      change: [2.5, -1.2, 3.8][i]
    })),
    aiReady: true,
    marketNote: '策略:估值优先、留足安全边际、扩大被动现金流。'
  })
}