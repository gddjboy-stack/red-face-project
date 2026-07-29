const { ensureLogin } = require('../../utils/auth')
const { buildShareMessage } = require('../../utils/share')
const { getPlayerDetail } = require('../../utils/api')

Page({
  data: {
    playerId: '',
    roundId: '',
    loading: false,
    detail: {},
    photos: []
  },
  async onLoad(options = {}) {
    this.setData({
      playerId: options.playerId || '',
      roundId: options.roundId || ''
    })
    await ensureLogin()
    await this.loadDetail()
  },
  async loadDetail() {
    if (!this.data.playerId) {
      tt.showToast({ title: '缺少选手信息', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const detail = await getPlayerDetail(this.data.playerId, this.data.roundId)
      const photos = (detail.photos || []).map(photo => ({ ...photo, imageError: false }))
      this.setData({
        detail,
        roundId: detail.roundId || this.data.roundId,
        photos
      })
    } catch (error) {
      tt.showToast({ title: error.message || '获取选手详情失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  onImageError(event) {
    const index = event.currentTarget.dataset.index
    const photos = this.data.photos.slice()
    if (photos[index]) {
      photos[index].imageError = true
      this.setData({ photos })
    }
  },
  goRedeem() {
    tt.navigateTo({ url: '/pages/redeem/index' })
  },
  onShareAppMessage() {
    return buildShareMessage()
  }
})
