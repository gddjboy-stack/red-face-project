import { jsonPost, jsonPut, request } from './http'

export function listPlayers() {
  return request<any[]>('/api/admin/players')
}

export function createPlayer(data: any) {
  return jsonPost<any>('/api/admin/players', data)
}

export function listTeams() {
  return request<any[]>('/api/admin/teams')
}

export function createTeam(data: any) {
  return jsonPost<any>('/api/admin/teams', data)
}

export function listRounds() {
  return request<any[]>('/api/admin/rounds')
}

export function createRound(data: any) {
  return jsonPost<any>('/api/admin/rounds', data)
}

export function updateRoundStatus(roundId: number, data: any) {
  return jsonPut<any>(`/api/admin/rounds/${roundId}/status`, data)
}

export function listPlayerRounds(roundId: number) {
  return request<any[]>(`/api/admin/player-round?roundId=${roundId}`)
}

export function savePlayerRound(data: any) {
  return jsonPost<any>('/api/admin/player-round', data)
}
