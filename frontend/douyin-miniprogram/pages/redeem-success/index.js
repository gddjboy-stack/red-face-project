const { REDEEM_SUCCESS_KEY } = require('../../utils/constants')
const { formatNumber } = require('../../utils/format')

Page({
  data: {
    payload: {},
    photoPreviewUrl: '',
    imageError: false,
    playerText: '--',
    pointsText: '0'
  },
  onLoad() {
    const payload = tt.getStorageSync(REDEEM_SUCCESS_KEY) || {}
    this.setData({
      payload,
      photoPreviewUrl: payload.photoPreviewUrl || '',
      playerText: payload.playerNumber ? `${payload.playerNumber}号 ${payload.playerName || ''}` : (payload.playerName || '--'),
      pointsText: formatNumber(payload.points),
      imageError: false
    })
  },
  onImageError() {
    this.setData({ imageError: true })
  },
  goPhotos() {
    tt.switchTab({ url: '/pages/my-photos/index' })
  },
  goHome() {
    tt.switchTab({ url: '/pages/home/index' })
  }
})
