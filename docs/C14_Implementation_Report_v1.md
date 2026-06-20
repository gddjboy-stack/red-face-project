# C14 退款实施报告与联调清单

> 实施人: Manus
> 日期: 2026.06.20
> 状态: **开发完成，待 Claude 复审**

## 一、实施概述

C14 退款卡已严格按照 Claude 在 6/20 裁定的《Claude 裁定 — C14 退款方案确认》以及“全部采纳方案 A”的指示完成编码。
本次实施**绝对未碰 C5 核销主干与 C2 人气引擎核心**，且**未修改前端、未改变现有鉴权体系**，仅通过新增后台接口和复用引擎实现了安全的退款回滚。

## 二、关键实现核对（对照 Claude 裁定）

| 裁定要求 | 实现说明 | 核对状态 |
|---|---|---|
| **主键用 token_id** | 新增 `RefundRequest` DTO，入参为 `token`，完全未引入 `order_id`，未推翻 C12 契约。[1] | ✅ |
| **加 refunded 态防重复** | 在 `db_schema.sql` 增加注释，`TokenMapper` 新增 `markRefundedIfUsed` 原子抢占，只有 `used` 能变 `refunded`，影响行数 ≠ 1 直接抛错回滚。[2] | ✅ |
| **负数容忍** | 调 `applyChange` 传负数 `-points`，底层 `StatsMapper` 如实记账（可为负），展示层 `computeScore` 兜底钳为 0。 | ✅ |
| **不碰会员与写真** | `RefundService` 未引入 `UserMembershipService` 或 `UserPhotoCollectionMapper` 的扣减逻辑，虚拟权益如约保留。[3] | ✅ |
| **跨轮次精确回滚** | 新增 `findRoundIdByIdempotencyKey`，把人气精确扣回核销当时的轮次，避免跨轮退款导致账面对不上。[4] | ✅ |

## 三、代码结构变更

1. **Service 层**：
   - 新增 `RefundService`，实现完整的原子抢占、读取原卡密信息、调 `applyChange` 回滚人气、写 `operations_log` 审计闭环。
   - 新增 `RefundException` 统一抛出 `not_refundable` / `invalid_token` 业务码。
2. **API 层**：
   - 新增 `RefundController`，暴露 `POST /api/admin/refund`，挂载于现有的 `AdminAuthInterceptor` 下，通过 `X-Admin-Token` 鉴权。
3. **DTO 层**：
   - 新增 `RefundRequest`、`RefundResult`、`RefundErrorData`。
4. **Mapper 层**：
   - `TokenMapper` 增加 `@Update` `markRefundedIfUsed`。
   - `PopularityLedgerMapper` 增加 `@Select` `findRoundIdByIdempotencyKey`。

## 四、测试与回归情况

- **专项测试（11 个用例）**：
  - `RefundServiceC14Test` 覆盖了：正常退款、**同卡重复退款被拒**、退未核销卡被拒、负数容忍钳 0、跨轮次精确扣回、不动会员与写真等 8 个核心业务用例。
  - `RefundControllerC14Test` 覆盖了：无 Admin Token 401 拦截、带 Token 正常退款、退款错误态映射 42002 等 3 个 API 行为。
- **全量回归**：
  - 执行 `mvn test`，全量 96 个测试（原 85 + 新增 11）**100% 通过**，证明 C14 未破坏任何已有逻辑。

## 五、C14 联调清单（供测试/客服使用）

以下操作需在后台配备了有效 `X-Admin-Token` 的情况下使用 API 工具（如 Postman / cURL）或未来的场控后台前端进行：

### 1. 正常退款
- **操作**：找一张已核销过的卡密（`status='used'`），发送 `POST /api/admin/refund`，请求体 `{"token":"RFZJ-XXXX-XXXX-XXXX", "operatorId":"admin", "reason":"用户退款"}`。
- **预期**：返回 `code: 0`，`data.result.refundedPoints` 显示扣减数值。
- **核对**：数据库 `tokens.status` 变为 `refunded`；`operations_log` 新增一条 `refund` 记录；选手的 `individual_popularity` 相应扣减。

### 2. 防重复退款（关键验证）
- **操作**：对上一步已退款成功的同一张卡密，再次发送同样的退款请求。
- **预期**：返回 `code: 42002`，`message` 提示“卡密不存在或不可退款”，`data.businessCode` 为 `not_refundable`。
- **核对**：数据库人气值未二次扣减，`operations_log` 无新记录。

### 3. 退未核销的卡
- **操作**：找一张从未核销过的卡密（`status='unused'`），发送退款请求。
- **预期**：同样返回 `code: 42002`（`not_refundable`）。
- **核对**：卡密状态仍为 `unused`，人气无变化。

---

## References
[1] Claude_Ruling_C12_Final.md: "oid 仅展示不上报后端... 订单追踪是退款(C14)和后续的事"
[2] TokenMapper.java: `markRefundedIfUsed` 方法
[3] RefundService.java: `refund` 方法实现
[4] PopularityLedgerMapper.java: `findRoundIdByIdempotencyKey` 方法
