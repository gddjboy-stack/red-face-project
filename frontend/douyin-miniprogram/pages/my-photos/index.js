const { ensureLogin } = require('../../utils/auth')
const { getMyPhotos } = require('../../utils/api')
const { formatDateTime } = require('../../utils/format')

Page({
  data: {
    loading: false,
    total: 0,
    items: []
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
      this.setData({ total: data.total || items.length, items })
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
  }
})
