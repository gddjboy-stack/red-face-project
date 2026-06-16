# C9 API Controller 层实施报告 v1

**作者：Manus AI**  
**日期：2026-06-16**  
**适用范围：直播选秀项目后端 C9 任务卡，API-0 至 API-4、统一响应、鉴权、页面级 DTO、MockMvc/H2 测试**

## 一、执行背景

本次 C9 实施依据 Claude 对 `C9_API_Controller_Implementation_Plan_v1` 的裁定执行。Claude 已批准 C9 编码，并明确 C9 的关键风险不是“写几个 Controller”，而是**身份机制、统一响应、页面级 DTO、排序合规、防止 userId 伪造**。因此本次实现严格按裁定新增 Controller、Facade/QueryService、DTO、Auth、Mapper 和两张身份表，不修改 C2~C8 已通过的核心业务逻辑，不实现 API-5。[1]

C9 的总体策略是：Controller 只接收参数、解析登录态、调用 Service/QueryService 并包装响应；所有业务写操作仍由 C2~C8 的 Service 层完成。页面级查询服务只做多表 join 组装 DTO，不写业务数据、不绕过 `PopularityService.applyChange(...)`。[1]

## 二、Claude 裁定执行情况

Claude 对 C9 方案的 8 个问题做出裁定，其中 7 个采纳，Q8 收敛为 requestId 只写日志、不进入响应体。本次实现逐项落地。

| 裁定项 | Claude 裁定 | 实施结果 |
|---|---|---|
| Q1 | API-0 `data` 增加 `token` 字段 | 已实现 `LoginResponse.userId/isNewUser/token`。 |
| Q2 | `AuthProvider` 抽象 + `MockAuthProvider`，真实抖音 code2session 后置 | 已实现 `AuthProvider` 与默认 `MockAuthProvider`。 |
| Q3 | 新增 `user_identity` 和 `user_session` 表 | 已同步更新 `db/db_schema.sql` 与 `schema-h2.sql`，并更新 schema 测试。 |
| Q4 | API-3 外层数值 code + `data.businessCode` 字符串 | 已实现 40001~40005 与业务字符串双表达。 |
| Q5 | `liveStatus` 仅 active round 为 live | 已实现；无 active 时返回 idle。 |
| Q6 | `updatedAt` 使用 Unix 秒 | 已实现秒级时间戳。 |
| Q7 | team tab 按 `team_id ASC` | 已实现；player/spy 也按 number ASC。 |
| Q8 | requestId 只写日志，不入响应体 | 已实现 `RequestLoggingFilter`，日志记录 requestId/method/path/status/costMs。 |

## 三、实际代码变更

本次新增 C9 API 层、鉴权层、统一响应、页面 DTO、只读查询服务和 MockMvc 测试，并对 H2 schema 初始化进行了幂等化处理。幂等化处理的原因是 C9 新增 MockMvc 测试后会引入不同 Spring 测试上下文，H2 命名内存库可能被重复初始化；`schema-h2.sql` 顶部增加 `DROP TABLE IF EXISTS` 仅影响测试环境，不改变主库 schema 语义。

| 类别 | 文件或目录 | 说明 |
|---|---|---|
| 统一响应与异常 | `com.redface.api.*` | `ApiResponse`、`ApiException`、`UnauthorizedException`、`GlobalExceptionHandler`。 |
| 鉴权 | `com.redface.auth.*` | `AuthProvider`、`MockAuthProvider`、`AuthService`、`CurrentUser`、`CurrentUserArgumentResolver`。 |
| Web 配置 | `com.redface.web.*` | 注册当前用户解析器；服务端日志生成 requestId。 |
| Controller | `com.redface.controller.*` | API-0 登录、API-1 首页、API-2 看板、API-3 卡密核销、API-4 我的写真。 |
| 页面 DTO | `LiveHomeResponse`、`PopularityBoardResponse`、`RedeemResponse`、`MyPhotosResponse` 等 | 严格服务前端页面级响应。 |
| 只读查询 | `C9QueryMapper`、`LiveHomeService`、`PopularityBoardService`、`RedeemViewService`、`PhotoQueryService` | 多表 join 组装 DTO，只读不写。 |
| 用户身份表 | `user_identity`、`user_session` | 主 schema 和 H2 schema 同步新增。 |
| 测试 | `AuthControllerC9Test`、`LiveHomeControllerC9Test`、`PopularityBoardControllerC9Test`、`TokenRedeemControllerC9Test`、`MyPhotosControllerC9Test` | 覆盖 Claude 指定的 ControllerC9Test 验证物。 |

## 四、API 实现说明

API-0 `POST /api/auth/login` 使用 `AuthProvider` 抽象换取 openid。C9 当前默认使用 `MockAuthProvider`，将 code 稳定映射为 mock openid；随后 `AuthService` 生成 openid hash 与脱敏 userId，写入 `user_identity`，再生成 Bearer token 写入 `user_session`。返回字段包含 `userId`、`isNewUser` 与 Claude 批准新增的 `token`。

API-1 `GET /api/live/home` 只在存在 active round 时返回 `live`，否则返回 `idle`。首页聚合服务读取当前 `collect_state`，根据 player/team/spy/pool 模式组装 `targetDisplayName`、目标人气、团队名称和团队人气；`spyChannelOpen` 在 C9 固定为 false，等待 C13 识破模块定稿。

API-2 `GET /api/popularity/board` 是本卡合规重点。player 与 spy tab 均按 `players.number ASC` 返回；team tab 按 `team_id ASC` 返回，明确不按人气值排序。测试中专门插入 3 号人气最高的选手，并断言接口仍返回 1→2→3。

API-3 `POST /api/tokens/redeem` 从 `Authorization: Bearer <token>` 解析 userId，不接受前端传 userId。Controller 调用既有 `TokenService.redeem(...)`，成功后通过只读查询补齐 `playerNumber`、`playerName`、`teamName`、`photoPreviewUrl` 和 `collected`；失败时按 Claude 裁定映射 40001~40005。

| `RedeemResult.code` | API code | data.businessCode | 说明 |
|---|---:|---|---|
| `invalid_format` | 40001 | `invalid_format` | 卡密格式错误。 |
| `not_found` | 40002 | `not_found` | 卡密不存在或不可用。 |
| `already_used` | 40003 | `already_used` | 卡密已被核销。 |
| `locked` | 40004 | `locked` | 连续错误次数过多，并返回 `remainingSeconds`。 |
| `round_not_available` | 40005 | `round_not_available` | 当前无可用轮次。 |

API-4 `GET /api/me/photos` 同样从 Bearer 登录态解析 userId，查询 `user_photo_collection` 并 join `photo_assets` 与 `players`，返回当前用户自己的写真列表，测试覆盖用户隔离。

## 五、测试覆盖情况

本次新增 19 个 C9 MockMvc/H2 测试，覆盖 API-0 至 API-4 的正常路径、关键异常路径和合规红线。连同 C2~C8 既有测试，全量测试共 51 个，全部通过。

| 测试类 | 覆盖重点 | 结果 |
|---|---|---:|
| `AuthControllerC9Test` | 正常登录、重复登录稳定 userId、无效 code、未登录访问 `/api/me/photos` | 通过 |
| `LiveHomeControllerC9Test` | active round 返回 live；无 active 返回 idle；player 场控展示聚合字段 | 通过 |
| `PopularityBoardControllerC9Test` | player 按 number ASC；team 按 team_id ASC；spy disabled 且按 number ASC | 通过 |
| `TokenRedeemControllerC9Test` | 成功页面 DTO；40001~40005 五个错误码；未登录拒绝 | 通过 |
| `MyPhotosControllerC9Test` | 空列表、有收藏、用户隔离、未登录拒绝 | 通过 |
| 既有 C2~C8 测试 | Service 核心业务回归 | 通过 |

## 六、全量测试结果

已在 `backend/redface-backend` 目录执行全量测试命令，并将完整输出保存到 `reports/C9_mvn_test_output_v1.txt`。

```bash
mvn test
```

最终测试结果如下。项目测试总数从 C8 的 32 个增加到 C9 的 51 个，全部通过。

```text
[INFO] Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  7.215 s
[INFO] Finished at: 2026-06-16T04:52:44Z
[INFO] ------------------------------------------------------------------------
```

## 七、边界确认

本次实现没有修改 C2~C8 Service 核心业务逻辑，没有在 Controller 或查询层写业务数据，没有接受前端传入 userId 参数，没有实现 API-5。新增的页面级查询均为只读 join，API-3 的核销写操作仍走 `TokenService.redeem(...)`，而人气入账仍由既有 Service 流程完成。

| Claude 禁止项 | 是否触碰 | 说明 |
|---|---:|---|
| 改 C2~C8 Service 核心业务逻辑 | 否 | 仅新增 API 层与只读查询。 |
| 改已有 16 张业务表结构 | 否 | 仅新增 `user_identity`、`user_session` 两张表。 |
| Controller 或查询层写业务/改人气值 | 否 | 查询层只读，核销仍调用既有 Service。 |
| 接受前端传入 userId 参数 | 否 | 使用 `@CurrentUser` 从 Bearer token 注入。 |
| 实现 API-5 | 否 | 识破提交留 C13。 |
| requestId 放进响应体 | 否 | 只写服务端日志。 |

## 八、上线待办

C9 按 Claude 裁定使用 `AuthProvider` 抽象和 `MockAuthProvider` 默认实现，以确保彩排和测试稳定。上线前仍需要配置真实抖音 AppID/Secret 并实现或启用 `DouyinAuthProvider`，这是依赖企业主体与 API 权限的外部待办，不应阻塞当前 C9 彩排闭环。[1]

## 九、结论

C9 已按 Claude 裁定完成 API-0 至 API-4 实现。当前后端已具备前端联调所需的统一响应、登录态、首页状态、人气看板、卡密核销和我的写真接口。全量测试结果为 **Tests run: 51, Failures: 0, Errors: 0, Skipped: 0**。从功能、合规排序和回归测试角度看，C9 已具备提交 Claude 复核的条件。

## References

[1]: /home/ubuntu/upload/Claude裁定_C9技术方案确认.md "Claude 裁定 — C9 技术方案确认"
[2]: C9_API_Controller_Implementation_Plan_v1.md "C9 API Controller 技术实施方案 v1"
[3]: backend/redface-backend/src/main/java/com/redface/controller/AuthController.java "API-0 AuthController"
[4]: backend/redface-backend/src/main/java/com/redface/controller/TokenController.java "API-3 TokenController"
[5]: backend/redface-backend/src/main/java/com/redface/mapper/C9QueryMapper.java "C9 页面级只读查询 Mapper"
[6]: backend/redface-backend/src/test/java/com/redface/PopularityBoardControllerC9Test.java "C9 排序合规测试"
[7]: reports/C9_mvn_test_output_v1.txt "C9 Maven 全量测试输出 v1"
