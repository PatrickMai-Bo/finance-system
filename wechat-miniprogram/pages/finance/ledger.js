import { getLedgerList, addLedger, getLedgerSummary } from '../../utils/request'

Page({
  data: {
    ledgerList: [],
    summary: null,
    showModal: false,
    typeIndex: 0,
    categoryIndex: 0,
    incomeCategories: ['工资', '奖金', '投资收益', '房租', '分红', '其他'],
    expenseCategories: ['餐饮', '交通', '住房', '教育', '娱乐', '医疗', '购物', '其他'],
    newRecord: {
      date: new Date().toISOString().split('T')[0],
      type: 'income',
      amount: '',
      description: '',
      category: 'salary'
    }
  },

  onLoad() {
    this.loadData()
  },

  loadData() {
    getLedgerList().then(data => {
      this.setData({ ledgerList: data })
    }).catch(err => {
      console.error('加载记账数据失败:', err)
    })

    getLedgerSummary().then(data => {
      this.setData({ summary: data })
    }).catch(err => {
      console.error('加载汇总数据失败:', err)
    })
  },

  getCategoryName(category) {
    const map = {
      'salary': '工资',
      'bonus': '奖金',
      'investment': '投资',
      'rent': '房租',
      'dividend': '分红',
      'food': '餐饮',
      'transport': '交通',
      'housing': '住房',
      'education': '教育',
      'entertainment': '娱乐',
      'health': '医疗',
      'other': '其他'
    }
    return map[category] || category
  },

  showAddModal() {
    this.setData({ showModal: true })
  },

  hideAddModal() {
    this.setData({ showModal: false })
  },

  onDateChange(e) {
    this.setData({
      'newRecord.date': e.detail.value
    })
  },

  onTypeChange(e) {
    const index = e.detail.value
    this.setData({
      typeIndex: index,
      'newRecord.type': index == 0 ? 'income' : 'expense',
      categoryIndex: 0,
      'newRecord.category': index == 0 ? 'salary' : 'food'
    })
  },

  onCategoryChange(e) {
    const index = e.detail.value
    const categories = this.data.typeIndex == 0 ? 
      ['salary', 'bonus', 'investment', 'rent', 'dividend', 'other'] :
      ['food', 'transport', 'housing', 'education', 'entertainment', 'health', 'shopping', 'other']
    
    this.setData({
      categoryIndex: index,
      'newRecord.category': categories[index]
    })
  },

  onAmountInput(e) {
    this.setData({
      'newRecord.amount': e.detail.value
    })
  },

  onDescriptionInput(e) {
    this.setData({
      'newRecord.description': e.detail.value
    })
  },

  submitRecord() {
    const { date, type, amount, description, category } = this.data.newRecord
    
    if (!date || !type || !amount || !description) {
      wx.showToast({
        title: '请填写完整信息',
        icon: 'none'
      })
      return
    }

    addLedger({ date, type, amount, description, category }).then(() => {
      wx.showToast({
        title: '添加成功'
      })
      this.hideAddModal()
      this.loadData()
      
      // 重置表单
      this.setData({
        'newRecord.amount': '',
        'newRecord.description': '',
        categoryIndex: 0,
        'newRecord.category': this.data.typeIndex == 0 ? 'salary' : 'food'
      })
    }).catch(err => {
      wx.showToast({
        title: err || '添加失败',
        icon: 'none'
      })
    })
  }
})