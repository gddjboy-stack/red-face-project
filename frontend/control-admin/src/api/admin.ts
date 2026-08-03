import { jsonPost, request } from './http'

export function getAdminHome() {
  return request<any>('/api/admin/live/home')
}

export function getAdminBoard(tab: string, roundId: number) {
  return request<any>(`/api/admin/board?tab=${encodeURIComponent(tab)}&roundId=${roundId}`)
}

export function getCollectState() {
  return request<any>('/api/admin/collect-state')
}

export function setCollectState(data: any) {
  return jsonPost<any>('/api/admin/collect-state', data)
}

export function simulateInject(data: any) {
  return jsonPost<any>('/api/admin/live/simulate', data)
}

export function manualAdjust(data: any) {
  return jsonPost<any>('/api/admin/adjust-coefficient', data)
}

export function distributeTeam(data: any) {
  return jsonPost<any>('/api/admin/team-distribution', data)
}

export function getSuspicionStatus(roundId?: number) {
  const query = roundId ? `?roundId=${roundId}` : ''
  return request<any>(`/api/admin/suspicion/status${query}`)
}

/** C20-3 群投票结果录入（增量累加，负数冲销，带幂等键防连点） */
export function recordGroupVote(data: any) {
  return jsonPost<any>('/api/admin/group-vote/entry', data)
}

/** C20-3 查询指定轮次各选手群投票累计票数 */
export function getGroupVoteSummary(roundId: number) {
  return request<any>(`/api/admin/group-vote/summary?roundId=${roundId}`)
}

/* ==================== C20-9 直播数据录入与场次校准 ====================
   六个端点均为 C20-4A 已实现的后端能力，此前前端从未接入（详见
   collaboration/Manus核查_手册两处功能界面缺失_V1.0.md）。 */

/** 三条来源的水位线现状，供界面展示「上次录入总数」以便运营核对 */
export function getLiveWatermarks() {
  return request<any[]>('/api/admin/live/watermarks')
}

/** 校准操作的官方文案由后端下发，前端不得自行改写（防止「清零」等危险措辞复活） */
export function getCalibrationCopy() {
  return request<Record<string, string>>('/api/admin/live/watermarks/calibrate-copy')
}

/** 预演一次录入，返回将要入账的增量；不写入任何数据 */
export function previewMetricEntry(metricType: string, currentTotal: number) {
  return request<any>(
    `/api/admin/live/metric-entry/preview?metricType=${encodeURIComponent(metricType)}&currentTotal=${currentTotal}`
  )
}

/** 按「中控台当前累计总数」录入；总数小于水位线时后端返回 40910 要求确认是否新场次 */
export function submitMetricEntry(data: any) {
  return jsonPost<any>('/api/admin/live/metric-entry', data)
}

/** 校准全部水位线，用于新一场直播开播。不改变任何选手人气值 */
export function calibrateWatermarks(data: any) {
  return jsonPost<any>('/api/admin/live/watermarks/calibrate', data)
}

/** 撤销最近一次校准，仅在校准后尚未录入时可用 */
export function revokeCalibration(data: any) {
  return jsonPost<any>('/api/admin/live/watermarks/revoke-calibration', data)
}
