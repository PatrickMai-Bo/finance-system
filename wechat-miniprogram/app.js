App({
  onLaunch() {
    console.log('理财决策系统小程序启动')
    
    // 检查登录状态
    const token = wx.getStorageSync('token')
    if (token) {
      console.log('用户已登录')
    } else {
      console.log('用户未登录')
    }

    // 检查网络状态
    wx.getNetworkType({
      success: (res) => {
        console.log('网络类型:', res.networkType)
      }
    })
  },

  onShow() {
    console.log('小程序显示')
  },

  onHide() {
    console.log('小程序隐藏')
  },

  onError(msg) {
    console.error('小程序错误:', msg)
  },

  globalData: {
    userInfo: null,
    isMockMode: true
  }
})