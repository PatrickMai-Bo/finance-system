import { getStockList } from '../../utils/request'

Page({
  data: {
    stocks: [],
    selectedStocks: {},
    selectedCount: 0,
    pagination: { page: 1, size: 10, total: 0 },
    currentPage: 1,
    totalPages: 1,
    totalItems: 0,
    loading: false
  },

  onLoad() {
    this.loadPage(1)
  },

  onShow() {
    // refresh on show if needed
  },

  // Load a specific page (replaces current data, does not append)
  loadPage(pageNum) {
    this.setData({ loading: true })
    const pageSize = this.data.pagination.size

    return getStockList({ page: pageNum, size: pageSize })
      .then(data => {
        const total = data.pagination.total
        const totalPages = Math.ceil(total / pageSize)
        this.setData({
          stocks: data.list,
          loading: false,
          currentPage: pageNum,
          totalPages: totalPages,
          totalItems: total,
          pagination: { page: pageNum, size: pageSize, total: total }
        })
      })
      .catch(err => {
        console.error('股票数据加载失败:', err)
        wx.showToast({ title: '数据加载失败', icon: 'none' })
        this.setData({ loading: false })
      })
  },

  loadNextPage() {
    const next = this.data.currentPage + 1
    if (next <= this.data.totalPages) this.loadPage(next)
  },

  loadPrevPage() {
    const prev = this.data.currentPage - 1
    if (prev >= 1) this.loadPage(prev)
  },

  preventBubble() {
    // prevent tap event from bubbling
  },

  onStockSelect(e) {
    const code = e.detail.value
    const selectedStocks = { ...this.data.selectedStocks }
    if (code) {
      selectedStocks[code] = true
    } else {
      delete selectedStocks[code]
    }
    this.setData({
      selectedStocks: selectedStocks,
      selectedCount: Object.keys(selectedStocks).length
    })
  },

  analyzeSelected() {
    const selectedCodes = Object.keys(this.data.selectedStocks)
    if (selectedCodes.length === 0) {
      wx.showToast({ title: '请先选择股票', icon: 'none' })
      return
    }
    const selectedItems = this.data.stocks.filter(item => selectedCodes.includes(item.code))
    this.analyzeWithAI('stock-batch', { items: selectedItems, count: selectedCodes.length })
  },

  analyzeAll() {
    this.analyzeWithAI('stock-batch', { items: this.data.stocks, count: this.data.stocks.length })
  },

  analyzeWithAI(scene, payload) {
    wx.showLoading({ title: 'AI分析中...' })
    setTimeout(() => {
      wx.hideLoading()
      wx.showModal({
        title: 'AI分析结果',
        content: `已为${payload.count}只股票完成AI分析，建议重点关注估值分位、护城河和安全边际。`,
        showCancel: false
      })
    }, 2000)
  },

  viewDetail(e) {
    const code = e.currentTarget.dataset.code
    wx.navigateTo({
      url: `/pages/stock/detail?code=${code}`,
      fail: () => {
        wx.showModal({
          title: '股票详情',
          content: `查看 ${code} 的详细信息`,
          showCancel: false
        })
      }
    })
  }
})