import { jsonPost } from './http'

export function generateTokens(data: { operatorId: string; playerId: number; photoAssetId: string; points: number; count: number; productSku: string; idempotencyKey: string }) {
  return jsonPost<any>('/api/admin/tokens/generate', data)
}

export async function exportTokens(batchId: string, operatorId: string) {
  const token = localStorage.getItem('adminToken')
  const res = await fetch(`/api/admin/tokens/export?batchId=${batchId}&operatorId=${operatorId}`, {
    headers: { 'X-Admin-Token': token || '' }
  })
  if (!res.ok) {
    let msg = '下载失败'
    try {
      const data = await res.json()
      msg = data.message || msg
    } catch (e) {}
    throw new Error(msg)
  }
  const text = await res.text()
  if (!text.trim()) {
    throw new Error('该批次为空或不存在')
  }
  const blob = new Blob([text], { type: 'text/plain' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `卡库_${batchId}.txt`
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}
