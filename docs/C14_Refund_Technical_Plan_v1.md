# C14 退款（Refund）技术方案与待裁定问题

> 制定人: Manus
> 日期: 2026.06.19
> 状态: **待 Claude 裁定**

## 一、背景与目标

C14 是一张涉及“钱”（退款）的 P0 级核心卡。根据现有的系统架构与 C5（核销）、C2（人气引擎）、C16（会员有效期）等实现，当前系统只支持“正向核销”与“正向权益叠加”。
本方案的目标是：在**绝对不破坏现有 C5、C2 逻辑**的前提下，设计一条**防重复、防负数、强审计**的退款回滚链路，并清晰列出由于现有业务逻辑留白而导致的**边界裁定问题**，交由 Claude 最终拍板。

---

## 二、退款核心逻辑（拟定）

### 2.1 整体架构与入口

- **专属领地**：新增 `RefundService`、新增 `RefundController`（或合并在后台 `AdminControlController` 中）。
- **回滚方式**：退款本质上是“对已核销卡密的逆向操作”。不改变 `TokenService.redeem` 的主干代码，而是通过调用 `PopularityService.applyChange`（`source='refund'`，负数 `rawValue`）来实现人气扣减。

### 2.2 数据模型现状与约束

通过勘察现有代码（`db_schema.sql`、`TokenService.java`、`PopularityService.java` 等），得出以下事实：
1. **人气引擎**：`PopularityService.applyChange` 已支持 `source='refund'` 和负数 `rawValue`，底层 `PopularityLedgerMapper` 支持写入负流水。但 `StatsMapper` 的 `increment...` 方法是无条件累加，**数据库层面没有防负数约束**。
2. **卡密状态**：`tokens` 表只有 `unused` 和 `used` 两态，没有 `refunded` 状态。
3. **会员权益（C16）**：`user_membership` 只有聚合态 `membership_until`，且 `UserMembershipService` 明确“只做正向叠加与展示，不做撤销、退款”。
4. **写真收藏**：`user_photo_collection` 只有插入，没有撤销或删除能力。
5. **订单追踪（C12 裁定）**：Claude 在 C12 中明确裁定“oid(订单号)在彩排阶段仅展示/调试用，订单追踪是退款(C14)和后续的事”，目前后端并未存储订单与卡密的强绑定关系（`tokens.order_id` 可能为空或未被前端有效传入）。

---

## 三、待 Claude 裁定的核心边界问题（🔴 重点）

鉴于以上现状，C14 在编码前必须由 Claude 对以下边界问题进行裁定：

### 裁定点 1：退款的“主键”是什么？
- **现状**：用户在抖店退款，通常按“订单号（order_id）”发起。但目前后端 `tokens` 表的 `order_id` 并未在核销时强制绑定（C12 裁定前端不上报 oid）。
- **问题**：退款 API 的入参是传 `token_id`（卡密）还是 `order_id`（订单号）？
  - *方案 A（推荐）*：后台退款按 `token_id` 发起。客服在阿奇索/抖店查到退款订单对应的卡密，然后在场控后台输入卡密进行退款。这不需要改造现有的核销链路。
  - *方案 B*：按 `order_id` 发起。这需要先改造 C5 核销链路，强制前端上报并落库 `order_id`，工作量大且涉及 C12 已定契约的推翻。

### 裁定点 2：退款状态如何标记（防重复退款）？
- **现状**：`tokens` 表只有 `status = 'used'`。
- **问题**：如何防止同一张卡密被退款两次？
  - *方案 A（推荐）*：在 `tokens` 表新增状态 `status = 'refunded'`。退款时执行 `UPDATE tokens SET status = 'refunded' WHERE token_id = ? AND status = 'used'`，利用影响行数做原子抢占。
  - *方案 B*：不改 `tokens` 表，纯靠 `popularity_ledger` 的 `idempotency_key`（如 `refund_token_{tokenId}`）防重。但这样无法直观查询卡密是否已退款。

### 裁定点 3：退款后“人气值变负数”如何处理？
- **现状**：如果选手当前人气为 0，退款扣减 19900 后，`player_round_stats` 里的 `individual_popularity` 会变成负数。C4 积分引擎（`computeScore`）目前对负人气会计算出负积分，并在最后钳制为 0。
- **问题**：是否允许数据库统计表出现负数？
  - *方案 A（推荐）*：允许统计表出现负数（当退款发生时，真实人气就是亏空的）。展示层（C4）已经做了兜底（最小为 0），无需修改底层 SQL，保持架构最简单。
  - *方案 B*：不允许负数。需要在扣减前查余额，如果余额不足则拒绝退款，或只扣减到 0。这会带来复杂的并发控制问题。

### 裁定点 4：退款是否需要回滚“会员天数”和“写真收藏”？
- **现状**：C16 会员是纯聚合态（只增不减）；写真收藏也没有撤销逻辑。
- **问题**：退款时，是否要把因为核销这张卡密而增加的 7 天会员扣掉？是否要删除对应的写真收藏？
  - *方案 A（推荐）*：**只退钱（扣人气），不扣会员和写真**。会员和写真作为虚拟权益，发了就发了，退款不回收。这符合 John 在 C16 中“不引入复杂会员产品体系、不做撤销”的原则，且开发成本最低。
  - *方案 B*：硬回收。需要给 `UserMembershipService` 加逆向扣减逻辑，给写真加删除逻辑。风险极高，容易引出“扣减后会员过期”等复杂状态。

---

## 四、拟定的 C14 技术实现路径（基于上述“方案 A”）

如果 Claude 批准上述所有“方案 A”，C14 的编码动作将非常收敛：

1. **数据库修改**：
   - 允许 `tokens.status` 写入 `'refunded'`。
2. **新增 RefundService**：
   - 入参：`tokenId`，`operatorId`，`reason`。
   - 步骤 1：原子抢占 `UPDATE tokens SET status = 'refunded' WHERE token_id = ? AND status = 'used'`。影响行数不为 1 则报错。
   - 步骤 2：查出 `token` 绑定的 `playerId` 和 `points`。
   - 步骤 3：调用 `PopularityService.applyChange`，传入负数 `-points`，`source="refund"`，`idempotencyKey="refund_" + tokenId`。
   - 步骤 4：写 `operations_log` 审计日志。
3. **新增后台 API**：
   - 在 `AdminControlController` 或新建 `RefundController` 中增加 `POST /api/admin/refund` 接口。
   - 仅供后台场控调用，不暴露给前端用户侧。

---

## 五、总结与下一步

本方案严格遵守了《三流并行协调原则_C18_T4_C14.md》中 C14 的专属地盘和禁区（不改前端、不改鉴权、不破坏核销主逻辑）。

**请 John 将本方案提交给 Claude 审查。等待 Claude 对上述 4 个裁定点给出明确结论后，Manus 再开始编码。**
