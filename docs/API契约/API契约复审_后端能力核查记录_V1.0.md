# API 契约复审：后端能力核查记录 V1.0

## 核查范围

本记录基于 Claude《页面级 API 契约定稿》中的 API-0 至 API-5，对当前仓库中已存在的 Controller、Service、Mapper、DTO 和数据库 schema 进行核查。

## 初步结论

当前仓库仍未发现 `@RestController`、`@Controller`、`@RequestMapping`、`@GetMapping` 或 `@PostMapping` 等 Controller 层注解。因此，Claude 将 API-0 至 API-4 定为 C9 开发依据是合理的，且 C9 不能只做薄 Controller，还必须补足若干页面级聚合查询与 DTO。

| API | 现有基础 | 主要缺口 | 复审判断 |
| --- | --- | --- | --- |
| API-0 `POST /api/auth/login` | 当前业务表以 `user_id` 字符串承载用户身份，`tokens` 与 `user_photo_collection` 均使用该字段。 | 未发现 users/openid/session/auth/login 相关表或服务；需要新增抖音 code 换 openid 的服务和用户身份存储或稳定映射方案。 | 契约必要，但不是简单 Controller，需要补身份模块最小实现。 |
| API-1 `GET /api/live/home` | `CollectStateService` 与 `CollectStateMapper` 可维护当前 `mode/targetId/roundId`；`RoundService` 可找 active/upcoming 轮次。 | 缺 `roundName`、`targetDisplayName`、`teamDisplayName`、`targetPopularity`、`teamPopularity`、`spyChannelOpen` 的聚合查询与 DTO。 | 可实现，但必须新增组合服务或 Mapper join。 |
| API-2 `GET /api/popularity/board` | `players`、`teams`、`player_round`、`player_round_stats`、`team_round_stats` 均有 schema；`StatsMapper` 有单值查询能力。 | 缺按 number 升序的列表查询；缺 player/team/spy 三个 tab 的统一 DTO；必须防止按 value 排序。 | 契约正确，需新增列表 Mapper 和排序测试。 |
| API-3 `POST /api/tokens/redeem` | `TokenService.redeem` 已支持格式校验、防爆破、轮次预检查、原子抢占、人气入账、写真自动收藏。 | 当前 `RedeemResult` 只返回 `playerId/points/photoAssetId/remainingSeconds`；缺 `playerNumber/playerName/teamName/photoPreviewUrl/collected`。错误码当前为字符串，Claude 契约为数值码 + 字符串枚举，需要统一。 | 核心最成熟，但响应 DTO 需增强。 |
| API-4 `GET /api/me/photos` | `user_photo_collection` 与 `photo_assets` 表存在，核销成功后可自动收藏。 | `UserPhotoCollectionMapper` 只有 insert 和 count，无按用户列表查询；缺 join `photo_assets` 与 `players` 的预览卡片 DTO。 | 简单但需新增查询，不是现成能力。 |
| API-5 `POST /api/suspicion/submit` | `suspicion_votes` 表已存在。 | 无 Mapper/Service/Controller；候选集合、活动状态、重复提交、统计进度均未实现。 | P1 后置合理，C9 不应实现。 |

## 蓝军重点

Claude 契约方向整体正确，但 C9 的真实工作量不应被低估。API-0、API-1、API-2、API-3、API-4 都不仅是“加 Controller”，而是涉及身份、聚合查询、响应 DTO、错误码映射和测试用例的补齐。建议 C9 验收标准不要只看接口能返回 200，而要看 UI 所需字段是否完整、排序是否合规、错误码是否被测试覆盖。
