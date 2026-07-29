import { getHoldingList, getHoldingSummary, addHolding } from '../../utils/request'

Page({
  data: {
    assets: [],
    liabilities: [],
    summary: null,
    showModal: false,
    typeIndex: 0,
    newRecord: {
      bigType: '资产',
      name: '',
      amount: '',
      monthlyCashflow: '',
      note: ''
    }
  },

  onLoad() {
    this.loadData()
  },

  onPullDownRefresh() {
    this.loadData().then(() => wx.stopPullDownRefresh())
  },

  loadData() {
    return Promise.all([
      getHoldingList(),
      getHoldingSummary()
    ]).then(([list, summary]) => {
      this.setData({
        assets: list.filter(h => h.bigType === '资产'),
        liabilities: list.filter(h => h.bigType === '负债'),
        summary
      })
    }).catch(err => {
      console.error('加载资产数据失败:', err)
      wx.showToast({ title: '加载失败', icon: 'none' })
    })
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
      typeIndex: index,
      'newRecord.bigType': index == 0 ? '资产' : '负债'
    })
  },

  onNameInput(e) {
    this.setData({ 'newRecord.name': e.detail.value })
  },

  onAmountInput(e) {
    this.setData({ 'newRecord.amount': e.detail.value })
  },

  onCashflowInput(e) {
    this.setData({ 'newRecord.monthlyCashflow': e.detail.value })
  },

  onNoteInput(e) {
    this.setData({ 'newRecord.note': e.detail.value })
  },

  submitRecord() {
    const { bigType, name, amount, monthlyCashflow, note } = this.data.newRecord
    if (!name || !amount) {
      wx.showToast({ title: '请填写名称和金额', icon: 'none' })
      return
    }
    addHolding({ bigType, name, amount: parseFloat(amount), monthlyCashflow: parseFloat(monthlyCashflow || 0), note }).then(() => {
      wx.showToast({ title: '添加成功' })
      this.hideAddModal()
      this.setData({
        newRecord: { bigType: '资产', name: '', amount: '', monthlyCashflow: '', note: '' }
      })
      this.loadData()
    }).catch(err => {
      wx.showToast({ title: err || '添加失败', icon: 'none' })
    })
  }
})