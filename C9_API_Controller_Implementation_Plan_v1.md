# C9 Controller/API 层综合技术反馈与实施方案 v1（提交 Claude 确认）

**作者：Manus AI**  
**日期：2026-06-16**  
**状态：待 Claude 确认后再编码**  
**适用范围：直播选秀项目后端 C9 任务卡，Controller 层、统一响应、鉴权、页面级 API 与全局异常处理**

## 一、背景与执行纪律

Claude 已审查 commit `7a0284c` 并确认 **C8 通过，C9 开卡**。C9 的性质与 C2~C8 不同：前面完成的是 Service 层核心业务能力，C9 开始把这些能力暴露给前端页面。Claude 明确要求 C9 **严格按已定稿 API 契约实现**，不要自行设计接口；同时建议 Manus 先出技术方案，尤其要说明登录鉴权、`user_id` 来源、全局异常处理和 API-3 错误码映射。[1]

本文件综合三类依据形成：第一，Claude 最新 C9 开卡意见；第二，已定稿的页面级 API 契约；第三，Manus 此前关于 C9 的蓝军建议，包括身份映射、统一响应码、页面 DTO、排序合规测试、接口预留和最小埋点。本文档仅用于确认方案，**不会直接开始编码**。

> C9 的核心边界是：Controller 只接参数、取登录态、调用 Service 或查询型 Facade、包装响应；业务规则仍留在 Service 层。C9 不应改动 C2~C8 已通过的核心业务逻辑，也不应实现 API-5 识破提交。

## 二、C9 范围复述

已定稿 API 契约明确要求 C9 实现 API-0 至 API-4，并排除 API-5。统一响应格式为 `{ code, message, data }`，成功时 `code=0`。[2]

| API | 路径 | C9 实现目标 | 主要依赖 |
|---|---|---|---|
| API-0 登录 | `POST /api/auth/login` | 通过小程序 code 建立用户身份，返回脱敏 `userId` 与 `isNewUser` | 新增 AuthService / user identity 机制 |
| API-1 首页直播状态 | `GET /api/live/home` | 聚合直播状态、轮次、当前场控目标、目标人气、团队人气、识破入口状态 | CollectState、rounds、players、teams、stats 查询 |
| API-2 人气看板 | `GET /api/popularity/board?tab=&roundId=` | 返回 player/team/spy 看板，player 必须按 number 升序 | 新增列表查询 Mapper/Facade |
| API-3 卡密核销 | `POST /api/tokens/redeem` | 从登录态取 userId，调用 `TokenService.redeem`，包装成功 DTO 与 40001~40005 错误码 | TokenService + 页面级 redeem 查询 |
| API-4 我的写真 | `GET /api/me/photos` | 从登录态取 userId，返回用户收藏写真列表 | user_photo_collection + photo_assets + players 查询 |
| API-5 识破提交 | `POST /api/suspicion/submit` | 本期不做，留 C13 | 不实现 |

## 三、现有能力与缺口审计

当前后端没有 Controller 层文件，说明 C9 确实是首次引入 API 暴露层。C2~C8 已完成业务服务，但页面级 API 所需的数据聚合、身份状态和前端 DTO 仍需补齐。[1]

### 3.1 登录鉴权缺口

此前 Manus 蓝军报告已指出：当前 schema 和代码中只有 `tokens.user_id` 与 `user_photo_collection.user_id` 字段，未发现用户表、openid 映射表、session 表或 auth/login 服务。这意味着 API-0 不只是简单 Controller，而是需要明确 code 如何换 openid、后端如何生成稳定用户标识、以及后续 `/api/me/*` 如何鉴权。[3]

| 检查项 | 当前状态 | C9 建议 |
|---|---|---|
| 用户身份表 | 未发现正式用户身份表 | 新增最小 `user_identity` 表，存储 userId、openidHash、createdAt、lastLoginAt。 |
| 登录态 | 未发现 session/token 机制 | 新增轻量 session token，前端后续请求通过 `Authorization: Bearer <token>` 携带。 |
| 抖音 code 换 openid | 当前无外部 API 配置 | 彩排环境建议使用可测试的 `AuthProvider` 抽象，默认 `MockAuthProvider` 支持固定测试 code；真实抖音换取留配置开关。 |
| `/api/me/photos` 鉴权 | 当前无法从请求解析 userId | 新增 `AuthContext` 或 `HandlerMethodArgumentResolver`，Controller 不接收前端传来的 userId。 |

### 3.2 页面级 DTO 缺口

API-3 成功响应要求返回 `playerNumber`、`playerName`、`teamName`、`photoPreviewUrl` 和 `collected`，但现有 `RedeemResult` 只返回 tokenId、playerId、points、photoAssetId 与 remainingSeconds，无法直接满足页面契约。[4] 因此 C9 不应把 `RedeemResult` 原样暴露给前端，而应新增页面级响应 DTO 或查询 Facade。

API-4 也不是现成能力。现有 `UserPhotoCollectionMapper` 只有 `insert(...)` 和 `countByUserAndToken(...)`，没有按用户列出写真，也没有 join `photo_assets` 与 `players` 返回 `previewUrl`、`playerName`、`createdAt` 的查询。[5]

### 3.3 API-1 与 API-2 聚合缺口

API-1 要求后端聚合 `targetDisplayName`、`targetPopularity`、`teamDisplayName` 和 `teamPopularity`，当前 `CollectStateService` 与 `StatsMapper` 提供的是偏底层的单值能力，尚没有页面级聚合查询。API-2 要求 player tab 按选手 `number` 升序返回，这也需要新增列表查询 Mapper/Facade，并把“不得按 value 排序”写成测试。[2] [3]

| API | 现有能力 | 缺口 |
|---|---|---|
| API-1 首页 | CollectState 可读当前 mode/targetId/roundId，StatsMapper 有部分单值查询 | 缺 roundName、targetDisplayName、目标人气聚合、teamDisplayName 和首页 DTO。 |
| API-2 player tab | players、player_round、player_round_stats 表齐备 | 缺按 number 升序的列表查询与合规测试。 |
| API-2 team tab | team_round_stats 表齐备 | 需要明确按 team_id 或配置顺序，而非 team_popularity 排序。 |
| API-2 spy tab | player_round_stats.spy_popularity 存在 | 需要确认 spyTabEnabled 规则；建议 C9 固定 false 或按明确业务条件判断。 |

## 四、Manus 既有想法与本次修订后的主张

用户特别提醒：C9 模块在此前对话中，Manus 提出过新的想法，需要结合 Claude 审查意见一起反馈。以下是我对这些想法的重新归纳，并按“采纳、收敛、后置”三类处理。

| Manus 既有想法 | 当前处理建议 | 理由 |
|---|---|---|
| 登录态不能靠前端传 userId，需后端换取并保存身份 | 采纳 | Claude 也明确 user_id 从登录态取，不从前端参数传，这是 API-3/API-4 安全底线。[1] |
| 统一响应使用外层数值 code，同时保留业务字符串 code | 采纳，但需 Claude 最终确认 | 契约要求 40001~40005 数值码；现有 `RedeemResult` 使用字符串 code。建议 `{ code, message, data: { businessCode } }`。 |
| API-1 liveStatus 只有 active 才是 live | 建议采纳 | 避免 upcoming 轮次被误显示为正在直播；upcoming 仍可用于卡密核销入账策略，但不应影响首页 live 状态。 |
| API-2 排序必须写成自动化合规测试 | 强烈采纳 | 这是合规红线，不是 UI 喜好。测试应证明 value 最大的选手不能排到前面。 |
| API-4 我的写真必须进 C9 测试 | 采纳 | API-4 是核销闭环的一部分，不能只插入不展示。 |
| C9 增加接口预留和埋点 | 收敛采纳 | C9 不应引入复杂埋点系统，但建议做轻量 `request_id`、统一响应、错误码统计预留；完整运营埋点后置。 |
| 直接做完整抖音真实登录 | 后置或抽象 | 没有外部配置与测试环境时，硬接真实抖音 API 会拖慢 C9。建议用 `AuthProvider` 抽象，先保证 Controller 契约和测试闭环。 |

## 五、建议新增代码结构

C9 建议采用“Controller + Application/Facade + Mapper 查询 + DTO + 全局异常处理”的分层。Controller 不写业务逻辑，只做请求解析、登录态读取、调用服务、返回 `ApiResponse`。

| 层级 | 拟新增内容 | 说明 |
|---|---|---|
| 通用响应 | `ApiResponse<T>` | 统一 `{ code, message, data }`。 |
| 全局异常 | `GlobalExceptionHandler` | 把参数错误、未登录、业务异常映射为稳定 code/message。 |
| 鉴权 | `AuthController`、`AuthService`、`AuthProvider`、`UserSessionService` | 支持 API-0 登录和 API-3/API-4 用户身份读取。 |
| 页面聚合 | `LiveHomeService`、`PopularityBoardService`、`PhotoQueryService`、`RedeemViewService` | 只做查询与 DTO 组装，不改 C2~C8 核心业务。 |
| Controller | `LiveController`、`PopularityController`、`TokenController`、`MeController` | 对应 API-1~API-4。 |
| Mapper | `UserIdentityMapper`、`UserSessionMapper`、`LiveHomeMapper`、`PopularityBoardMapper`、`PhotoQueryMapper`、`RedeemViewMapper` | 最小新增查询能力。 |
| 测试 | `AuthControllerC9Test`、`LiveHomeControllerC9Test`、`PopularityBoardControllerC9Test`、`TokenRedeemControllerC9Test`、`MyPhotosControllerC9Test` | 使用 MockMvc 覆盖正常与关键异常路径。 |

## 六、API-0 登录方案

API-0 是 C9 最需要 Claude 裁定的部分。正式契约写明后端用 `tt.login` 返回的 code 调抖音接口换 openid，并以 openid 作为 user_id。[2] 但当前项目没有抖音 AppID、Secret、外部调用配置和用户表。为了保证 C9 可测试、可彩排、可上线扩展，我建议引入 `AuthProvider` 抽象。

| 方案 | 实现 | 优点 | 风险 |
|---|---|---|---|
| A：C9 直接接真实抖音接口 | AuthService 内直接 HTTP 调用抖音 code2session | 接近最终生产 | 依赖外部配置与网络；测试复杂；彩排不稳定。 |
| B：AuthProvider 抽象 + MockAuthProvider 默认 | C9 实现接口，测试和彩排用 mock，生产配置切 DouyinAuthProvider | 可测试、可替换、风险低 | 需要后续在上线前补真实 provider 配置。 |

我建议采用**方案 B**。C9 先保证 API 契约和登录态链路闭环：`POST /api/auth/login` 收到 code 后，通过 `AuthProvider.exchange(code)` 得到 openid；对 openid 做不可逆脱敏生成 userId；写入 `user_identity`；生成 session token，写入 `user_session`；返回 `data.userId` 和 `data.isNewUser`，并建议把 token 放在 `data.token` 中供前端保存。

这里需要 Claude 裁定：契约当前只写 `data: { userId, isNewUser }`，没有写 token。如果后续请求不从 cookie/session 中取，前端就无法携带登录态。我的建议是**兼容增加 `data.token` 字段**，不破坏既有字段；后续 API-3/API-4 通过 `Authorization: Bearer <token>` 取 userId。

## 七、统一响应与错误码方案

契约要求通用响应 `{ code, message, data }`，成功 code=0；API-3 失败要返回 40001~40005。[2] 现有 `RedeemResult` 失败语义是字符串 code，例如 `invalid_format`、`not_found`、`already_used`、`locked`、`round_not_available`。[4]

建议采用以下映射，既满足 Claude 数值码，也保留后端业务语义，便于前端固定文案展示。

| RedeemResult.code | API code | message 建议 | data |
|---|---:|---|---|
| `invalid_format` | 40001 | 卡密格式错误 | `{ "businessCode": "invalid_format" }` |
| `not_found` | 40002 | 卡密不存在或不可用 | `{ "businessCode": "not_found" }` |
| `already_used` | 40003 | 该卡密已被核销 | `{ "businessCode": "already_used" }` |
| `locked` | 40004 | 连续错误次数过多，请稍后再试 | `{ "businessCode": "locked", "remainingSeconds": n }` |
| `round_not_available` | 40005 | 当前无可用轮次，请联系工作人员 | `{ "businessCode": "round_not_available" }` |

全局异常处理建议只处理通用异常和鉴权异常；API-3 的五类业务错误建议由 `TokenController` 明确映射，因为它们是前端固定文案依赖的契约，不应被泛化异常处理吞掉。

## 八、API-1 首页直播状态方案

API-1 应返回前端可直接渲染的数据，而不是底层表字段。建议新增 `LiveHomeService`，聚合 active round、collect_state、players/teams/player_round 和 stats 表。

| 字段 | 建议实现规则 |
|---|---|
| `liveStatus` | 只有存在 active round 时为 `live`，否则为 `idle`。upcoming 不应显示为 live。 |
| `roundId/roundName` | active round 的 ID 与 name；无 active 时可为 null。 |
| `currentMode` | 有 collect_state 时用其 mode；无状态时为 `none`。 |
| `targetDisplayName` | player/spy 模式显示“{number}号 {name} {teamName}”；team 模式显示 teamName；pool 模式显示“赛事总池”。 |
| `targetPopularity` | player 模式取 individual_popularity；spy 模式取 spy_popularity；team 模式取 team_popularity；pool 模式取 pool_popularity。 |
| `teamDisplayName/teamPopularity` | 若当前目标能关联团队则返回团队；否则可为 null/0。 |
| `spyChannelOpen` | C9 建议固定 false，等 C13 识破模块定稿后再打开。 |
| `updatedAt` | 取 collect_state.updated_at 或当前服务时间，返回 Unix 秒或毫秒需 Claude 确认。 |

需要 Claude 确认 `updatedAt` 的单位。契约示例写 `1781234567`，更像 Unix 秒而非毫秒。我建议 C9 返回**Unix 秒**，并在 DTO 和测试中固定。

## 九、API-2 人气看板方案

API-2 是合规风险最高的接口。Claude 明确要求 player tab 按 number 升序，绝不能按 value 排序。[1] 我建议将该约束写成测试：插入 1、2、3 号选手，其中 3 号人气最高，接口仍必须返回 1、2、3 顺序。

| tab | 查询策略 | 排序规则 |
|---|---|---|
| `player` | join players + player_round + teams + player_round_stats | `players.number ASC`，禁止按 value。 |
| `team` | join teams + team_round_stats | 建议 `team_id ASC` 或团队名称配置顺序，禁止按 team_popularity。 |
| `spy` | join players + player_round + teams + player_round_stats.spy_popularity | C9 建议 `spyTabEnabled=false`；若仍返回 items，也按 number ASC。 |

需要 Claude 确认 team tab 的排序。此前 Manus 建议按 team_id 或配置顺序，不按 value。若无额外配置，建议 C9 使用 `team_id ASC`。

## 十、API-3 卡密核销方案

API-3 不应改 `TokenService.redeem` 的核心业务流程。Controller 从登录态取 userId，读取请求体 token，调用 `TokenService.redeem(token, userId, "miniapp")`，然后根据结果包装响应。

成功时，需要新增 `RedeemViewMapper` 或 `RedeemViewService` 补齐前端字段：`playerNumber`、`playerName`、`teamName`、`points`、`photoAssetId`、`photoPreviewUrl`、`collected`。这些字段需要 join `tokens`、`players`、`player_round`、`teams`、`photo_assets` 和 `user_photo_collection`。现有 `RedeemResult` 字段不足，不能直接返回。[4]

建议 API-3 成功响应如下：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "playerNumber": 3,
    "playerName": "林夏",
    "teamName": "A组",
    "points": 19900,
    "photoAssetId": "photo_p3_0001",
    "photoPreviewUrl": "https://...",
    "collected": true
  }
}
```

## 十一、API-4 我的写真方案

API-4 从登录态取 userId，查询 `user_photo_collection`，join `photo_assets` 和 `players`，返回 `{ total, items }`。现有 `UserPhotoCollectionMapper` 只有 insert 和 count，因此需要新增列表查询能力。[5]

建议返回字段严格贴合契约：`assetId`、`previewUrl`、`playerName`、`createdAt`。测试必须覆盖：未登录拒绝、无收藏返回 total=0/items=[]、有收藏返回完整字段、用户隔离不串数据。

## 十二、接口预留与埋点的收敛建议

此前我建议在 H5/小程序开发初期就预留数据埋点和接口扩展，避免后续“屎山代码”。结合 Claude 对 C9 的边界要求，我建议在 C9 **只做轻量预留，不做完整运营埋点系统**。

| 预留项 | C9 建议 | 理由 |
|---|---|---|
| `requestId` | 全局响应可选加入，或日志中生成 | 方便联调和排查错误。 |
| API 访问日志 | 建议暂不建表，只在日志中记录 method/path/code/userIdHash | 避免 C9 范围膨胀。 |
| 数据分析事件表 | 后置到运营分析任务卡 | C9 重点是接口契约和稳定性。 |
| DTO 版本兼容 | 响应字段只增不删 | 方便 C10/C11 前端联调。 |
| C13 识破预留 | API-1 返回 `spyChannelOpen=false` | 保证 UI 可灰置，避免实现未定 API-5。 |

需要 Claude 确认的是：`ApiResponse` 是否允许额外带 `requestId`。如果担心偏离契约，C9 可只在日志中生成 requestId，不放进响应体。

## 十三、建议测试矩阵

C9 测试建议使用 MockMvc + H2。它应覆盖 Controller、统一响应包、鉴权、页面 DTO、排序合规和 API-3 错误码，而不是重复测试 C2~C8 的 Service 内部细节。

| 测试类 | 覆盖接口 | 必测场景 |
|---|---|---|
| `AuthControllerC9Test` | API-0 | 正常 code 登录；重复登录同一 openid 稳定 userId；无效 code 返回错误；返回 token。 |
| `LiveHomeControllerC9Test` | API-1 | active 轮次为 live；无 active 为 idle；player/team/pool 场控展示正确；spyChannelOpen=false。 |
| `PopularityBoardControllerC9Test` | API-2 | player tab 按 number 升序；team tab 不按 value；spy tab 未激活灰置；空数据 items=[]。 |
| `TokenRedeemControllerC9Test` | API-3 | 成功 DTO 字段完整；40001~40005 五类错误码；locked 带 remainingSeconds；未登录拒绝。 |
| `MyPhotosControllerC9Test` | API-4 | 无收藏空列表；有收藏字段完整；用户间隔离；未登录拒绝。 |

## 十四、需要 Claude 裁定的问题

| 编号 | 问题 | Manus 建议 |
|---|---|---|
| Q1 | API-0 是否允许在 `data` 中增加 `token` 字段？ | 建议允许。否则后续 API-3/API-4 无法稳定从登录态取 userId。 |
| Q2 | C9 彩排阶段是否采用 `AuthProvider` 抽象 + MockAuthProvider，真实抖音 code2session 后置配置？ | 建议采用，保证可测试和进度稳定。 |
| Q3 | 是否新增最小 `user_identity` 与 `user_session` 表？ | 建议新增，避免 userId/session 只存在内存导致重启丢失。 |
| Q4 | API-3 错误响应是否采用外层数值 code + `data.businessCode` 字符串？ | 建议采用，兼容契约数值码和现有 `RedeemResult` 字符串语义。 |
| Q5 | API-1 `liveStatus` 是否定义为仅 active round 才 live？ | 建议是；upcoming 不应显示直播中。 |
| Q6 | API-1 `updatedAt` 单位是 Unix 秒还是毫秒？ | 建议 Unix 秒，因为契约示例更像秒级时间戳。 |
| Q7 | API-2 team tab 排序规则是否用 `team_id ASC`？ | 建议是，禁止按人气值排序。 |
| Q8 | API 响应是否允许加入 `requestId`？ | 若 Claude 认为偏离契约，则仅写日志不入响应。 |

## 十五、拟提交验证物

Claude 确认后，我将按确认后的方案编码，并提交以下验证物。

| 验证物 | 说明 |
|---|---|
| Controller、DTO、Auth、Facade/QueryService、Mapper | API-0~API-4 实现。 |
| `*ControllerC9Test.java` | MockMvc + H2 测试，覆盖正常路径与关键异常路径。 |
| `reports/C9_mvn_test_output_v1.txt` | 完整 `mvn test` 输出。 |
| `C9_API_Controller_Fix_Report_v1.md` | C9 实施说明、测试结果与变更清单。 |

## 十六、结论

我同意 Claude 的 C9 开卡方向：**严格按定稿 API 契约实现 Controller 层**。同时，我认为 C9 的真正风险不在写几个 Controller，而在身份机制、统一响应、页面级 DTO、排序合规测试和“前端不能伪造 userId”。因此建议 C9 采用轻量但完整的鉴权闭环、明确错误码映射、为页面新增查询型 DTO/Mapper，并将排序和鉴权写成自动化测试。待 Claude 对 Q1~Q8 做出裁定后，再开始编码。

## References

[1]: /home/ubuntu/upload/Claude审查_C8通过_C9开卡.md "Claude 审查 — C8 通过，C9 开卡"
[2]: docs/API契约/当前页面级API契约定稿_C9依据_V1.0.md "当前页面级 API 契约定稿 C9 依据 V1.0"
[3]: docs/Manus第二轮_UI_API_测试用例三方对齐复审报告_V1.0.md "Manus 第二轮 UI/API/测试用例三方对齐复审报告 V1.0"
[4]: backend/redface-backend/src/main/java/com/redface/dto/RedeemResult.java "RedeemResult 服务层核销结果"
[5]: backend/redface-backend/src/main/java/com/redface/mapper/UserPhotoCollectionMapper.java "UserPhotoCollectionMapper 当前收藏能力"
[6]: backend/redface-backend/src/main/java/com/redface/mapper/TokenMapper.java "TokenMapper 当前卡密查询能力"
