import { jsonPost, jsonPut, multipartPost, request } from './http'

export function listPhotos(params: { playerId?: number | null; status?: string | null } = {}) {
  const query = new URLSearchParams()
  if (params.playerId) query.set('playerId', String(params.playerId))
  if (params.status) query.set('status', params.status)
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return request<any[]>(`/api/admin/photos${suffix}`)
}

export function uploadPhoto(data: { operatorId: string; playerId: number; isCover: boolean; sortOrder: number; file: File }) {
  const form = new FormData()
  form.append('operatorId', data.operatorId)
  form.append('playerId', String(data.playerId))
  form.append('isCover', String(data.isCover))
  form.append('sortOrder', String(data.sortOrder))
  form.append('file', data.file)
  return multipartPost<any>('/api/admin/photos/upload', form)
}

export function replacePhoto(assetId: string, data: { operatorId: string; file: File }) {
  const form = new FormData()
  form.append('operatorId', data.operatorId)
  form.append('file', data.file)
  return multipartPost<any>(`/api/admin/photos/${assetId}/replace`, form)
}

export function updatePhoto(assetId: string, data: any) {
  return jsonPut<any>(`/api/admin/photos/${assetId}`, data)
}

export function updatePhotoStatus(assetId: string, data: { operatorId: string; status: string }) {
  return jsonPut<any>(`/api/admin/photos/${assetId}/status`, data)
}

export function setPhotoCover(assetId: string, data: { operatorId: string }) {
  return jsonPost<any>(`/api/admin/photos/${assetId}/cover`, data)
}
