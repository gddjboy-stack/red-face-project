# C7 LiveDataService 技术实施方案 v1（提交 Claude 确认）

**作者：Manus AI**  
**日期：2026-06-15**  
**状态：待 Claude 确认后再编码**  
**适用范围：直播选秀项目后端 C7 任务卡，LiveDataService（直播接入 + 模拟器）**

## 一、背景与执行纪律

Claude 已复核 commit `a213b17`，确认 **C6 正式关闭**，并给出 C7 开卡要求。根据项目协作规则，凡涉及技术判断、修复方案、架构取舍或测试设计，Manus 先形成 Markdown 文件并上传 GitHub，由 John 转发 Claude 确认，确认后再实施代码变更。本文件即为 C7 编码前的技术方案确认稿。

> 本方案不会直接修改业务代码。待 Claude 确认后，再按确认后的范围创建 `LiveDataService`、必要 DTO 与 JUnit/H2 测试。

C7 的业务目标来自开发指导手册 5.3：直播数据接入包括礼物逐条事件、点赞/留言总增量事件，以及彩排兜底模拟器；模拟器行为必须与真实事件一致。[1]

## 二、C7 需求复述

C7 的核心不是新增一套人气计算规则，而是新增一个**直播/模拟事件入口适配层**。它应把外部直播事件或后台模拟注入转换为 `PopularityChangeRequest`，并统一委托 `PopularityService.applyChange(...)` 入账。现有 `PopularityService` 已经是人气值变更的唯一入口，其内部负责换算、归属判定、写流水幂等、更新统计和返回结果。[2]

| 方法 | 输入模型 | 目标行为 | 归属方式 | 幂等键规则 |
|---|---|---|---|---|
| `onGiftEvent` | 礼物逐条事件 | 将抖币礼物换算为人气值并入账 | 显式 `targetType="player"`，`targetId=playerId` | `gift_` + `msgId` |
| `onMetricDelta` | 点赞/留言总增量 | 将点赞或留言增量入账 | `targetType=null`，由引擎读取当前场控状态 | 调用方传入 `idemKey`，建议服务层统一加来源前缀或校验非空 |
| `simulateInject` | 后台手动模拟注入 | 行为与真实事件一致，用于彩排兜底 | 按 eventType 分派到礼物或指标增量路径 | `sim_` + 时间戳 + 随机数 |

## 三、现有代码依据

现有 `PopularityService.applyChange(...)` 已支持 C7 所需的所有下游能力。它会根据 `source` 完成换算：`gift` 按抖币换算，`like` 和 `comment` 按指标增量换算；如果 `targetType` 为空且来源为 `like/comment`，它会调用 `CollectStateService.getCurrent()` 获取当前场控状态进行自动归属。[2]

现有 `CollectStateService` 负责设置和读取当前场控目标。其 `setCollectTarget(mode, targetId, roundId, operatorId)` 支持 `player/team/spy/pool` 四种模式，`getCurrent()` 返回当前场控状态，正好支撑 C7 的点赞和留言增量归属。[3]

现有 `TokenService.redeem(...)` 已提供一个成熟的“入口服务适配器”样板：它先通过 `RoundService.getCurrentAccrualRoundId()` 取得入账轮次，再构造 `PopularityChangeRequest`，最后调用 `popularityService.applyChange(req)`。C7 的礼物事件与模拟器可以复用这一模式，避免在入口层重复实现人气引擎逻辑。[4]

现有 C3 集成测试已经验证了 `like/comment` 在 `targetType` 为空时会按场控目标正确入账。例如，场控设为 team 时，like 增量进入 team 统计；场控设为 pool 时，comment 增量进入 pool 统计。这为 C7 的测试设计提供了直接依据。[5]

## 四、拟新增代码结构

本次建议新增的代码非常小，保持 C7 是“入口适配层”，不直接操作 Mapper，不直接写 `popularity_ledger` 或 stats 表。

| 拟新增文件 | 类型 | 说明 |
|---|---|---|
| `backend/redface-backend/src/main/java/com/redface/service/LiveDataService.java` | Service | C7 核心服务，提供 `onGiftEvent`、`onMetricDelta`、`simulateInject`。 |
| `backend/redface-backend/src/main/java/com/redface/dto/SimResult.java` | DTO | 模拟器返回对象，建议包含 success、duplicated、eventType、popularityValue、targetType、targetId、roundId、idempotencyKey、message。 |
| `backend/redface-backend/src/test/java/com/redface/LiveDataServiceC7Test.java` | JUnit/H2 测试 | 验证模拟注入、礼物入账、幂等重复。 |

暂不建议新增 Controller。C7 是 service 层任务卡，C9 才是 API 层串联任务；如果现在提前新增 Controller，容易把 C7 和 C9 的边界混在一起。

## 五、核心设计建议

### 5.1 `onGiftEvent` 设计

`onGiftEvent(String msgId, int playerId, long doubiValue, long occurredAt)` 建议返回 `PopularityChangeResult`，而不是 `void`。开发手册示例写的是 `void`，但当前后端服务层测试和调试需要可断言结果；`PopularityChangeResult` 已能表达 success、duplicated、popularityValue、targetType、targetId 和 roundId。[6]

建议逻辑如下：校验 `msgId` 非空、`playerId > 0`、`doubiValue > 0`；通过 `RoundService.getCurrentAccrualRoundId()` 取得轮次；若无可用轮次，则抛出明确异常或返回失败结果。然后构造 `PopularityChangeRequest`，设置 `targetType="player"`、`targetId=playerId`、`source="gift"`、`rawValue=doubiValue`、`roundId`、`idempotencyKey="gift_" + msgId`、`occurredAt`。

这里有一个需要 Claude 裁定的小问题：`PopularityChangeResult` 目前没有通用失败工厂方法，只支持 `success` 和 `duplicated`。[6] 为保持 C7 变更最小，我建议**无轮次或参数非法时抛出 `IllegalArgumentException/IllegalStateException`**，不改 `PopularityChangeResult` 结构。

### 5.2 `onMetricDelta` 设计

`onMetricDelta(String metricType, long delta, long occurredAt, String idemKey)` 同样建议返回 `PopularityChangeResult`。它不传 `targetType` 与 `targetId`，让 `PopularityService` 自动读取场控状态归属。`metricType` 按开发手册映射：`like_delta` 转为 `source="like"`，`comment_delta` 转为 `source="comment"`。[1]

建议服务层校验 `delta > 0`、`idemKey` 非空、`metricType` 只能为 `like_delta/comment_delta`。轮次建议仍由 `RoundService.getCurrentAccrualRoundId()` 提供，并设置到 request 中。虽然 `PopularityService` 在自动归属时可以使用 `CollectState` 的 roundId，但入口层显式使用当前可入账轮次，能与 `TokenService` 的现有做法保持一致。[4]

需要 Claude 确认的问题是：当 `CollectState` 自带 roundId 与 `RoundService.getCurrentAccrualRoundId()` 返回值不一致时，C7 应以哪个为准？我倾向于**以入口层当前可入账轮次为准**，因为直播事件发生时应归入当前轮次；但如果项目业务定义认为场控状态绑定的 roundId 更权威，则 `onMetricDelta` 可以不设置 roundId，让 `PopularityService` 使用 `CollectState.roundId`。[2] [3]

### 5.3 `simulateInject` 设计

`simulateInject(String eventType, long value, String operatorId)` 是彩排生命线，应尽量稳定、直观、可测试。它应生成 `sim_` 前缀幂等键，并把模拟事件转发到与真实事件完全相同的路径，而不是复制入账逻辑。

建议支持以下 `eventType`：

| eventType | 行为 | 备注 |
|---|---|---|
| `like_delta` | 调用 `onMetricDelta("like_delta", value, now, simKey)` | 按当前场控目标归属。 |
| `comment_delta` | 调用 `onMetricDelta("comment_delta", value, now, simKey)` | 按当前场控目标归属。 |
| `gift` | 调用礼物路径 | 这里需要 playerId；但当前签名只有 eventType、value、operatorId，缺少 playerId。 |

礼物模拟存在一个接口设计缺口：手册中的 `simulateInject(String eventType, long value, String operatorId)` 没有 `playerId` 参数，但礼物事件必须明确归属到选手。因此我建议两种方案供 Claude 裁定。

| 方案 | 接口 | 优点 | 风险 |
|---|---|---|---|
| A：严格按手册签名 | `simulateInject(String eventType, long value, String operatorId)` | 完全贴合手册 | 无法可靠模拟礼物给指定选手，除非默认读场控 player 目标；这会偏离“礼物逐条事件明确 playerId”的真实行为。 |
| B：增加重载或参数 | `simulateInject(String eventType, long value, Integer targetId, String operatorId)` | 可准确模拟礼物给选手，也可模拟指标增量 | 比手册骨架多一个参数，但更适合彩排后台使用。 |

我的建议是采用**方案 B**，同时保留手册签名作为便捷方法：当 eventType 为 `gift` 且未提供 targetId 时，如果当前场控模式是 `player`，可使用当前场控 targetId；否则抛出明确异常。这样既兼容手册，又保证礼物模拟的目标明确。

## 六、测试设计建议

C7 测试建议继续使用 Spring Boot + H2 集成测试风格，与 C2~C5 保持一致，而不是改成 Mockito 单元测试。这样可以真实验证 `LiveDataService → PopularityService → Mapper → H2` 的主链路，符合“彩排生命线”的要求。

| 测试用例 | 前置条件 | 操作 | 断言 |
|---|---|---|---|
| 模拟注入点赞进入 player | 设置场控为 `player`，插入选手与轮次 | `simulateInject("like_delta", 77, operator)` | 返回 success；targetType=player；player individual popularity 增加 77。 |
| 模拟注入点赞进入 team | 设置场控为 `team`，插入团队与轮次 | `simulateInject("like_delta", 88, operator)` | 返回 success；targetType=team；team popularity 增加 88。 |
| 模拟注入礼物 1000 抖币 | 插入选手与轮次 | `simulateInject("gift", 1000, playerId, operator)` 或礼物路径 | 返回 success；popularityValue=100000；player stats 增加 100000。 |
| 同一幂等键重复注入 | 使用 `onMetricDelta(..., sameIdemKey)` 调用两次 | 第二次重复 | 第一次 success；第二次 duplicated；统计只增加一次。 |

此处最后一个用例建议直接测 `onMetricDelta` 的固定幂等键，而不是 `simulateInject`，因为 `simulateInject` 每次自动生成随机幂等键，不适合验证重复幂等。

## 七、建议的实现边界

C7 不应修改 `PopularityService`、`CollectStateService`、`StatsMapper`、`PopularityLedgerMapper` 的现有逻辑。C7 只应通过构造 `PopularityChangeRequest` 调用 `PopularityService.applyChange(...)` 来完成入账。任何直接写 stats 表或 ledger 表的做法都应视为越界。

| 不建议做的事 | 原因 |
|---|---|
| 在 `LiveDataService` 中直接调用 Mapper | 破坏 `PopularityService` 作为唯一人气变更入口的规则。[2] |
| 修改 C2~C5 已通过测试 | C6 教训表明跨卡测试应避免无关改动。 |
| 提前新增 API Controller | C9 才是 API 层任务，C7 应聚焦 Service 和模拟器。 |
| 为模拟器复制一套人气计算逻辑 | 模拟器必须与真实事件完全一致，应走同一路径。[1] |

## 八、需要 Claude 确认的问题

| 编号 | 问题 | Manus 建议 |
|---|---|---|
| Q1 | `onGiftEvent` / `onMetricDelta` 是否可返回 `PopularityChangeResult`，而不是手册骨架中的 `void`？ | 建议返回，方便测试与后续 API 层复用。 |
| Q2 | `onMetricDelta` 的 roundId 应使用 `RoundService` 当前轮次，还是让 `PopularityService` 使用 `CollectState.roundId`？ | 建议使用 `RoundService` 当前轮次，但请 Claude 裁定。 |
| Q3 | 礼物模拟是否允许给 `simulateInject` 增加 `targetId/playerId` 参数或重载？ | 建议增加重载，避免礼物模拟目标不明确。 |
| Q4 | C7 是否只做 Service + DTO + JUnit，不做 Controller？ | 建议是，Controller 留到 C9。 |
| Q5 | 模拟器自动生成幂等键后，重复幂等测试是否改测 `onMetricDelta` 固定 idemKey？ | 建议是，测试目的更清晰。 |

## 九、拟提交验证物

Claude 确认后，我将按确认后的方案编码，并在完成后提交以下验证物。

| 验证物 | 说明 |
|---|---|
| `LiveDataServiceC7Test.java` | JUnit/H2 集成测试，覆盖模拟点赞归属、模拟礼物入账、重复幂等。 |
| `reports/C7_mvn_test_output_v1.txt` | 完整 `mvn test` 输出。 |
| `C7_LiveDataService_Fix_Report_v1.md` | C7 实施说明、测试结果和变更清单。 |

## 十、结论

C7 应作为轻量但关键的直播事件入口层实现，其核心原则是：**不新增人气算法、不绕过 PopularityService、不直接写数据库、不提前做 API 层**。最需要 Claude 裁定的是模拟礼物接口是否增加 playerId/targetId，以及 `onMetricDelta` 的 roundId 归属来源。待 Claude 确认这些问题后，我再开始编码。

## References

[1]: docs/红颜局中局开发指导手册V2.0完整版.md "开发指导手册 V2.0，第 5.3 LiveDataService 章节"
[2]: backend/redface-backend/src/main/java/com/redface/service/PopularityService.java "PopularityService.applyChange 与自动归属逻辑"
[3]: backend/redface-backend/src/main/java/com/redface/service/CollectStateService.java "CollectStateService 场控状态服务"
[4]: backend/redface-backend/src/main/java/com/redface/service/TokenService.java "TokenService.redeem 入口适配器样板"
[5]: backend/redface-backend/src/test/java/com/redface/PopularityServiceC3Test.java "C3 场控归属集成测试"
[6]: backend/redface-backend/src/main/java/com/redface/dto/PopularityChangeResult.java "PopularityChangeResult 返回结构"
