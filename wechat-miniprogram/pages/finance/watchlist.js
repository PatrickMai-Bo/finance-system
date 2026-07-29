import { getWatchlistList, getWatchlistSummary, addWatchlist } from '../../utils/request'

Page({
  data: {
    currentType: 'stock',
    watchlist: [],
    summary: null,
    showModal: false,
    newTypeIndex: 0,
    newRecord: {
      type: 'stock',
      name: '',
      code: '',
      category: '',
      cost: '',
      amount: '',
      targetPrice: '',
      note: ''
    }
  },

  onLoad() {
    this.loadData()
  },

  loadData() {
    const type = this.data.currentType
    Promise.all([
      getWatchlistList({ type }),
      getWatchlistSummary({ type })
    ]).then(([list, summary]) => {
      this.setData({ watchlist: list, summary })
    }).catch(err => {
      console.error('加载自选数据失败:', err)
    })
  },

  switchType(e) {
    const type = e.currentTarget.dataset.type
    this.setData({ currentType: type })
    this.loadData()
  },

  formatNumber(num) {
    if (!num) return '0'
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  },

  showAddModal() {
    this.setData({ showModal: true })
  },

  hideAddModal() {
    this.setData({ showModal: false })
  },

  onTypeChange(e) {
    const index = e.detail.value
    this.setData({
      newTypeIndex: index,
      'newRecord.type': index == 0 ? 'stock' : 'fund'
    })
  },

  onNameInput(e) { this.setData({ 'newRecord.name': e.detail.value }) },
  onCodeInput(e) { this.setData({ 'newRecord.code': e.detail.value }) },
  onCostInput(e) { this.setData({ 'newRecord.cost': e.detail.value }) },
  onAmountInput(e) { this.setData({ 'newRecord.amount': e.detail.value }) },
  onTargetPriceInput(e) { this.setData({ 'newRecord.targetPrice': e.detail.value }) },

  submitRecord() {
    const r = this.data.newRecord
    if (!r.name || !r.code || !r.cost || !r.amount) {
      wx.showToast({ title: '请填写完整信息', icon: 'none' })
      return
    }
    addWatchlist({
      type: r.type,
      name: r.name,
      code: r.code,
      category: r.type === 'stock' ? 'A股' : '股票型',
      cost: parseFloat(r.cost),
      amount: parseFloat(r.amount),
      targetPrice: parseFloat(r.targetPrice || 0),
      note: r.note
    }).then(() => {
      wx.showToast({ title: '添加成功' })
      this.hideAddModal()
      this.setData({
        newRecord: { type: 'stock', name: '', code: '', category: '', cost: '', amount: '', targetPrice: '', note: '' }
      })
      this.loadData()
    }).catch(err => {
      wx.showToast({ title: err || '添加失败', icon: 'none' })
    })
  }
})