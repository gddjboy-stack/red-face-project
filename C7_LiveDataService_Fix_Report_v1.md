# C7 LiveDataService 实施报告 v1

**作者：Manus AI**  
**日期：2026-06-16**  
**适用范围：直播选秀项目后端 C7 任务卡，LiveDataService（直播接入 + 模拟器）**

## 一、执行背景

本次 C7 实施依据 Claude 对 `C7_LiveDataService_Implementation_Plan_v1` 的裁定执行。Claude 已明确批准编码，并裁定 C7 的边界为：新增 `LiveDataService`、`SimResult` 和 `LiveDataServiceC7Test`；禁止修改 `PopularityService`、`CollectStateService`、任何 Mapper 现有逻辑、C2~C6 已通过测试，以及禁止提前新增 Controller。[1]

本次实施遵守 C7 的核心原则：**LiveDataService 只做直播事件入口适配层，不新增人气算法，不绕过 PopularityService，不直接写数据库，不提前进入 C9 API 层**。所有真实直播事件和模拟事件均统一转换为 `PopularityChangeRequest`，再委托 `PopularityService.applyChange(...)` 入账。[2]

## 二、Claude 裁定执行情况

Claude 对 5 个关键问题逐项裁定，本次代码已按裁定落实。特别是 Q2：点赞/留言指标增量的 `roundId` 不在入口层设置，而是保持 `null`，交由 `PopularityService` 根据 `CollectState.roundId` 自动归属；礼物事件则通过 `RoundService.getCurrentAccrualRoundId()` 获取当前可入账轮次。这种不一致是设计要求，不是疏漏。[1]

| 裁定项 | Claude 裁定 | 实施结果 |
|---|---|---|
| Q1 | `onGiftEvent` / `onMetricDelta` 返回 `PopularityChangeResult` | 已实现，便于测试和 C9 API 层复用。 |
| Q2 | `onMetricDelta` 不设置 `roundId`，交给场控状态 | 已实现，metric 请求未写入 `request.roundId`。 |
| Q3 | `simulateInject` 增加 `targetId` 参数并保留兼容逻辑 | 已实现四参数方法，并保留三参数兼容重载。 |
| Q4 | C7 只做 Service + DTO + JUnit，不做 Controller | 已遵守，未新增 Controller。 |
| Q5 | 幂等测试用固定 `idemKey` 的 `onMetricDelta` | 已按该方式编写重复幂等测试。 |

## 三、实际代码变更

本次新增 3 个源码/测试文件，未修改 C2~C6 已通过测试，未修改既有 Mapper 和核心人气服务逻辑。新增内容如下表所示。

| 文件 | 类型 | 说明 |
|---|---|---|
| `backend/redface-backend/src/main/java/com/redface/service/LiveDataService.java` | 新增 Service | 实现礼物事件、点赞/留言增量事件，以及彩排模拟注入入口。 |
| `backend/redface-backend/src/main/java/com/redface/dto/SimResult.java` | 新增 DTO | 封装模拟注入结果，包括 success、duplicated、eventType、popularityValue、targetType、targetId、roundId、idempotencyKey、message。 |
| `backend/redface-backend/src/test/java/com/redface/LiveDataServiceC7Test.java` | 新增 JUnit/H2 集成测试 | 覆盖 Claude 要求的 4 个 C7 验收场景。 |

## 四、核心实现说明

`LiveDataService.onGiftEvent(...)` 接收礼物逐条事件，并将其显式归属到 `player`。它校验 `msgId`、`playerId` 和 `doubiValue`，通过 `RoundService.getCurrentAccrualRoundId()` 获取礼物入账轮次，然后构造 `PopularityChangeRequest` 并设置 `idempotencyKey="gift_" + msgId`。这符合 Claude 对礼物事件的裁定：礼物显式归属到选手，不依赖场控状态。[1]

`LiveDataService.onMetricDelta(...)` 接收 `like_delta` 或 `comment_delta` 总增量。该方法只设置 `source`、`rawValue`、`idempotencyKey`、`operatorId`、`reason` 和 `occurredAt`，**不设置 targetType、targetId、roundId**。因此下游 `PopularityService` 会沿用已验证的自动归属链路：读取当前 `CollectState`，并使用其 mode、targetId 和 roundId。[2] [3]

`LiveDataService.simulateInject(...)` 是彩排兜底入口。它自动生成 `sim_` 前缀幂等键，并把模拟事件转发到与真实事件一致的内部入账路径。对于 `gift`，四参数方法允许显式传入 `targetId`；若未传入且当前场控为 `player` 模式，则使用当前场控 targetId；否则抛出明确异常。对于 `like_delta/comment_delta`，它不需要 targetId，继续走场控自动归属。[1]

## 五、测试覆盖情况

本次新增 `LiveDataServiceC7Test`，采用与 C2~C6 一致的 Spring Boot + H2 集成测试风格，真实验证 `LiveDataService → PopularityService → Mapper → H2` 主链路。测试不使用 Mockito，不绕过数据库，符合“彩排生命线”的验证要求。

| 测试用例 | 验证点 | 结果 |
|---|---|---:|
| `simulateLikeDeltaShouldGoToCurrentPlayerTarget` | 模拟点赞按当前场控 player 目标入账 | 通过 |
| `simulateLikeDeltaShouldGoToCurrentTeamTarget` | 模拟点赞按当前场控 team 目标入账 | 通过 |
| `simulateGift1000DoubiShouldAdd100000PopularityToPlayer` | 模拟礼物 1000 抖币给选手增加 100000 人气值 | 通过 |
| `sameIdempotencyKeyShouldOnlyApplyMetricDeltaOnce` | 固定幂等键重复注入只生效一次 | 通过 |

## 六、全量测试结果

已在 `backend/redface-backend` 目录执行全量测试命令，并将完整输出保存到 `reports/C7_mvn_test_output_v1.txt`。

```bash
mvn test
```

最终测试结果如下。C7 新增 4 个测试通过，项目测试总数从 C6 的 20 个增加到 24 个，全部绿灯。

```text
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.061 s
[INFO] Finished at: 2026-06-16T03:10:15Z
[INFO] ------------------------------------------------------------------------
```

| 测试范围 | 结果 |
|---|---:|
| `TokenGeneratorServiceTest` | 通过 |
| `PopularityServiceC3Test` | 通过 |
| `SchemaInitializationTest` | 通过 |
| `LiveDataServiceC7Test` | 通过，4 个新增测试全部成功 |
| `PopularityServiceC4Test` | 通过 |
| `PopularityServiceC2Test` | 通过 |
| `TokenServiceC5Test` | 通过 |
| 全量测试汇总 | `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0` |

## 七、边界确认

本次实施未修改 `PopularityService`、`CollectStateService`、任何 Mapper 现有逻辑，也未修改 C2~C6 已通过测试。C7 没有新增 Controller，API 层仍留给 C9 处理。

| 禁止项 | 是否触碰 | 说明 |
|---|---:|---|
| 修改 `PopularityService` | 否 | 保持人气引擎唯一入口不变。 |
| 修改 `CollectStateService` | 否 | 继续复用已验证的场控状态逻辑。 |
| 修改 Mapper | 否 | C7 不直接操作数据库。 |
| 修改 C2~C6 测试 | 否 | 保持已关闭任务卡稳定。 |
| 新增 Controller | 否 | API 层留到 C9。 |

## 八、结论

C7 LiveDataService 已按 Claude 裁定完成，实现了真实直播礼物事件、点赞/留言总增量事件，以及彩排用模拟注入入口。全量 `mvn test` 已通过，结果为 **Tests run: 24, Failures: 0, Errors: 0, Skipped: 0**。从功能和测试角度看，C7 已具备提交 Claude 复核的条件。

## References

[1]: Claude裁定_C7技术方案确认.md "Claude 裁定 — C7 技术方案确认"
[2]: backend/redface-backend/src/main/java/com/redface/service/PopularityService.java "PopularityService 人气值变更入口"
[3]: backend/redface-backend/src/main/java/com/redface/service/CollectStateService.java "CollectStateService 场控状态服务"
[4]: backend/redface-backend/src/main/java/com/redface/service/LiveDataService.java "C7 LiveDataService 实现"
[5]: backend/redface-backend/src/main/java/com/redface/dto/SimResult.java "C7 SimResult DTO"
[6]: backend/redface-backend/src/test/java/com/redface/LiveDataServiceC7Test.java "C7 LiveDataService 集成测试"
[7]: reports/C7_mvn_test_output_v1.txt "C7 Maven 全量测试输出 v1"
