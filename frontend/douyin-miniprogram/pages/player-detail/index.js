const { ensureLogin } = require('../../utils/auth')
const { getPlayerDetail } = require('../../utils/api')
const { formatNumber } = require('../../utils/format')

Page({
  data: {
    playerId: '',
    roundId: '',
    loading: false,
    detail: {},
    popularityText: '0',
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
        popularityText: formatNumber(detail.popularityValue),
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
  }
})
