const { ensureLogin } = require('../../utils/auth')
const { getSuspicionStatus, submitSuspicion } = require('../../utils/api')

const ERROR_MESSAGES = {
  41001: '该环节暂未开启。',
  41002: '该选手暂不可选择，请刷新后重试。',
  41003: '本轮判断已提交，请等待直播间揭晓。',
  41004: '环节状态已更新，请刷新页面。',
  41000: '提交失败，请稍后重试。'
}

Page({
  data: {
    loading: false,
    submitting: false,
    roundId: null,
    roundName: '',
    open: false,
    submitted: false,
    submittedPlayerId: null,
    selectedPlayerId: null,
    candidates: [],
    errorMessage: '',
    updatedAtText: '--'
  },
  async onLoad(options) {
    const roundId = options && options.roundId ? Number(options.roundId) : null
    this.setData({ roundId: Number.isFinite(roundId) && roundId > 0 ? roundId : null })
    await this.bootstrap()
  },
  async onPullDownRefresh() {
    await this.refreshStatus()
    tt.stopPullDownRefresh()
  },
  async bootstrap() {
    try {
      await ensureLogin()
      await this.refreshStatus()
    } catch (error) {
      tt.showToast({ title: error.message || '登录失败', icon: 'none' })
    }
  },
  async refreshStatus() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const status = await getSuspicionStatus(this.data.roundId)
      const candidates = (status.candidates || []).map(item => ({
        ...item,
        percent: Math.round((Number(item.ratio) || 0) * 100),
        selected: item.playerId === this.data.selectedPlayerId,
        submitted: item.playerId === status.submittedPlayerId
      }))
      this.setData({
        roundId: status.roundId || this.data.roundId,
        roundName: status.roundName || '',
        open: !!status.open,
        submitted: !!status.submitted,
        submittedPlayerId: status.submittedPlayerId || null,
        selectedPlayerId: status.submittedPlayerId || this.data.selectedPlayerId,
        candidates,
        updatedAtText: status.updatedAt ? String(status.updatedAt) : '--'
      })
    } catch (error) {
      this.setData({ errorMessage: ERROR_MESSAGES[error.code] || error.message || '数据获取失败，请稍后重试。' })
    } finally {
      this.setData({ loading: false })
    }
  },
  selectCandidate(event) {
    if (!this.data.open || this.data.submitted || this.data.submitting) return
    const playerId = Number(event.currentTarget.dataset.playerId)
    const candidates = this.data.candidates.map(item => ({ ...item, selected: item.playerId === playerId }))
    this.setData({ selectedPlayerId: playerId, candidates, errorMessage: '' })
  },
  async submitChoice() {
    if (!this.data.open) {
      this.setData({ errorMessage: '该环节暂未开启。' })
      return
    }
    if (this.data.submitted) {
      this.setData({ errorMessage: '本轮判断已提交，请等待直播间揭晓。' })
      return
    }
    if (!this.data.selectedPlayerId) {
      this.setData({ errorMessage: '请先选择一位你认为最可疑的选手。' })
      return
    }
    this.setData({ submitting: true, errorMessage: '' })
    try {
      const result = await submitSuspicion(this.data.roundId, this.data.selectedPlayerId)
      this.setData({
        submitted: true,
        submittedPlayerId: result.submittedPlayerId,
        selectedPlayerId: result.submittedPlayerId,
        errorMessage: result.message || '本轮判断已提交，请等待直播间揭晓。'
      })
      await this.refreshStatus()
    } catch (error) {
      this.setData({ errorMessage: ERROR_MESSAGES[error.code] || error.message || '提交失败，请稍后重试。' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
