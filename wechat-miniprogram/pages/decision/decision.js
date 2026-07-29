import { getDecisionFramework, searchDecision, getDecisionLogs } from '../../utils/request'

Page({
  data: {
    question: '',
    scenes: ['股票投资', '基金调整', '消费决策', '职业发展', '房产投资', '通用决策'],
    sceneIndex: 0,
    framework: null,
    result: null,
    logs: [],
    analyzing: false
  },

  onLoad() {
    this.loadFramework()
    this.loadLogs()
  },

  loadFramework() {
    getDecisionFramework().then(data => {
      this.setData({ framework: data })
    }).catch(err => {
      console.error('加载框架失败:', err)
    })
  },

  loadLogs() {
    getDecisionLogs().then(data => {
      this.setData({ logs: data })
    }).catch(err => {
      console.error('加载日志失败:', err)
    })
  },

  onQuestionInput(e) {
    this.setData({ question: e.detail.value })
  },

  onSceneChange(e) {
    this.setData({ sceneIndex: e.detail.value })
  },

  analyze() {
    if (!this.data.question) {
      wx.showToast({ title: '请输入问题', icon: 'none' })
      return
    }
    this.setData({ analyzing: true })
    searchDecision({
      question: this.data.question,
      scene: this.data.scenes[this.data.sceneIndex]
    }).then(data => {
      this.setData({ result: data, analyzing: false })
      wx.showToast({ title: '分析完成' })
      this.loadLogs()
    }).catch(err => {
      this.setData({ analyzing: false })
      wx.showToast({ title: err || '分析失败', icon: 'none' })
    })
  }
})