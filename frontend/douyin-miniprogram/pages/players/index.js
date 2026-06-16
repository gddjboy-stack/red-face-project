const { ensureLogin } = require('../../utils/auth')
const { getPopularityBoard } = require('../../utils/api')
const { formatNumber } = require('../../utils/format')

Page({
  data: {
    roundId: 1,
    loading: false,
    items: []
  },
  async onLoad() {
    await ensureLogin()
    await this.loadPlayers()
  },
  onRoundInput(event) {
    this.setData({ roundId: Number(event.detail.value || 1) })
  },
  async loadPlayers() {
    this.setData({ loading: true })
    try {
      const board = await getPopularityBoard('player', this.data.roundId)
      const items = (board.items || []).map(item => ({ ...item, valueText: formatNumber(item.value) }))
      // 选手 Tab 按 Claude 方案 B 复用 API-2 player 顺序，不实现完整选手详情。
      this.setData({ items })
    } catch (error) {
      tt.showToast({ title: error.message || '获取选手失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
