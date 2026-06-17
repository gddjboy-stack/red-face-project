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
  return jsonPost<any>('/api/admin/popularity/manual-adjust', data)
}

export function distributeTeam(data: any) {
  return jsonPost<any>('/api/admin/team-distribution', data)
}

export function getSuspicionStatus(roundId?: number) {
  const query = roundId ? `?roundId=${roundId}` : ''
  return request<any>(`/api/admin/suspicion/status${query}`)
}
