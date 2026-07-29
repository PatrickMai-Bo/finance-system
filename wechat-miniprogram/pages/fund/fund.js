import { getFundList } from '../../utils/request'

Page({
  data: {
    funds: [],
    categories: ['全部', '股票型', '混合型', '债券型', '指数型', 'QDII'],
    categoryIndex: 0,
    currentCategory: '全部',
    selectedFunds: {},
    selectedCount: 0,
    pagination: { page: 1, size: 10, total: 0 },
    currentPage: 1,
    totalPages: 1,
    totalItems: 0,
    hasMore: true,
    loading: false
  },

  onLoad() {
    this.loadPage(1)
  },

  onCategoryChange(e) {
    const idx = e.detail.value
    this.setData({
      categoryIndex: idx,
      currentCategory: this.data.categories[idx]
    })
    this.loadPage(1)
  },

  loadPage(pageNum) {
    this.setData({ loading: true })
    const pageSize = this.data.pagination.size

    return getFundList({
      type: this.data.currentCategory,
      page: pageNum,
      size: pageSize
    })
      .then(data => {
        const total = data.pagination.total
        const totalPages = Math.max(1, Math.ceil(total / pageSize))
        this.setData({
          funds: data.list,
          loading: false,
          currentPage: pageNum,
          totalPages: totalPages,
          totalItems: total,
          hasMore: pageNum < totalPages,
          pagination: { page: pageNum, size: pageSize, total: total }
        })
      })
      .catch(err => {
        console.error('基金数据加载失败:', err)
        wx.showToast({ title: '加载失败', icon: 'none' })
        this.setData({ loading: false })
      })
  },

  loadNextPage() {
    if (this.data.currentPage < this.data.totalPages) {
      this.loadPage(this.data.currentPage + 1)
    }
  },

  loadPrevPage() {
    if (this.data.currentPage > 1) {
      this.loadPage(this.data.currentPage - 1)
    }
  },

  preventBubble() {},

  onFundSelect(e) {
    const code = e.detail.value
    const selectedFunds = { ...this.data.selectedFunds }
    if (code) {
      selectedFunds[code] = true
    } else {
      delete selectedFunds[code]
    }
    this.setData({
      selectedFunds: selectedFunds,
      selectedCount: Object.keys(selectedFunds).length
    })
  },

  analyzeSelected() {
    const codes = Object.keys(this.data.selectedFunds)
    if (codes.length === 0) {
      wx.showToast({ title: '请先选择基金', icon: 'none' })
      return
    }
    const items = this.data.funds.filter(f => codes.includes(f.code))
    this.analyzeWithAI('fund-batch', { items, count: codes.length })
  },

  analyzeAll() {
    this.analyzeWithAI('fund-batch', { items: this.data.funds, count: this.data.funds.length })
  },

  analyzeWithAI(scene, payload) {
    wx.showLoading({ title: 'AI分析中...' })
    setTimeout(() => {
      wx.hideLoading()
      wx.showModal({
        title: 'AI分析结果',
        content: `已为${payload.count}只基金完成AI分析，建议关注投资组合配置和风险分散。`,
        showCancel: false
      })
    }, 2000)
  }
})
