const { REDEEM_SUCCESS_KEY } = require('../../utils/constants')
const { formatNumber, formatDateTime } = require('../../utils/format')

Page({
  data: {
    payload: {},
    photoPreviewUrl: '',
    imageError: false,
    playerText: '--',
    pointsText: '0',
    membershipAddedText: '',
    membershipUntilText: ''
  },
  onLoad() {
    const payload = tt.getStorageSync(REDEEM_SUCCESS_KEY) || {}
    this.setData({
      payload,
      photoPreviewUrl: payload.photoPreviewUrl || '',
      playerText: payload.playerNumber ? `${payload.playerNumber}号 ${payload.playerName || ''}` : (payload.playerName || '--'),
      pointsText: formatNumber(payload.points),
      membershipAddedText: payload.membershipAddedDays ? `会员有效期已增加 ${payload.membershipAddedDays} 天` : '',
      membershipUntilText: payload.membershipUntil ? `会员有效期至：${formatDateTime(payload.membershipUntil)}` : '',
      imageError: false
    })
  },
  onImageError() {
    this.setData({ imageError: true })
  },
  goPhotos() {
    tt.navigateTo({ url: '/pages/my-photos/index' })
  },
  goHome() {
    tt.switchTab({ url: '/pages/home/index' })
  }
})
