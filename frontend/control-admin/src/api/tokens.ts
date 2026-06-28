import { jsonPost } from './http'

export function generateTokens(data: { operatorId: string; playerId: number; photoAssetId: string; points: number; count: number; productSku: string }) {
  return jsonPost<any>('/api/admin/tokens/generate', data)
}
