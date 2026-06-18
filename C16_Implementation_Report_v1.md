# C16「会员有效期」实施报告 v1

> 作者：Manus AI  
> 日期：2026-06-18  
> 状态：已编码，待 Claude 复审  
> 前置裁定：Claude 已批准 C16 技术方案，明确本卡只做“核销成功后会员 +7 天 + 页面展示”，不做退款、撤销、权益回收、风控、会员等级或历史补偿。[1]

## 一、实施结论

C16 已按 Claude 裁定完成最小正式实现。后端新增 `user_membership` 聚合表、Mapper、Service 与会员摘要 DTO；核销成功后在 `TokenService.redeem` 同一事务内追加会员有效期 +7 天；`RedeemResponse` 以 additive 方式返回 `membershipAddedDays`、`membershipUntil` 和 `memberActive`；`/api/me/photos` 在原 `total/items` 外新增独立 `membership` 字段组。小程序核销成功页与我的写真页也已增加会员有效期展示。整个实现没有引入退款、撤销、权限、风控、会员等级或复杂会员中心。

| 模块 | 实施结果 | 边界说明 |
|---|---|---|
| 数据库 | 新增 `user_membership` 表 | 只保存聚合态，不做会员流水 |
| 核销事务 | 核销成功后同事务调用 `grantSevenDays` | 会员叠加失败会回滚核销事务 |
| 并发安全 | `ensureRow` + `SELECT ... FOR UPDATE` + 更新 | 覆盖同用户两卡并发 +14 天测试 |
| API-3 核销成功 | additive 增加会员字段 | 保留 C9 原字段 |
| API-4 我的写真 | additive 增加独立 `membership` 对象 | `total/items` 不变 |
| 小程序 | 核销成功页和我的写真页展示会员有效期 | 不改首页，不做会员中心 |

## 二、主要代码变更

### 2.1 数据表与测试 schema

主 schema 与 H2 测试 schema 均新增 `user_membership` 表。该表以 `user_id` 为主键，保存当前会员有效期截止时间、最近一次增加会员的卡密以及审计时间。H2 schema 同步加入 DROP TABLE 清理顺序，`SchemaInitializationTest` 的表数量从 18 更新为 19，并新增 `user_membership` 表名断言。

| 文件 | 变更 |
|---|---|
| `db/db_schema.sql` | 新增 C16 用户会员有效期聚合表 |
| `backend/redface-backend/src/test/resources/schema-h2.sql` | 同步新增 C16 表与 DROP 清理 |
| `SchemaInitializationTest.java` | 断言表数量与表名包含 `user_membership` |

### 2.2 会员服务与并发控制

新增 `UserMembershipEntity`、`UserMembershipSummary`、`UserMembershipMapper` 与 `UserMembershipService`。`grantSevenDays(userId, tokenId)` 的规则为 `max(now, membershipUntil) + 7 days`，无记录或已过期用户从当前时间起算，未过期用户从旧到期日起续。Mapper 采用项目既有 MyBatis 注解风格，实现“确保行存在 → 锁定用户会员行 → 计算新到期日 → 更新”。[2]

| 类 | 职责 |
|---|---|
| `UserMembershipEntity` | 承载 `user_membership` 表记录 |
| `UserMembershipSummary` | 页面级响应的会员摘要字段组 |
| `UserMembershipMapper` | `ensureRow`、`lockByUserId`、`findByUserId`、`updateMembership` |
| `UserMembershipService` | 正向 +7 天叠加与会员摘要读取 |

### 2.3 核销链路集成

`TokenService.redeem` 保持原“规范化 → 防爆破 → 轮次预检查 → 原子抢占 → 人气入账 → 写真收藏”流程不变，只在写真收藏之后、清除失败计数与返回之前新增 C16 会员 +7 天。该调用处于 `redeem` 的 `@Transactional` 方法内，符合 Claude 对一致性的要求：不得出现“卡密已用但会员没加”。[3]

| 步骤 | C16 前 | C16 后 |
|---|---|---|
| 输入规范化 | 不变 | 不变 |
| 防爆破 | 不变 | 不变 |
| 轮次预检查 | 不变 | 不变 |
| 原子抢占卡密 | 不变 | 不变 |
| 人气入账 | 不变 | 不变 |
| 写真收藏 | 不变 | 不变 |
| 返回成功 | 返回核销字段 | 新增同事务会员 +7 天并返回会员摘要 |

### 2.4 API 响应 additive 扩展

`RedeemResult` 增加会员摘要承载字段，`RedeemResponse` 增加 `membershipAddedDays`、`membershipUntil`、`memberActive`，并由 `RedeemViewService` 在组装页面级 DTO 时填充。`MyPhotosResponse` 增加独立 `membership` 字段组，由 `PhotoQueryService` 调用 `UserMembershipService.getSummary(userId)` 填充。这符合 Claude 对 Q5 的条件裁定：会员字段应独立成组，未来拆 `/api/me/membership` 时前端结构可平滑迁移。[1]

| API | 原字段 | C16 additive 字段 |
|---|---|---|
| `POST /api/tokens/redeem` | `playerNumber/playerName/teamName/points/photoAssetId/photoPreviewUrl/collected` | `membershipAddedDays/membershipUntil/memberActive` |
| `GET /api/me/photos` | `total/items` | `membership: { memberActive, membershipUntil, membershipRemainingDays, membershipAddedDays }` |

### 2.5 小程序展示

小程序核销成功页读取后端返回的会员字段，展示“会员有效期已增加 7 天”和“会员有效期至”。我的写真页读取 `/api/me/photos` 的 `membership` 对象，在 intro-card 中展示“会员有效期至”或“暂未开通会员，核销明信片后将增加会员有效期”。本次没有改首页，也没有新增会员中心或复杂权益文案。

| 页面 | 变更 |
|---|---|
| `pages/redeem-success` | 增加会员有效期增加与到期时间提示 |
| `pages/my-photos` | 增加会员状态提示区块 |

## 三、测试与验证

已补充 C16 专项测试，并执行全量后端回归。C16 重点测试覆盖无记录 +7、已过期从 now +7、未过期从旧有效期 +7、同用户并发两张不同卡累计 +14、核销成功返回会员字段、重复核销不重复加天，以及 `/api/me/photos` 的有/无会员两态。

| 验证项 | 结果 |
|---|---|
| `mvn test -Dtest=SchemaInitializationTest,UserMembershipServiceC16Test,TokenRedeemControllerC16Test,MyPhotosControllerC9Test` | 10 tests，全部通过 |
| `mvn test` 全量回归 | 70 tests，全部通过 |
| 小程序 JS 语法检查 | `node --check` 通过 |
| Git diff 空白检查 | `git diff --check` 通过 |

> 蓝军说明：本次验证证明后端 API 和小程序脚本层面可通过本地测试；真实抖音真机、真实 Agiso、真实订单和正式小程序环境仍属于 6/22 或后续真实环境联调范围，不应被本报告声称为“已上线验证”。

## 四、范围铁线执行情况

Claude 特别强调 C16 不得提前引入 C14/C18 的复杂度。本次实现只出现正向 `grantSevenDays`，没有 `revoke`、`refund`、`rollback`、`membershipLevel`、`subscription`、`risk` 或权限体系改造。重复核销已用卡仍走既有 `already_used` 分支，不会再次加会员天数。

| 禁止事项 | 本次是否涉及 | 说明 |
|---|---|---|
| 退款/撤销/回收 | 否 | 无反向扣减逻辑 |
| 风控封禁 | 否 | 不修改鉴权或风险策略 |
| 会员等级 | 否 | 只有有效期，无等级 |
| 历史补偿 | 否 | 不对历史已核销用户重算 |
| 改 C2-C13 核心业务 | 否 | 仅在核销成功后 additive 追加会员叠加 |
| 改首页 | 否 | 仅核销成功页和我的写真页展示 |

## 五、提交 Claude 复审重点

建议 Claude 重点复审以下几点：第一，`TokenService.redeem` 中会员叠加位置是否完全满足“同一事务”要求；第二，`UserMembershipMapper` 的 `ensureRow + lockByUserId + updateMembership` 是否符合项目 MyBatis/H2/MySQL 兼容要求；第三，`/api/me/photos` 的 `membership` 独立字段组是否符合 Q5 裁定；第四，新增测试是否足以覆盖并发 +14 和重复核销不加天；第五，小程序展示是否保持“只展示有效期，不承诺复杂权益”的范围边界。

| 复审点 | 位置 |
|---|---|
| 同事务会员叠加 | `TokenService.redeem` |
| 并发安全 | `UserMembershipService`、`UserMembershipMapper`、`UserMembershipServiceC16Test` |
| API additive 字段 | `RedeemResponse`、`MyPhotosResponse`、`RedeemViewService`、`PhotoQueryService` |
| 重复核销不加天 | `TokenRedeemControllerC16Test` |
| 小程序展示范围 | `pages/redeem-success`、`pages/my-photos` |

## 六、后续建议

C16 当前可提交 Claude 复审。若 Claude 通过，下一步可以按项目节奏进入批次二 T4/T5/T6 后台易用性方案，或继续推进 6/22 真实环境联调准备。C16 的退款回滚、会员流水、权益回收和历史补偿不建议在本卡继续扩展，应留给 C14 或单独运营脚本处理。

## 参考来源

[1]: 用户提供的《C16方案裁定_批准.md》，Claude 对 C16 六个问题的正式裁定与编码边界；本报告已在正文中完整复述其关键约束。  
[2]: `backend/redface-backend/src/main/java/com/redface/mapper/StatsMapper.java`，项目既有 `ensure + atomic update` MyBatis 聚合态更新风格参考。  
[3]: `backend/redface-backend/src/main/java/com/redface/service/TokenService.java`，卡密核销事务流程，本次在写真收藏后追加 C16 会员叠加。  
[4]: `C16_Membership_Validity_Technical_Plan_v1.md`，C16 技术方案 v1。  
[5]: `backend/redface-backend/src/test/java/com/redface/UserMembershipServiceC16Test.java` 与 `backend/redface-backend/src/test/java/com/redface/TokenRedeemControllerC16Test.java`，C16 专项测试。
