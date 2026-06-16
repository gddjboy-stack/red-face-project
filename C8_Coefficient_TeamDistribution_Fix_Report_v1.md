# C8 CoefficientService + TeamDistributionService 实施报告 v1

**作者：Manus AI**  
**日期：2026-06-16**  
**适用范围：直播选秀项目后端 C8 任务卡，加成系数与团队池分配**

## 一、执行背景

本次 C8 实施依据 Claude 对 `C8_Coefficient_TeamDistribution_Implementation_Plan_v1` 的裁定执行。Claude 已明确批准编码，并裁定 C8 允许新增 `CoefficientService`、`TeamDistributionService`、2 个 DTO、必要 Entity/Mapper，以及对 `StatsMapper` 做最小扩展；同时禁止修改 `PopularityService` 核心流程，禁止直接写 `popularity_ledger`/stats 表绕过既有入口，禁止新建 `team_distribution_details` 表，禁止修改 C2~C7 已通过测试，禁止新增 Controller。[1]

本次实施遵守 C8 的核心原则：**系数调整只通过 coefficient_ledger + player_round_stats.coefficient 完成；团队分配批次写入 team_distribution_batches，分配明细复用 popularity_ledger.distribution_batch_id，并且每位成员入账均走 PopularityService.applyChange(...) 单一入口**。[2]

## 二、Claude 裁定执行情况

Claude 对 C8 技术方案中的 5 个问题逐项裁定，本次代码已按裁定落实。特别是团队分配事务顺序采用“建批次 → 原子扣池 → 逐人 applyChange”，且扣池 SQL 带 `team_popularity >= totalValue` 条件并检查影响行数为 1；若团队池余额不足或被并发扣减，则抛异常触发事务回滚。[1]

| 裁定项 | Claude 裁定 | 实施结果 |
|---|---|---|
| Q1 | `adjustCoefficient` 返回 `CoefficientResult` | 已实现，便于测试断言和 C9 API 层复用。 |
| Q2 | `pk_win` 且 `completed=false` 抛异常 | 已实现，拒绝自行发明 PK 失败扣分规则。 |
| Q3 | custom 余数按 `player_id` 升序补齐 | 已实现，custom 与 equal 使用一致的余数补齐顺序。 |
| Q4 | 团队分配事务顺序：建批次 → 原子扣池 → 逐人 `applyChange` | 已实现，并在扣池 UPDATE 中加入余额条件。 |
| Q5 | `distribute` 固定分配当前团队池全部余额；余额≤0、无成员均抛异常 | 已实现，并增加测试覆盖。 |

## 三、实际代码变更

本次新增 8 个文件，并对 `StatsMapper` 做最小扩展。没有修改 `PopularityService`、`CollectStateService`、C2~C7 已通过测试，也没有新增 Controller。

| 文件 | 类型 | 说明 |
|---|---|---|
| `backend/redface-backend/src/main/java/com/redface/service/CoefficientService.java` | 新增 Service | 实现系数调整、幂等流水和 coefficient 累加。 |
| `backend/redface-backend/src/main/java/com/redface/service/TeamDistributionService.java` | 新增 Service | 实现团队池全额分配、批次记录、原子扣池和逐人入账。 |
| `backend/redface-backend/src/main/java/com/redface/dto/CoefficientResult.java` | 新增 DTO | 返回系数调整结果、delta、最新 coefficient 和幂等键。 |
| `backend/redface-backend/src/main/java/com/redface/dto/DistributionResult.java` | 新增 DTO | 返回团队分配批次、总额、方法和成员份额。 |
| `backend/redface-backend/src/main/java/com/redface/entity/CoefficientLedgerEntity.java` | 新增 Entity | 映射 `coefficient_ledger`。 |
| `backend/redface-backend/src/main/java/com/redface/entity/TeamDistributionBatchEntity.java` | 新增 Entity | 映射 `team_distribution_batches`。 |
| `backend/redface-backend/src/main/java/com/redface/mapper/CoefficientLedgerMapper.java` | 新增 Mapper | 插入系数流水并按幂等键计数。 |
| `backend/redface-backend/src/main/java/com/redface/mapper/TeamDistributionBatchMapper.java` | 新增 Mapper | 插入团队分配批次并回填自增 batchId。 |
| `backend/redface-backend/src/main/java/com/redface/mapper/PlayerRoundMapper.java` | 新增 Mapper | 按 teamId + roundId 查询成员，按 player_id 升序返回。 |
| `backend/redface-backend/src/main/java/com/redface/mapper/StatsMapper.java` | 最小扩展 | 新增 `incrementPlayerCoefficient`、`distributeTeamPopularity`、`findTeamDistributedPopularity`。 |
| `backend/redface-backend/src/test/java/com/redface/CoefficientAndDistributionC8Test.java` | 新增 JUnit/H2 测试 | 覆盖 C8 验收和边界场景。 |

## 四、核心实现说明

`CoefficientService.adjustCoefficient(...)` 使用 `coef_ + taskId + _ + playerId` 作为幂等键。服务先确保 `player_round_stats` 行存在，再插入 `coefficient_ledger`。若插入命中唯一键冲突，则返回 duplicated，且不再次更新 coefficient；若插入成功，则调用 `StatsMapper.incrementPlayerCoefficient(...)`，用 `UPDATE ... SET coefficient = coefficient + ?` 的累加写法保证并发安全。[3]

`TeamDistributionService.distribute(...)` 固定分配当前团队池全部余额。它先查询团队成员和团队池余额，余额小于等于 0 时抛出“团队池无可分配人气”，成员数为 0 时抛出“团队无成员”。equal 模式按成员 `player_id` 升序平分，余数逐个 +1；custom 模式按权重整数比例计算基础份额，并同样按 `player_id` 升序补齐余数。[4]

团队分配事务顺序为：读取团队池与成员 → 计算份额 → 插入 `team_distribution_batches` 并获取 batchId → 调用 `StatsMapper.distributeTeamPopularity(...)` 原子扣减团队池并增加已分配值 → 对每个正份额成员调用 `PopularityService.applyChange(...)` 写入带 `distribution_batch_id` 的流水。任何一步失败都会在事务中回滚。[5]

## 五、测试覆盖情况

本次新增 `CoefficientAndDistributionC8Test`，采用与 C2~C7 一致的 Spring Boot + H2 集成测试风格，真实验证 Service、Mapper、唯一索引、事务和 stats/ledger 结果。测试数量为 8 个，其中包含 Claude 要求的 4 个核心验证用例、Q5 两个边界用例，并补充了 `pk_win completed=false` 与 custom 余数规则验证。

| 测试用例 | 验证点 | 结果 |
|---|---|---:|
| `sameTaskIdRepeatedAdjustCoefficientShouldOnlyApplyOnce` | 同一 taskId 重复调整系数只生效一次 | 通过 |
| `completedAndFailedTaskShouldAdjustCoefficientByPlusAndMinusTen` | 任务完成 +10，失败 -10 | 通过 |
| `pkWinWithCompletedFalseShouldThrowException` | `pk_win completed=false` 抛异常且不写流水 | 通过 |
| `equalDistribution1000ToSevenMembersShouldNotLoseRemainder` | 1000 平分 7 人，前 6 人 143，第 7 人 142，总额不丢分 | 通过 |
| `distributionShouldWriteLedgerWithBatchIdAndUpdateTeamStats` | 每人 ledger 带 batch_id，团队池扣减，已分配增加 | 通过 |
| `noTeamPopularityShouldThrowAndNotCreateBatch` | 团队池余额为 0 时抛异常，不创建批次 | 通过 |
| `noTeamMembersShouldThrowAndNotCreateBatch` | 团队无成员时抛异常，不创建批次 | 通过 |
| `customDistributionShouldFillRemainderByPlayerIdAscending` | custom 模式按 player_id 升序补余数 | 通过 |

## 六、全量测试结果

已在 `backend/redface-backend` 目录执行全量测试命令，并将完整输出保存到 `reports/C8_mvn_test_output_v1.txt`。

```bash
mvn test
```

最终测试结果如下。C8 新增 8 个测试通过，项目测试总数从 C7 的 24 个增加到 32 个，全部绿灯。

```text
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.376 s
[INFO] Finished at: 2026-06-16T03:39:13Z
[INFO] ------------------------------------------------------------------------
```

| 测试范围 | 结果 |
|---|---:|
| `TokenGeneratorServiceTest` | 通过 |
| `PopularityServiceC3Test` | 通过 |
| `SchemaInitializationTest` | 通过 |
| `LiveDataServiceC7Test` | 通过 |
| `PopularityServiceC4Test` | 通过 |
| `CoefficientAndDistributionC8Test` | 通过，8 个新增测试全部成功 |
| `PopularityServiceC2Test` | 通过 |
| `TokenServiceC5Test` | 通过 |
| 全量测试汇总 | `Tests run: 32, Failures: 0, Errors: 0, Skipped: 0` |

## 七、边界确认

本次实施没有修改 `PopularityService` 核心流程，没有直接绕过 `PopularityService` 写 `popularity_ledger` 明细，没有新建 `team_distribution_details` 表，没有修改 C2~C7 已通过测试，也没有新增 Controller。团队分配明细全部复用 `popularity_ledger.distribution_batch_id`。

| 禁止项 | 是否触碰 | 说明 |
|---|---:|---|
| 修改 `PopularityService` 核心流程 | 否 | C8 分配入账仍走 `applyChange`。 |
| 直接写 `popularity_ledger` 明细 | 否 | 每人份额通过 `PopularityService.applyChange(...)` 写入。 |
| 新建 `team_distribution_details` 表 | 否 | 明细复用 ledger 的 batch_id。 |
| 修改 C2~C7 已通过测试 | 否 | 只新增 C8 测试。 |
| 新增 Controller | 否 | API 层留到 C9。 |

## 八、结论

C8 已按 Claude 裁定完成实现：系数调整具备幂等保护和累加式并发安全更新；团队分配具备批次记录、整数余数不丢分、原子扣池、逐人入账和边界异常保护。全量 `mvn test` 已通过，结果为 **Tests run: 32, Failures: 0, Errors: 0, Skipped: 0**。从功能和测试角度看，C8 已具备提交 Claude 复核的条件。

## References

[1]: /home/ubuntu/upload/Claude裁定_C8技术方案确认.md "Claude 裁定 — C8 技术方案确认"
[2]: C8_Coefficient_TeamDistribution_Implementation_Plan_v1.md "C8 技术实施方案 v1"
[3]: backend/redface-backend/src/main/java/com/redface/service/CoefficientService.java "C8 CoefficientService 实现"
[4]: backend/redface-backend/src/main/java/com/redface/service/TeamDistributionService.java "C8 TeamDistributionService 实现"
[5]: backend/redface-backend/src/main/java/com/redface/mapper/StatsMapper.java "StatsMapper C8 最小扩展"
[6]: backend/redface-backend/src/test/java/com/redface/CoefficientAndDistributionC8Test.java "C8 JUnit/H2 集成测试"
[7]: reports/C8_mvn_test_output_v1.txt "C8 Maven 全量测试输出 v1"
