const { ensureLogin } = require('../../utils/auth')
const { getLiveHome } = require('../../utils/api')
const { formatNumber, formatTime } = require('../../utils/format')

Page({
  data: {
    loading: false,
    home: {},
    updatedAtText: '--',
    targetPopularityText: '0',
    teamPopularityText: '0'
  },
  async onLoad() {
    await this.bootstrap()
  },
  async onShow() {
    await this.refreshHome()
  },
  async bootstrap() {
    try {
      await ensureLogin()
      await this.refreshHome()
    } catch (error) {
      tt.showToast({ title: error.message || '登录失败', icon: 'none' })
    }
  },
  async refreshHome() {
    this.setData({ loading: true })
    try {
      const home = await getLiveHome()
      this.setData({
        home,
        updatedAtText: formatTime(home.updatedAt),
        targetPopularityText: (home.currentMode === 'spy' && home.targetId == null) ? '--' : formatNumber(home.targetPopularity),
        teamPopularityText: formatNumber(home.teamPopularity)
      })
    } catch (error) {
      tt.showToast({ title: error.message || '数据获取失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  goRedeem() {
    tt.navigateTo({ url: '/pages/redeem/index' })
  }
})
