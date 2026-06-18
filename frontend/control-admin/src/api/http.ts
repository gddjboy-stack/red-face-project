export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const baseURL = import.meta.env.VITE_API_BASE_URL || ''

// 场控后台 Admin 口令（X-Admin-Token）本地存储 key。
// 口令由运营首次输入、存于本地，绝不硬编码进源码或构建产物（C-ADMIN-FE-01）。
export const ADMIN_TOKEN_KEY = 'adminToken'

/** 当后端返回 401（口令无效/缺失）时触发，由 UI 层注册：清旧值并提示运营重新输入。 */
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export function getAdminToken(): string {
  return localStorage.getItem(ADMIN_TOKEN_KEY) || ''
}

export function setAdminToken(token: string): void {
  localStorage.setItem(ADMIN_TOKEN_KEY, token.trim())
}

export function clearAdminToken(): void {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
}

export class UnauthorizedError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'UnauthorizedError'
  }
}

export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {})
  }

  // 仅对 /api/admin/** 注入 Admin 口令；用户端等其它路径不带。
  if (url.startsWith('/api/admin')) {
    const token = getAdminToken()
    if (token) {
      headers['X-Admin-Token'] = token
    }
  }

  const response = await fetch(`${baseURL}${url}`, {
    ...options,
    headers
  })

  // 后端 Admin 鉴权失败：清掉本地旧口令并通知 UI 让运营重输。
  if (response.status === 401) {
    clearAdminToken()
    if (unauthorizedHandler) {
      unauthorizedHandler()
    }
    throw new UnauthorizedError('管理口令无效，请重新输入')
  }

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    throw new Error(`接口返回异常（状态 ${response.status}），请检查后端服务或代理是否正常`)
  }

  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || `请求失败：${response.status}`)
  }
  return payload.data
}

export function jsonPost<T>(url: string, data: unknown): Promise<T> {
  return request<T>(url, {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function jsonPut<T>(url: string, data: unknown): Promise<T> {
  return request<T>(url, {
    method: 'PUT',
    body: JSON.stringify(data)
  })
}
