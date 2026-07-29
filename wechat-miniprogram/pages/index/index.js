import { getHomeOverview, getUserInfo, getDecisionModels } from '../../utils/request'

Page({
  data: {
    userInfo: {},
    homeData: {},
    currentDate: '',
    loading: false
  },

  onLoad() {
    this.setCurrentDate()
    this.loadData()
  },

  onPullDownRefresh() {
    this.loadData().then(() => {
      wx.stopPullDownRefresh()
    })
  },

  setCurrentDate() {
    const date = new Date()
    const options = { 
      year: 'numeric', 
      month: 'long', 
      day: 'numeric',
      weekday: 'long'
    }
    this.setData({
      currentDate: date.toLocaleDateString('zh-CN', options)
    })
  },

  loadData() {
    this.setData({ loading: true })
    
    return Promise.all([
      getHomeOverview(),
      getUserInfo(),
      getDecisionModels()
    ]).then(([homeData, userInfo, decisionModels]) => {
      this.setData({
        homeData,
        userInfo,
        decisionModels,
        loading: false
      })
    }).catch(err => {
      console.error('数据加载失败:', err)
      wx.showToast({ title: '数据加载失败', icon: 'none' })
      this.setData({ loading: false })
    })
  },

  formatNumber(num) {
    if (!num) return '0'
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  }
})