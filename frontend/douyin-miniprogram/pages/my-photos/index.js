const { ensureLogin } = require('../../utils/auth')
const { buildShareMessage } = require('../../utils/share')
const { getMyPhotos } = require('../../utils/api')
const { formatDateTime } = require('../../utils/format')

Page({
  data: {
    loading: false,
    total: 0,
    items: [],
    membershipText: '暂未开通会员，核销明信片后将增加会员有效期。',
    membershipActive: false
  },
  async onLoad() {
    await ensureLogin()
    await this.loadPhotos()
  },
  async onShow() {
    await this.loadPhotos()
  },
  async loadPhotos() {
    this.setData({ loading: true })
    try {
      const data = await getMyPhotos()
      const items = (data.items || []).map(item => ({
        ...item,
        imageError: false,
        createdAtText: formatDateTime(item.createdAt)
      }))
      const membership = data.membership || {}
      const membershipActive = !!membership.memberActive
      const membershipText = membershipActive && membership.membershipUntil
        ? `会员有效期至：${formatDateTime(membership.membershipUntil)}`
        : '暂未开通会员，核销明信片后将增加会员有效期。'
      this.setData({ total: data.total || items.length, items, membershipText, membershipActive })
    } catch (error) {
      tt.showToast({ title: error.message || '获取写真失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
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
