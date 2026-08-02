import { jsonPost, multipartPost, request } from './http'

/** 按选手汇总的一行。件数与人气值必须并列显示：只看人气值无法区分「卖得多」与「单价配错」。 */
export interface PlayerOrderSummary {
  merchantCode: string
  playerName: string | null
  playerId: number | null
  validRows: number
  quantity: number
  popularityValue: number
  aftersaleRows: number
  aftersaleExposure: number
  unitPriceCent: number | null
}

export interface OrderRow {
  rowNumber: number
  subOrderNo: string
  mainOrderNo: string
  merchantCode: string
  playerId: number | null
  playerName: string | null
  quantity: number
  unitPriceCent: number | null
  popularityValue: number
  orderStatus: string
  aftersaleStatus: string
  validity: string
  invalidReason: string | null
  inAftersale: boolean
  unknownOrderStatus: boolean
}

export interface OrderImportPreview {
  /** 前置检查（空跑）时为 null：空跑不得留下可误点的确认入口 */
  previewToken: string | null
  totalRows: number
  validRows: number
  invalidRows: number
  unattributedRows: number
  duplicateRows: number
  aftersaleRows: number
  aftersaleExposure: number
  unknownStatusRows: number
  totalQuantity: number
  totalPopularity: number
  byPlayer: Record<string, number>
  byPlayerDetail: PlayerOrderSummary[]
  unattributedSubOrderNos: string[]
  blockedByUnattributed: boolean
  blockReason: string | null
  blockingErrors: string[]
  warnings: string[]
  rows: OrderRow[]
}

/** 上传并预览，生成一次性预览令牌。 */
export function previewOrderImport(file: File, roundId: number | null) {
  const form = new FormData()
  form.append('file', file)
  if (roundId) form.append('roundId', String(roundId))
  return multipartPost<OrderImportPreview>('/api/admin/orders/preview', form)
}

/**
 * C20-4C 前置检查（赛前空跑）：只校验，不落库、不产生令牌。
 * 与 preview 走同一解析路径，避免「空跑通过、正式导入被拦」。
 */
export function preflightOrderImport(file: File, roundId: number | null) {
  const form = new FormData()
  form.append('file', file)
  if (roundId) form.append('roundId', String(roundId))
  return multipartPost<OrderImportPreview>('/api/admin/orders/preflight', form)
}

/** 凭令牌确认入账。存在未归属行时后端返回 409/40920 硬阻断。 */
export function confirmOrderImport(data: { previewToken: string; operatorId: string }) {
  return jsonPost<any>('/api/admin/orders/confirm', data)
}

/**
 * C20-4C 确认入账并显式排除未归属订单。
 * 必须逐笔传子订单号且与预览完全一致，并填写原因；后端会在入账前写操作日志。
 */
export function confirmOrderImportWithOverride(data: {
  previewToken: string
  operatorId: string
  overrideSubOrderNos: string[]
  overrideReason: string
}) {
  return jsonPost<any>('/api/admin/orders/confirm-override', data)
}

export function listProductPrices() {
  return request<any[]>('/api/admin/orders/prices')
}

export function saveProductPrice(data: {
  merchantCode: string
  productName: string
  unitPriceYuan: string
  status: string
  operatorId: string
}) {
  return jsonPost<any>('/api/admin/orders/prices', data)
}
