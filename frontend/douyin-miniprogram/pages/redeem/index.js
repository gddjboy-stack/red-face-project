const { ensureLogin } = require('../../utils/auth')
const { redeemToken } = require('../../utils/api')
const { ERROR_MESSAGES, REDEEM_SUCCESS_KEY } = require('../../utils/constants')

Page({
  data: {
    token: '',
    submitting: false,
    errorMessage: '',
    remainingSeconds: 0
  },
  async onLoad() {
    try {
      await ensureLogin()
    } catch (error) {
      tt.showToast({ title: error.message || '登录失败', icon: 'none' })
    }
  },
  onTokenInput(event) {
    this.setData({ token: String(event.detail.value || '').trim().toUpperCase(), errorMessage: '', remainingSeconds: 0 })
  },
  pasteToken() {
    // 合规红线：只在用户点击后读取剪贴板，禁止页面加载时自动读取。
    tt.getClipboardData({
      success: res => {
        this.setData({ token: String(res.data || '').trim().toUpperCase(), errorMessage: '', remainingSeconds: 0 })
      },
      fail: () => tt.showToast({ title: '读取剪贴板失败', icon: 'none' })
    })
  },
  async submitRedeem() {
    if (this.data.submitting) return
    if (!this.data.token) {
      this.setData({ errorMessage: ERROR_MESSAGES[40001] })
      return
    }
    this.setData({ submitting: true, errorMessage: '', remainingSeconds: 0 })
    try {
      const data = await redeemToken(this.data.token)
      tt.setStorageSync(REDEEM_SUCCESS_KEY, data)
      tt.navigateTo({ url: '/pages/redeem-success/index' })
    } catch (error) {
      const message = ERROR_MESSAGES[error.code] || error.message || '核销失败，请稍后重试。'
      const remainingSeconds = error && error.data && error.data.remainingSeconds ? error.data.remainingSeconds : 0
      this.setData({ errorMessage: message, remainingSeconds })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
