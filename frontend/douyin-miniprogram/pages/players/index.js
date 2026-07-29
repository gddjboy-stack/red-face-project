const { ensureLogin } = require('../../utils/auth')
const { buildShareMessage } = require('../../utils/share')
const { getPlayers } = require('../../utils/api')

Page({
  data: {
    roundId: '',
    roundName: '',
    loading: false,
    items: []
  },
  async onLoad() {
    await ensureLogin()
    await this.loadPlayers()
  },
  onRoundInput(event) {
    this.setData({ roundId: event.detail.value || '' })
  },
  async loadPlayers() {
    this.setData({ loading: true })
    try {
      const data = await getPlayers(this.data.roundId)
      const items = (data.items || []).map(item => ({
        ...item,
        imageError: false
      }))
      this.setData({
        roundId: data.roundId || this.data.roundId || '',
        roundName: data.roundName || '当前轮次',
        items
      })
    } catch (error) {
      tt.showToast({ title: error.message || '获取选手失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  goDetail(event) {
    const playerId = event.currentTarget.dataset.playerId
    if (!playerId) {
      return
    }
    const roundQuery = this.data.roundId ? `&roundId=${encodeURIComponent(this.data.roundId)}` : ''
    tt.navigateTo({ url: `/pages/player-detail/index?playerId=${encodeURIComponent(playerId)}${roundQuery}` })
  },
  onImageError(event) {
    const index = event.currentTarget.dataset.index
    const items = this.data.items.slice()
    if (items[index]) {
      items[index].imageError = true
      this.setData({ items })
    }
  },
  onShareAppMessage() {
    return buildShareMessage()
  }
})
