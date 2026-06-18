const { ensureLogin } = require('../../utils/auth')
const { getMyPhotos } = require('../../utils/api')
const { formatDateTime } = require('../../utils/format')

Page({
  data: {
    loading: false,
    total: 0,
    membershipActive: false,
    membershipText: '暂未开通会员，核销明信片后将增加会员有效期。'
  },
  async onLoad() {
    await ensureLogin()
    await this.loadMe()
  },
  async onShow() {
    await this.loadMe()
  },
  async loadMe() {
    this.setData({ loading: true })
    try {
      const data = await getMyPhotos()
      const membership = data.membership || {}
      const membershipActive = !!membership.memberActive
      const membershipText = membershipActive && membership.membershipUntil
        ? `会员有效期至：${formatDateTime(membership.membershipUntil)}`
        : '暂未开通会员，核销明信片后将增加会员有效期。'
      this.setData({
        total: data.total || 0,
        membershipActive,
        membershipText
      })
    } catch (error) {
      tt.showToast({ title: error.message || '获取我的信息失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  goPhotos() {
    tt.navigateTo({ url: '/pages/my-photos/index' })
  },
  goRedeem() {
    tt.navigateTo({ url: '/pages/redeem/index' })
  }
})
