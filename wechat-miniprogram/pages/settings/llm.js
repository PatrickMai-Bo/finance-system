import { getLLMConfigs, addLLMConfig, activateLLM, testLLM, deleteLLMConfig } from '../../utils/request'

Page({
  data: {
    configs: [],
    showModal: false,
    testingId: null,
    newConfig: {
      name: '',
      baseUrl: '',
      model: '',
      apiKey: '',
      enableSearch: false
    }
  },

  onLoad() {
    this.loadConfigs()
  },

  loadConfigs() {
    getLLMConfigs().then(data => {
      this.setData({ configs: data })
    }).catch(err => {
      console.error('加载配置失败:', err)
    })
  },

  showAddModal() {
    this.setData({ showModal: true })
  },

  hideAddModal() {
    this.setData({ showModal: false })
  },

  onNameInput(e) { this.setData({ 'newConfig.name': e.detail.value }) },
  onBaseUrlInput(e) { this.setData({ 'newConfig.baseUrl': e.detail.value }) },
  onModelInput(e) { this.setData({ 'newConfig.model': e.detail.value }) },
  onApiKeyInput(e) { this.setData({ 'newConfig.apiKey': e.detail.value }) },
  onSearchChange(e) { this.setData({ 'newConfig.enableSearch': e.detail.value }) },

  submitConfig() {
    const c = this.data.newConfig
    if (!c.name || !c.baseUrl || !c.model || !c.apiKey) {
      wx.showToast({ title: '请填写完整', icon: 'none' })
      return
    }
    addLLMConfig(c).then(() => {
      wx.showToast({ title: '添加成功' })
      this.hideAddModal()
      this.setData({ newConfig: { name: '', baseUrl: '', model: '', apiKey: '', enableSearch: false } })
      this.loadConfigs()
    }).catch(err => {
      wx.showToast({ title: err || '添加失败', icon: 'none' })
    })
  },

  activateConfig(e) {
    const id = e.currentTarget.dataset.id
    activateLLM(id).then(() => {
      wx.showToast({ title: '激活成功' })
      this.loadConfigs()
    }).catch(err => {
      wx.showToast({ title: err || '激活失败', icon: 'none' })
    })
  },

  testConfig(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ testingId: id })
    testLLM(id).then(data => {
      this.setData({ testingId: null })
      wx.showModal({
        title: '测试结果',
        content: `连接成功！延迟: ${data.latency}ms`,
        showCancel: false
      })
    }).catch(err => {
      this.setData({ testingId: null })
      wx.showModal({
        title: '测试失败',
        content: err || '连接失败',
        showCancel: false
      })
    })
  },

  deleteConfig(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认删除',
      content: '确定删除该模型配置？',
      success: (res) => {
        if (res.confirm) {
          deleteLLMConfig(id).then(() => {
            wx.showToast({ title: '删除成功' })
            this.loadConfigs()
          }).catch(err => {
            wx.showToast({ title: err || '删除失败', icon: 'none' })
          })
        }
      }
    })
  }
})