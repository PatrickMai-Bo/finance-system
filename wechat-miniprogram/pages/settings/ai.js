import { analyzeAI } from '../../utils/request'

Page({
  data: {
    scenes: ['stock', 'fund', 'finance', 'holding', 'watchlist', 'decision'],
    currentScene: 'stock',
    input: '',
    result: null,
    analyzing: false
  },

  switchScene(e) {
    this.setData({ currentScene: e.currentTarget.dataset.scene })
  },

  onInput(e) {
    this.setData({ input: e.detail.value })
  },

  analyze() {
    if (!this.data.input) {
      wx.showToast({ title: '请输入分析内容', icon: 'none' })
      return
    }
    this.setData({ analyzing: true })
    analyzeAI({
      scene: this.data.currentScene,
      payload: { question: this.data.input }
    }).then(data => {
      this.setData({ result: data, analyzing: false })
      wx.showToast({ title: '分析完成' })
    }).catch(err => {
      this.setData({ analyzing: false })
      wx.showToast({ title: err || '分析失败', icon: 'none' })
    })
  }
})