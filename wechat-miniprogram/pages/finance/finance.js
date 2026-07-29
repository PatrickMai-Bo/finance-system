Page({
  data: {
    userInfo: {}
  },

  onLoad() {
    this.loadUserInfo()
  },

  loadUserInfo() {
    const userInfo = wx.getStorageSync('userInfo')
    if (userInfo) {
      this.setData({ userInfo })
    }
  },

  goToFinanceOverview() {
    wx.showToast({
      title: '跳转财务概览',
      icon: 'none'
    })
  },

  goToLedger() {
    wx.navigateTo({
      url: '/pages/finance/ledger'
    })
  },

  goToHolding() {
    wx.navigateTo({
      url: '/pages/finance/holding'
    })
  },

  goToWatchlist() {
    wx.navigateTo({
      url: '/pages/finance/watchlist'
    })
  },

  addLedger() {
    wx.showModal({
      title: '添加记账',
      content: '跳转到记账页面',
      success(res) {
        if (res.confirm) {
          wx.navigateTo({
            url: '/pages/finance/ledger'
          })
        }
      }
    })
  },

  addHolding() {
    wx.showModal({
      title: '添加资产',
      content: '跳转到资产页面',
      success(res) {
        if (res.confirm) {
          wx.navigateTo({
            url: '/pages/finance/holding'
          })
        }
      }
    })
  },

  viewSummary() {
    wx.showToast({
      title: '查看汇总报表',
      icon: 'none'
    })
  },

  analyzeFinance() {
    wx.navigateTo({
      url: '/pages/settings/ai'
    })
  }
})