# Manus 第二轮“UI—API—测试用例”三方对齐复审报告 V1.0

**项目名称：** 红颜局中局  
**复审对象：** Claude《UI 二审回应蓝军报告 + API 契约定稿》、彬少 UI 二审修改方向、当前后端代码与数据库结构  
**复审日期：** 2026-06-14  
**作者：** Manus AI  
**文档用途：** 供 John 转发给 Claude，请 Claude 确认 C9 实施边界与测试验收口径  

---

## 1. 总体结论

Claude 本次 API 契约定稿总体正确，我建议 **C9 可以按 API-0 至 API-4 的方向推进**。相比我上一轮蓝军报告，Claude 补充了我漏掉的 **API-0 登录鉴权**，并把 `redeem` 成功响应增强为前端可直接渲染的页面级 DTO，这是必要且正确的修正。[1]

但蓝军视角下，C9 的工作量不能被理解为“加几个 Controller”。当前仓库仍未发现 Controller 层注解；现有后端具备 `TokenService`、`CollectStateService`、`StatsMapper`、`UserPhotoCollectionMapper` 等业务基础，但 API-0 至 API-4 都还需要不同程度的 **身份模块、页面聚合查询、响应 DTO、错误码映射和 Controller 测试**。[4] [5] [6] [7]

| 复审项 | 结论 | 风险级别 | 建议 |
| --- | --- | --- | --- |
| API 方向 | Claude 的 API-0 至 API-4 方向正确，API-5 后置合理。 | 低 | C9 采用该契约作为开发依据。 |
| 后端可实现性 | 核心业务规则已有基础，但页面级聚合与 Controller 层尚未完成。 | 中高 | C9 必须补 DTO、Mapper join、统一响应和测试。 |
| UI 覆盖度 | 首页、人气、核销、我的写真主链路基本覆盖。 | 中 | 选手详情、会员有效期、订单查询、会员群、真相识破不进 P0。 |
| 测试验收 | 现有 C2—C6 测试覆盖业务规则，但不覆盖 API 层。 | 高 | C9 必须新增 Controller/API 集成测试。 |
| 最大蓝军风险 | API-3 错误码表达与现有 `RedeemResult` 字符串 code 体系不一致。 | 高 | 需要统一外层 code 与业务错误码，不然前端状态会混乱。 |

---

## 2. 对 Claude API 契约的逐项复审

### 2.1 API-0 登录：必要，但不能低估身份模块缺口

Claude 增补 `POST /api/auth/login` 是正确的，因为所有 `/api/me/*` 都依赖用户身份。如果没有登录态，“我的写真”无法可靠按用户维度查询。[1]

不过，当前 schema 和代码中只发现 `tokens.user_id` 与 `user_photo_collection.user_id` 字段，未发现用户表、openid 映射表、session 表或 auth/login 服务。[8] 这意味着 API-0 不是简单 Controller，它至少需要明确三件事：抖音 `tt.login` code 如何换 openid，后端如何生成脱敏 `userId`，以及该 `userId` 是否持久稳定。

| 检查项 | 当前情况 | 建议 |
| --- | --- | --- |
| openid 存储 | 当前未发现用户表或 openid 映射表。 | C9 最小实现应新增用户身份映射表，或明确以脱敏 openid 作为稳定 userId。 |
| 登录态 | 当前未发现 session/token 机制。 | 若时间紧，可先采用简单 token/session，但必须支持 `/api/me/photos` 鉴权。 |
| 测试 | 当前无 auth 测试。 | 必测正常登录、无效 code、重复登录稳定映射、未登录访问 me 接口。 |

### 2.2 API-1 首页直播状态：契约正确，但需要聚合服务

API-1 的字段设计能覆盖首页的主要 UI 信息，包括直播状态、轮次、当前互动归属、当前目标人气、团队人气、识破入口开关和更新时间。[1] 这解决了我上一轮提出的“首页字段需要页面级接口”的问题。[2]

当前 `CollectStateService` 能维护 `mode/targetId/roundId`，`RoundService` 能选择 active 或 upcoming 轮次，但它们返回的是原始业务字段，不是首页 DTO。[5] 因此 C9 需要新增组合服务或 Mapper join，把 `players`、`teams`、`player_round`、`player_round_stats`、`team_round_stats` 和 `collect_state` 拼成前端可直接渲染的 `targetDisplayName` 与人气数值。[5] [6] [8]

| API-1 字段 | 当前支撑 | 缺口 |
| --- | --- | --- |
| `liveStatus` | `RoundService` 可判断 active/upcoming。 | 需定义 `idle` 与 upcoming 的关系，避免“有 upcoming 但未直播”被误判为 live。 |
| `roundName` | `rounds.name` 存在。 | 当前 `RoundMapper` 只查 ID，需补 name 查询。 |
| `targetDisplayName` | `collect_state.targetId` 和 `players/teams` 表可支撑。 | 需 join 组装“3号 林夏 A组”。 |
| `targetPopularity/teamPopularity` | 统计表存在，`StatsMapper` 有单值查询。 | 需按 currentMode 聚合查询。 |
| `spyChannelOpen` | 可由业务状态判断。 | 需明确开启条件：round、日期、后台开关还是 spy mode。 |

### 2.3 API-2 人气看板：方向正确，排序测试必须硬性加入

API-2 明确 `items` 按 number 升序，绝不按 value 排序，这是合规上最重要的字段约束。[1] 数据库中有 `players.number`、`player_round.team_id`、`player_round_stats.individual_popularity/spy_popularity` 和 `team_round_stats.team_popularity`，因此该接口具备 schema 基础。[8]

当前 `StatsMapper` 只有单个 player/team/pool 的查询方法，并没有面向 UI 的列表查询。[6] 因此 C9 必须新增列表 Mapper，并用测试证明排序是按 number，而不是按 value。这个测试应作为合规测试，而不只是功能测试。

| tab | 建议实现 | 必测点 |
| --- | --- | --- |
| `player` | join `players + player_round + teams + player_round_stats`，按 `players.number ASC`。 | 即使 3 号 value 最大，也必须排在 1、2 号后。 |
| `team` | 查询团队统计，建议按 team_id 或固定配置顺序。 | 不得按 team_popularity 降序显示。 |
| `spy` | 未激活时返回 `spyTabEnabled=false`，前端灰置。 | 未激活时不应返回错误或空白崩溃。 |

### 2.4 API-3 卡密核销：核心最成熟，但 DTO 与错误码要统一

API-3 是当前最成熟的主链路。现有 `TokenService.redeem` 已经完成输入规范化、防爆破、轮次预检查、原子抢占、人气入账和写真自动收藏；并且现有测试已覆盖成功核销、重复核销、并发核销、防爆破和无轮次保护。[4]

但 Claude API-3 成功响应要求 `playerNumber/playerName/teamName/photoPreviewUrl/collected`，当前 `RedeemResult` 只返回 `tokenId/playerId/points/photoAssetId/remainingSeconds`。[4] [9] 当前 `TokenMapper.findById` 也只查 token 表，不 join player/team/photo 表。[7] 因此 C9 需要新增 `RedeemResponse` 页面 DTO，不能直接把现有 `RedeemResult` 原样暴露给前端。

另外，Claude 契约写法为“失败 code 枚举：40001 invalid_format / 40002 not_found ...”，而当前 `RedeemResult` 的业务 code 是字符串，如 `invalid_format`、`not_found`、`already_used`、`locked`、`round_not_available`。[9] 这里必须统一，否则前端到底按数值码还是字符串码展示状态会产生歧义。

| 错误状态 | 现有后端字符串 | Claude 数值码 | 建议前端业务语义 |
| --- | --- | --- | --- |
| 格式错误 | `invalid_format` | 40001 | “卡密格式错误，请检查 RFZJ 前缀和分段。” |
| 不存在 | `not_found` | 40002 | “卡密不存在或不可用。” |
| 已核销 | `already_used` | 40003 | “该卡密已被核销。” |
| 锁定 | `locked` | 40004 | “连续错误次数过多，请 X 秒后再试。” |
| 无轮次 | `round_not_available` | 40005 | “当前无可用轮次，请联系工作人员。” |

我的建议是采用统一响应：外层 `{ code, message, data }` 的 `code` 保持数值，业务错误同时给出 `businessCode` 字符串。例如失败响应为 `{ code: 40001, message: "卡密格式错误", data: { businessCode: "invalid_format" } }`。这样既满足 Claude 的数值码，也保留现有后端语义，前端也能稳定映射 UI 状态。

### 2.5 API-4 我的写真：简单但不是现成能力

API-4 的方向正确，因为核销成功后的“我的数字写真”需要用户维度列表。[1] 当前 schema 中有 `user_photo_collection` 和 `photo_assets`，且核销成功会自动插入收藏记录。[4] [8]

但是现有 `UserPhotoCollectionMapper` 只有 insert 和测试用 count，没有按用户查询列表的方法，也没有 join `photo_assets` 和 `players` 的查询。[10] 所以 API-4 虽然可以做成“一条 SQL”，但仍需新增列表 DTO、鉴权、空状态和用户隔离测试。

### 2.6 API-5 真相识破：后置是正确选择

Claude 将 API-5 标记为 P1，并明确彩排后随 C13 开卡时定稿，这是正确的。虽然数据库已有 `suspicion_votes` 表，但当前没有对应 Mapper、Service 或 Controller。[8]

因此 C9 不应实现 `POST /api/suspicion/submit`。如果 UI 仍保留识破入口，应由 API-1 的 `spyChannelOpen=false` 控制隐藏或灰置，而不是前端做一个无法提交的假页面。

---

## 3. UI—API 覆盖复审

Claude 契约基本覆盖了 P0 页面，但前端和彬少必须同步删除或灰置所有不在契约内的模块。否则即使 API-0 至 API-4 做对，页面仍可能因为“多画了但后端不给”而返工。

| UI 页面 | API 覆盖 | 结论 | 必须同步给彬少/前端的边界 |
| --- | --- | --- | --- |
| 首页 | API-1 覆盖直播状态、当前互动归属、人气摘要、识破入口开关。 | 覆盖完整。 | 四入口已决策删除；“集赞”改为“当前直播互动计入”。 |
| 人气页 | API-2 覆盖 player/team/spy 三档。 | 覆盖主链路。 | spy 未激活灰置；排序只能按 number。 |
| 卡密核销页 | API-3 覆盖提交与错误码。 | 覆盖完整。 | 必须补五类错误态、提交中、防重复点击、网络失败。 |
| 核销成功页 | API-3 成功 DTO 覆盖选手、团队、人气、写真预览。 | 覆盖完整。 | 会员有效期删除；写真加载失败需降级。 |
| 我的页 | API-0 + API-4 覆盖登录与写真列表。 | 覆盖基础闭环。 | 会员天数、订单查询、会员群不进 P0。 |
| 选手详情页 | 当前无独立 API。 | 不应作为 P0 动态页。 | 删除个人动态、关注、距离；如保留，只做静态或极简展示。 |
| 真相识破页 | API-5 后置。 | 不进 C9。 | 候选/进度错位可先改视觉稿，但不开发提交。 |

---

## 4. C9 建议测试矩阵

现有 C2—C6 测试覆盖的是业务规则，不覆盖 Controller 层、统一响应包和页面 DTO。[3] C9 必须新增 API 集成测试，否则“接口看似可调但 UI 字段不全”的问题会在联调阶段暴露。

| API | 建议测试类 | 必测场景 |
| --- | --- | --- |
| API-0 | `AuthControllerC9Test` | 正常 code 登录；重复登录稳定返回同一 userId；无效 code 返回固定错误；未登录访问 `/api/me/photos` 被拒。 |
| API-1 | `LiveHomeControllerC9Test` | active 轮次 live；无 active idle；collect_state 为 player/team/spy/pool 时目标展示正确；`spyChannelOpen=false`。 |
| API-2 | `PopularityBoardControllerC9Test` | player tab 按 number 升序；team tab 不按 value 排序；spy tab 未激活灰置；空数据返回 items=[]。 |
| API-3 | `TokenRedeemControllerC9Test` | 成功 DTO 字段完整；五类错误码完整；locked 带 remainingSeconds；并发核销仍只有一次成功。 |
| API-4 | `MyPhotosControllerC9Test` | 无收藏返回空列表；有收藏返回 previewUrl/playerName/createdAt；用户间数据隔离；未登录拒绝。 |

特别建议把“**排序不是按人气值**”写成自动化测试。因为这不是 UI 偏好，而是合规红线。如果后续有人为了“好看”把 SQL 改成 `ORDER BY value DESC`，测试必须立刻失败。

---

## 5. Manus 对 Claude 的确认请求

我建议 John 将本报告转发给 Claude，请 Claude 确认以下 5 点。只要这 5 点确认，C9 就可以进入实现；如果有一项不能确认，应先修改契约再开发。

| 待确认项 | Manus 建议 | 原因 |
| --- | --- | --- |
| API-0 是否新增用户身份映射表 | 建议新增最小 `users` 或 `user_identity` 表。 | 否则 `/api/me/photos` 的长期稳定性不足。 |
| 统一响应错误码格式 | 建议外层数值 code + data.businessCode 字符串。 | 兼容 Claude 契约与现有 `RedeemResult` 字符串语义。 |
| API-1 liveStatus 定义 | 建议只有 active 轮次为 `live`，无 active 为 `idle`，upcoming 仅用于核销入账轮次策略。 | 避免首页显示“正在直播”但实际只是 upcoming。 |
| API-2 team tab 排序规则 | 建议明确按 team_id 或配置顺序，而非人气值。 | 防止团队页变相排名。 |
| API-4 是否纳入 C9 必测 | 建议纳入，哪怕只做简单列表。 | 我的写真是核销闭环的一部分，不能只插入不展示。 |

---

## 6. 最终蓝军结论

Claude 的 API 契约已经解决了上一轮最核心的“UI 字段后端给不了”问题，我建议采纳为 C9 开发依据。但 C9 的验收标准必须提高：不能只看接口是否存在，而要看 **UI 字段是否完整、错误状态是否覆盖、排序是否合规、登录态是否稳定、测试是否防回归**。

如果 Claude 确认本报告第 5 节的五项问题，Manus 认为 C9 可以开始实现。若 Claude 对用户表、错误码格式或 liveStatus 定义有不同意见，建议先在 GitHub 文档中定稿后再动代码，避免前端、后端和测试三方再次错位。

---

## References

[1]: ClaudeUI二审_回应蓝军报告_API契约定稿.md "Claude UI 二审：回应蓝军报告 + API 契约定稿"
[2]: UI蓝军审查报告_墨刀最新界面_V1.0.md "Manus UI 蓝军审查报告 V1.0"
[3]: API契约/API契约复审_测试矩阵与风险_V1.0.md "API 契约复审：测试矩阵与蓝军风险 V1.0"
[4]: ../backend/redface-backend/src/main/java/com/redface/service/TokenService.java "TokenService.java：卡密核销业务流程"
[5]: ../backend/redface-backend/src/main/java/com/redface/service/CollectStateService.java "CollectStateService.java：当前场控互动归属"
[6]: ../backend/redface-backend/src/main/java/com/redface/mapper/StatsMapper.java "StatsMapper.java：人气统计单值查询能力"
[7]: ../backend/redface-backend/src/main/java/com/redface/mapper/TokenMapper.java "TokenMapper.java：卡密原子抢占与 token 查询"
[8]: ../db/db_schema.sql "db_schema.sql：数据库表结构"
[9]: ../backend/redface-backend/src/main/java/com/redface/dto/RedeemResult.java "RedeemResult.java：当前卡密核销结果对象"
[10]: ../backend/redface-backend/src/main/java/com/redface/mapper/UserPhotoCollectionMapper.java "UserPhotoCollectionMapper.java：写真收藏写入与测试计数"
