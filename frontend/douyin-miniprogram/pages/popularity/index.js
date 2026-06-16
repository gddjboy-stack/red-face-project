const { ensureLogin } = require('../../utils/auth')
const { getPopularityBoard } = require('../../utils/api')
const { formatNumber } = require('../../utils/format')

Page({
  data: {
    activeTab: 'player',
    roundId: 1,
    loading: false,
    board: {},
    items: []
  },
  async onLoad(options) {
    await ensureLogin()
    const tab = options && options.tab ? options.tab : 'player'
    this.setData({ activeTab: tab })
    await this.loadBoard()
  },
  async onShow() {
    await this.loadBoard()
  },
  onRoundInput(event) {
    this.setData({ roundId: Number(event.detail.value || 1) })
  },
  async switchTab(event) {
    const tab = event.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
    await this.loadBoard()
  },
  async loadBoard() {
    this.setData({ loading: true })
    try {
      const board = await getPopularityBoard(this.data.activeTab, this.data.roundId)
      const items = (board.items || []).map(item => ({
        ...item,
        valueText: formatNumber(item.value)
      }))
      // 合规红线：这里禁止按 value 排序，完全保留后端顺序。
      this.setData({ board, items })
    } catch (error) {
      tt.showToast({ title: error.message || '获取看板失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
