import { jsonPost, request } from './http'

/**
 * 录入结果三态。**前端必须区分对待，不可都显示成「操作成功」**：
 * - recorded：已入账，人气已变动
 * - duplicated：这一笔早已入账，不必重来（若提示成功，运营会再录一遍）
 * - needs_confirm：**尚未入账**，等运营看清提示后确认（若提示成功，销量会凭空消失）
 */
export type ManualSalesStatus = 'recorded' | 'duplicated' | 'needs_confirm'

export interface ManualSalesEntryResult {
  status: ManualSalesStatus
  popularityValue: number
  unitPriceCent: number
  quantity: number
  productName: string | null
  playerId: number | null
  playerName: string | null
  /** 本轮该选手该商品冲销后的累计件数 */
  totalQuantityAfter: number
  /** 需二次确认时的原因，直接展示给运营 */
  confirmReason: string | null
}

/** 汇总内层：按「选手 + 商品」一行。件数不跨商品相加。 */
export interface ManualSalesSummaryItem {
  playerId: number | null
  playerName: string | null
  playerNumber: number | null
  merchantCode: string
  productName: string | null
  totalQuantity: number
  totalPopularity: number
  entryCount: number
  latestUnitPriceCent: number
  earliestUnitPriceCent: number
  priceInconsistent: boolean
}

/** 汇总外层：按选手聚合人气合计。 */
export interface ManualSalesPlayerGroup {
  playerId: number | null
  playerName: string | null
  playerNumber: number | null
  products: ManualSalesSummaryItem[]
  totalPopularity: number
  entryCount: number
  hasPriceInconsistency: boolean
}

export interface ManualSalesSummary {
  roundId: number
  players: ManualSalesPlayerGroup[]
  grandTotalPopularity: number
  warnings: string[]
}

export interface ManualSalesEntryPayload {
  roundId: number
  playerId: number
  merchantCode: string
  /** 负数表示冲销纠错 */
  quantity: number
  operatorId: string
  reason: string
  /** 由前端生成，防连点重复提交 */
  idempotencyKey: string
  /** 运营看清软重复/异常量提示后置 true 才真正入账 */
  confirmed: boolean
}

export function recordManualSales(data: ManualSalesEntryPayload) {
  return jsonPost<ManualSalesEntryResult>('/api/admin/sales/manual-entry', data)
}

export function getManualSalesSummary(roundId: number) {
  return request<ManualSalesSummary>(`/api/admin/sales/manual-summary?roundId=${roundId}`)
}

/**
 * 生成幂等键。**只能防「同一次点击的重复提交」**，防不住「运营以为没成功、手动再点一次」
 * ——那是一次新点击，会得到新键而正常入账。后者由服务端的软重复提示兜底。
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}
