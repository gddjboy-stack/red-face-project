const { ensureLogin } = require('../../utils/auth')
const { getSuspicionStatus, submitSuspicion } = require('../../utils/api')
const { buildShareMessage } = require('../../utils/share')

const ERROR_MESSAGES = {
  41001: '该环节暂未开启。',
  41002: '该选手暂不可选择，请刷新后重试。',
  41003: '本轮判断已提交，请等待直播间揭晓。',
  41004: '环节状态已更新，请刷新页面。',
  41000: '提交失败，请稍后重试。'
}

Page({
  onShareAppMessage() {
    return buildShareMessage()
  },
  data: {
    loading: false,
    submitting: false,
    roundId: null,
    roundName: '',
    open: false,
    submitted: false,
    votedIds: [],
    selectedIds: [],
    candidates: [],
    groupedCandidates: [],
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
      const candidates = status.candidates || []
      const votedIds = status.submittedPlayerIds || []
      
      const grouped = []
      const teamMap = {}
      candidates.forEach(c => {
        if (!teamMap[c.teamId]) {
          teamMap[c.teamId] = { teamId: c.teamId, teamName: c.teamName, members: [] }
          grouped.push(teamMap[c.teamId])
        }
        teamMap[c.teamId].members.push({
          ...c,
          percent: Math.round((Number(c.ratio) || 0) * 100)
        })
      })

      this.setData({
        roundId: status.roundId || this.data.roundId,
        roundName: status.roundName || '',
        open: !!status.open,
        submitted: !!status.submitted,
        votedIds,
        candidates,
        groupedCandidates: grouped,
        selectedIds: [],
        updatedAtText: status.updatedAt ? String(status.updatedAt) : '--'
      })
    } catch (error) {
      this.setData({ errorMessage: ERROR_MESSAGES[error.code] || error.message || '数据获取失败，请稍后重试。' })
    } finally {
      this.setData({ loading: false })
    }
  },
  selectCandidate(event) {
    if (!this.data.open || this.data.submitting) return
    const playerId = Number(event.currentTarget.dataset.playerId)
    if (this.data.votedIds.includes(playerId)) return // 已投过，置灰不可点
    
    let selected = [...this.data.selectedIds]
    const idx = selected.indexOf(playerId)
    if (idx > -1) {
      selected.splice(idx, 1)
    } else {
      selected.push(playerId)
    }
    this.setData({ selectedIds: selected, errorMessage: '' })
  },
  async submitChoice() {
    if (!this.data.open) {
      this.setData({ errorMessage: '该环节暂未开启。' })
      return
    }
    if (this.data.selectedIds.length === 0) {
      this.setData({ errorMessage: '请先选择选手。' })
      return
    }
    this.setData({ submitting: true, errorMessage: '' })
    try {
      const result = await submitSuspicion(this.data.roundId, this.data.selectedIds)
      const accepted = result.accepted || []
      const duplicated = result.duplicated || []
      
      const msg = `成功提交 ${accepted.length} 人${duplicated.length > 0 ? `，${duplicated.length} 人此前已投过` : ''}。`
      
      // 合并 votedIds
      const newVotedIds = Array.from(new Set([...this.data.votedIds, ...accepted, ...duplicated]))
      
      this.setData({
        submitted: true,
        votedIds: newVotedIds,
        selectedIds: [],
        errorMessage: msg
      })
      tt.showToast({ title: msg, icon: 'none' })
      await this.refreshStatus()
    } catch (error) {
      this.setData({ errorMessage: ERROR_MESSAGES[error.code] || error.message || '提交失败，请稍后重试。' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
