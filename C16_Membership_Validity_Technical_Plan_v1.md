# C16「会员有效期」技术方案 v1（提交 Claude 审批版）

> 作者：Manus AI  
> 日期：2026-06-17  
> 当前状态：仅技术方案，**未编码**  
> 前置状态：C13 已通过 Claude 审查，退款 C14 与最终安全/权限卡不在当前推进范围。[1] [2]

## 〇、开卡背景与目标

C16 的背景来自页面级 API 契约中的明确缺口：购买明信片对应“会员 +7 天”是正式需求，但 C11/C12 彩排底线阶段暂时删除了会员天数展示，因为当时后端缺少 `user_membership` 表和完整的有效期读写链路。[3] 现在 C12 与 C13 已通过 Claude 审查，John 又明确要求除退款与最终安全卡外继续推进彩排卡，因此 C16 可作为下一张轻量补齐卡推进。

C16 的目标是做一个**正向会员有效期闭环**：用户核销成功后，系统把会员有效期按“当前有效期与当前时间取较晚者，再叠加 7 天”的规则延长；核销成功页展示本次会员权益已增加；“我的写真”页或“我的”相关页面展示当前会员有效期。C16 不处理退款、撤销、风控封禁、权益回收或后台权限安全，这些均属于 John 当前明确排除的范围。[2]

> 本方案的核心原则是：**只做正向叠加与展示，不做逆向退款/撤销，不引入复杂会员产品体系**。C16 应增强核销后的权益感，但不能把 C14 退款或 C18 安全权限问题提前带入。

## 一、现有基础能力审计

当前核销链路已经具备事务边界、原子抢占卡密、防重复核销、人气入账和自动收藏写真能力。`TokenService.redeem` 在一个事务中按“规范化 → 防爆破 → 轮次预检查 → 原子抢占 → 人气入账 → 写真收藏 → 返回”的顺序执行；成功后由 `TokenController` 调用 `RedeemViewService` 组装页面级 `RedeemResponse`。[4] [5]

现有数据结构没有会员表。H2 测试 schema 中已经存在 `tokens`、`photo_assets`、`user_photo_collection`、`user_identity` 与 `user_session`，但没有 `user_membership`，这与 API 契约文档中“Schema 里漏建了会员表”的判断一致。[3] [6]

| 模块 | 现状 | C16 可复用点 | 缺口 |
|---|---|---|---|
| 核销事务 | `TokenService.redeem` 已有 `@Transactional` | 可在抢占成功且写真收藏后追加会员 +7 天 | 目前不写会员有效期 |
| 核销成功 DTO | `RedeemResponse` 返回选手、团队、人气、写真等字段 | 可 additive 增加会员到期与本次增加天数 | 当前无会员字段 |
| 我的写真页 API | `/api/me/photos` 返回写真列表 | 可 additive 增加会员有效期摘要 | 当前只返回 `total/items` |
| 数据库 | 有用户身份、会话、写真收藏表 | 可新增 `user_membership` 表，以 `user_id` 为主键 | 当前无会员有效期存储 |
| 测试 | API-3/4 已有回归测试 | 可扩展测试正向叠加、不破坏原字段 | 需新增 C16 专项测试 |

## 二、范围裁定建议

C16 建议定义为 **P1 正向会员有效期版**，不做会员商品体系，不做会员等级，不做退款/撤销，不做权益回收。它只回答三个问题：用户当前会员到期日是什么；核销成功是否增加 7 天；前端在哪里展示这项权益。

| 纳入 C16 | 暂不纳入 C16 |
|---|---|
| 新增 `user_membership` 表 | 不做退款、撤销、权益回收 |
| 核销成功时正向延长 7 天 | 不做风控封禁或权限安全体系 |
| 核销成功响应 additive 返回会员字段 | 不做会员等级、连续包月、付费订阅 |
| “我的写真”页 additive 展示会员到期 | 不新增复杂会员中心 |
| 新增 C16 后端测试与前端静态验证 | 不对已用卡密追加补偿性重算，历史补偿另开运营脚本 |

## 三、数据模型方案

建议新增单表 `user_membership`。彩排阶段不必新增复杂会员流水表，因为核销卡密本身已通过 `tokens.token_id` 与 `user_id/used_at` 记录了权益来源；如果后续需要财务审计或退款回滚，再另开非彩排卡处理会员流水与反向权益。当前表只保存会员聚合态，便于页面快速读取。

```sql
CREATE TABLE user_membership (
  user_id          VARCHAR(64) NOT NULL,
  membership_until TIMESTAMP NOT NULL,
  last_token_id    VARCHAR(32) NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  KEY idx_membership_until (membership_until)
);
```

| 字段 | 用途 | 说明 |
|---|---|---|
| `user_id` | 用户主键 | 与 `user_identity/user_session` 的脱敏 userId 一致 |
| `membership_until` | 当前会员有效期截止时间 | 核销成功后正向叠加 |
| `last_token_id` | 最近一次增加会员的卡密 | 只用于排查，不用于退款或回滚 |
| `created_at/updated_at` | 审计时间 | 保持与现有 schema 风格一致 |

### 3.1 叠加规则

C16 固定使用“+7 天”规则。若用户当前没有会员记录，或 `membership_until < now`，则新到期时间为 `now + 7 days`；若用户当前仍在会员有效期内，则新到期时间为 `membership_until + 7 days`。

| 当前状态 | 示例 | 核销后结果 |
|---|---|---|
| 无会员记录 | 无 `user_membership` 行 | `now + 7 days` |
| 已过期 | `membership_until = yesterday` | `now + 7 days` |
| 未过期 | `membership_until = now + 3 days` | `now + 10 days` |
| 连续核销两张卡 | 第一张后 `now+7`，第二张后 | `now + 14 days` |

该规则只适用于核销成功后的正向叠加。重复核销已使用卡密时，现有卡密原子抢占会返回 `already_used`，不得再次增加会员天数。[4]

## 四、后端技术方案

后端保持 Java 17、Spring Boot 3、MyBatis、构造器注入与统一 `ApiResponse` 风格。C16 不引入 JPA，不新增复杂权限系统，不修改 C13 的卧底链路。

### 4.1 新增类建议

| 类 | 职责 |
|---|---|
| `UserMembershipMapper` | 查询、创建/锁定、更新 `user_membership` |
| `UserMembershipService` | 封装 `grantSevenDays(userId, tokenId)` 与 `getSummary(userId)` |
| `UserMembershipSummary` DTO | 返回 `memberActive`、`membershipUntil`、`remainingDays` 等展示字段 |
| `UserMembershipEntity`（可选） | 承载表记录；也可直接用 DTO/简单 POJO |

### 4.2 核销链路改造点

建议在 `TokenService.redeem` 中，抢占成功、加人气、收藏写真之后，调用 `userMembershipService.grantSevenDays(userId, token)`。由于 `redeem` 已经是事务方法，会员叠加应处于同一事务中：如果会员叠加失败，整个核销事务回滚，避免出现“卡密已用但会员没加”的不一致。

| 步骤 | 当前行为 | C16 后行为 |
|---|---|---|
| 1 | 输入规范化 | 不变 |
| 2 | 防爆破 | 不变 |
| 3 | 轮次预检查 | 不变 |
| 4 | 原子抢占卡密 | 不变 |
| 5 | 人气入账 | 不变 |
| 6 | 自动收藏写真 | 不变 |
| 7 | 返回成功 | 新增：会员 +7 天后再返回 |

并发方面，会员服务需要在同一用户同时核销多张不同卡密时避免丢失更新。建议实现时采用“确保行存在 → `SELECT ... FOR UPDATE` 锁定该用户会员行 → 计算 `max(now, oldUntil)+7 days` → 更新”的模式。若实现时 H2 与 MySQL 语法存在差异，应优先用 MyBatis 方法拆分和事务锁来保证测试可跑，而不是用无法跨测试环境的复杂 SQL。

### 4.3 响应字段 additive 扩展

C16 应保持 API-3 与 API-4 原有字段不变，只做 additive 扩展，避免破坏 C9/C11/C12 已通过链路。当前 `TokenRedeemControllerC9Test` 已断言核销成功响应包含 `playerNumber/playerName/teamName/points/photoAssetId/photoPreviewUrl/collected`，C16 不应删除或改名这些字段。[7]

`RedeemResponse` 建议新增字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `membershipAddedDays` | number | 本次核销增加天数，固定 7 |
| `membershipUntil` | string | 最新会员到期时间，ISO 或后端现有时间序列化格式 |
| `memberActive` | boolean | 核销成功后是否为会员，通常 true |

`MyPhotosResponse` 建议新增字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `memberActive` | boolean | 当前用户是否在有效期内 |
| `membershipUntil` | string/null | 当前会员有效期截止时间 |
| `membershipRemainingDays` | number | 向上取整或自然日剩余天数，用于 UI 展示 |

如 Claude 认为 `/api/me/photos` 语义不应承载会员信息，可新增 `/api/me/membership`。但从彩排最小闭环角度，Manus 建议先 additive 扩展 `/api/me/photos`，因为当前小程序“我的”tab 实际落在“我的写真”页，用户最容易在这里看到权益状态。[8]

## 五、小程序前端方案

小程序前端只做轻量展示，不新增复杂会员中心。核销成功页在“数字写真已自动存入我的收藏”下面增加一行正向权益提示，例如“会员有效期已增加 7 天”，并在信息行中展示“会员有效期至 YYYY-MM-DD”。“我的写真”页顶部 intro-card 展示当前会员状态。

| 页面 | 现状 | C16 建议 |
|---|---|---|
| 核销成功页 | 展示写真收藏、人气值、选手、团队 | 增加“会员有效期已增加 7 天”“有效期至” |
| 我的写真页 | 展示写真总数与写真列表 | 顶部增加“会员有效期至 / 暂未开通会员” |
| 首页 | 不展示会员 | C16 不改首页，避免范围扩大 |

文案建议保持克制，不承诺复杂权益。示例文案如下：

| 场景 | 建议文案 |
|---|---|
| 核销成功 | “会员有效期已增加 7 天。” |
| 有效会员 | “会员有效期至：2026-06-24。” |
| 暂无会员 | “暂未开通会员，核销明信片后将增加会员有效期。” |
| 数据异常 | “会员状态暂未获取，请稍后刷新。” |

## 六、测试方案

C16 编码获批后必须新增后端测试，并保持现有全量回归通过。测试重点是：正向叠加规则、重复卡密不重复加天、API 字段 additive、不破坏 `/api/me/photos` 原字段。

| 测试类建议 | 覆盖场景 |
|---|---|
| `UserMembershipServiceC16Test` | 无记录 +7、已过期从 now +7、未过期从 oldUntil +7、连续两次不同 token +14 |
| `TokenRedeemControllerC16Test` | 核销成功返回会员字段；重复核销已用卡不增加会员；原 C9 字段仍存在 |
| `MyMembershipControllerC16Test` 或扩展 `MyPhotosControllerC9Test` | 我的页返回会员有效期、未开通时字段安全为空 |
| 全量回归 | `mvn clean test` 通过，不退化 C12/C13 |

前端验证建议包括：小程序 JS 语法检查、核销成功页字段展示、我的写真页会员区块展示，以及无会员/有会员两种状态。真实端到端仍需在 6/22 或后续真机环境验证，不能在本地沙箱中伪造为“已真实上线验证”。

## 七、蓝军风险清单

| 风险 | 影响 | 控制建议 |
|---|---|---|
| 误把退款/撤销纳入 C16 | 范围失控，触碰 John 明确排除项 | C16 文档和代码只出现正向 grant，不做 revoke/refund |
| 并发核销导致会员天数丢失 | 用户权益少加 | 会员行加锁或等价事务保护，测试连续核销 |
| 卡密已用但会员未加 | 数据不一致 | 会员叠加放入 `TokenService.redeem` 同一事务 |
| API 字段破坏旧页面 | C9/C11/C12 回归失败 | 所有字段 additive，不删除原字段 |
| 会员展示承诺过多 | 后续运营压力 | 只展示有效期，不展示复杂权益包 |
| 历史已核销用户无会员补偿 | 内测样本可能疑惑 | C16 不做历史补偿；如需要另开一次性运营脚本 |

## 八、提交 Claude 裁定的问题

| 编号 | 问题 | Manus 建议 |
|---|---|---|
| C16-Q1 | 是否批准新增 `user_membership` 表作为 C16 唯一会员聚合表？ | 批准，先不做会员流水表 |
| C16-Q2 | 是否批准核销成功时固定正向增加 7 天？ | 批准，按契约文档执行 |
| C16-Q3 | 叠加规则是否为 `max(now, membershipUntil)+7 days`？ | 批准，避免过期用户从旧日期续期 |
| C16-Q4 | 是否批准在 `RedeemResponse` additive 返回会员字段？ | 批准，方便核销成功页立即展示权益 |
| C16-Q5 | 是否批准在 `/api/me/photos` additive 返回会员摘要？ | 倾向批准，彩排最小闭环最快；如不同意则改新增 `/api/me/membership` |
| C16-Q6 | 是否明确 C16 不做退款、撤销、权益回收、风控封禁或权限安全？ | 必须批准，符合 John 当前排除范围 |

## 九、预期交付物

如 Claude 批准，本卡编码阶段建议交付以下内容。C16 仍应延续 C13 流程：先审批方案，再编码，再生成实施报告与联调清单。

| 交付物 | 说明 |
|---|---|
| 后端会员表与 Mapper/Service | `user_membership` schema、`UserMembershipMapper`、`UserMembershipService` |
| 核销链路正向叠加 | `TokenService.redeem` 成功事务中会员 +7 天 |
| 响应 DTO additive 字段 | `RedeemResponse`、`MyPhotosResponse` 增加会员展示字段 |
| 小程序展示 | 核销成功页与我的写真页展示会员有效期 |
| 测试 | C16 专项测试 + 全量回归 |
| 文档 | `C16_Implementation_Report_v1.md` 与 `C16_Integration_Checklist_v1.md` |

## 十、引用

[1]: C13_Closure_Summary_v1.md "C13 结项总结与卧底识破玩法完成确认 v1"
[2]: Rehearsal_Remaining_Cards_Scope_v1.md "后续彩排卡范围说明 v1"
[3]: docs/API契约/当前页面级API契约定稿_C9依据_V1.0.md "当前页面级 API 契约：C16 user_membership 表 + 核销 +7 天 + 我的页展示"
[4]: backend/redface-backend/src/main/java/com/redface/service/TokenService.java "TokenService.redeem 核销事务流程"
[5]: backend/redface-backend/src/main/java/com/redface/controller/TokenController.java "TokenController 核销成功后组装 RedeemResponse"
[6]: backend/redface-backend/src/test/resources/schema-h2.sql "H2 测试 schema：tokens、user_photo_collection、user_identity、user_session，当前无 user_membership"
[7]: backend/redface-backend/src/test/java/com/redface/TokenRedeemControllerC9Test.java "API-3 核销成功响应现有字段断言"
[8]: frontend/douyin-miniprogram/pages/my-photos/index.ttml "我的写真页当前结构"
