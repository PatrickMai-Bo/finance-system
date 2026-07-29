import { getUserInfo, login } from '../../utils/request'

Page({
  data: {
    userInfo: {},
    isMock: true,
    lastUpdate: ''
  },

  onLoad() {
    this.loadUserInfo()
    this.setLastUpdate()
  },

  onShow() {
    this.loadUserInfo()
  },

  loadUserInfo() {
    const token = wx.getStorageSync('token')
    if (token) {
      getUserInfo().then(userInfo => {
        this.setData({ userInfo })
      }).catch(() => {
        wx.removeStorageSync('token')
        this.setData({ userInfo: {} })
      })
    }
  },

  setLastUpdate() {
    this.setData({
      lastUpdate: new Date().toLocaleString('zh-CN')
    })
  },

  login() {
    login({ username: 'demo', password: '123456' })
      .then(data => {
        wx.setStorageSync('token', data.token)
        this.setData({ userInfo: data.userInfo })
        wx.showToast({ title: '登录成功' })
      })
      .catch(err => {
        wx.showToast({ title: err, icon: 'none' })
      })
  },

  logout() {
    wx.showModal({
      title: '确认退出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('token')
          this.setData({ userInfo: {} })
          wx.showToast({ title: '已退出登录' })
        }
      }
    })
  },

  navigateTo(e) {
    const url = e.currentTarget.dataset.url
    wx.navigateTo({ url })
  },

  clearCache() {
    wx.showModal({
      title: '清除缓存',
      content: '这将清除所有本地缓存数据，确定继续？',
      success: (res) => {
        if (res.confirm) {
          wx.clearStorage()
          this.setData({ userInfo: {} })
          wx.showToast({ title: '缓存已清除' })
        }
      }
    })
  }
})