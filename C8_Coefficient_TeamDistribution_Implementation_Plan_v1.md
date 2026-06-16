# C8 CoefficientService + TeamDistributionService 技术实施方案 v1（提交 Claude 确认）

**作者：Manus AI**  
**日期：2026-06-16**  
**状态：待 Claude 确认后再编码**  
**适用范围：直播选秀项目后端 C8 任务卡，加成系数与团队池分配**

## 一、背景与执行纪律

Claude 已审查 commit `7027966` 并确认 **C7 通过，C8 开卡**。根据当前项目协作规则，涉及技术方案、Mapper 扩展、事务边界、分配算法和测试设计时，Manus 先形成 Markdown 文件并上传 GitHub，由 John 转发 Claude 确认，确认后再实施代码变更。本文件即为 C8 编码前的技术方案确认稿。

C8 包含两个服务：`CoefficientService` 负责加成系数调整，`TeamDistributionService` 负责将团队池人气分配给团队成员。开发指导手册 5.4 已给出这两个服务的骨架签名和核心行为：系数调整需要写 `coefficient_ledger` 并更新 `player_round_stats.coefficient`；团队分配需要建立批次、计算份额、对每位成员调用 `PopularityService.applyChange(source="team_distribution")`，并扣减 `team_round_stats.team_popularity`、增加 `distributed_popularity`。[1]

> 本方案不会直接修改代码。待 Claude 确认后，再按确认后的范围新增 Service、DTO、Mapper 与 JUnit/H2 测试。

## 二、C8 需求复述

C8 的目标是补齐两个后台核心运营能力。第一，任务完成、任务失败和 PK 胜利会改变选手在某轮的加成系数；第二，团队池中累积的人气需要按规则分配给队内成员，并留下可追溯的批次与流水。

| 模块 | 方法 | 核心行为 | 幂等与审计 |
|---|---|---|---|
| `CoefficientService` | `adjustCoefficient(int playerId, int roundId, String taskId, String taskType, boolean completed, String operatorId)` | 计算 delta，写 `coefficient_ledger`，更新 `player_round_stats.coefficient` | 幂等键为 `coef_` + `taskId` + `_` + `playerId`，防同一任务重复加减。[2] |
| `TeamDistributionService` | `distribute(int teamId, int roundId, String method, Map<Integer,Integer> customWeights, String operatorId, String reason)` | 建批次，计算每位成员份额，逐人调用 `applyChange(source="team_distribution")`，扣减团队池并累加已分配值 | 批次写入 `team_distribution_batches`；明细复用 `popularity_ledger.distribution_batch_id`，不新建明细表。[1] |

## 三、现有代码依据与 Mapper 缺口

`AppConstants` 已定义 C8 所需的系数常量：基础系数为 `100`，任务完成/失败的变化值为 `10`，PK 胜利变化值为 `5`。这意味着系数采用 “×100 整数存储” 方式，`100` 表示 1.0，`110` 表示 1.1。[3]

`StatsMapper` 目前已经具备确保选手轮次统计行存在、查询选手系数、确保团队统计行存在、查询团队池人气等能力；但它没有更新 coefficient 的方法，也没有团队分配时原子扣减 `team_popularity` 并累加 `distributed_popularity` 的方法。[4] 因此 C8 需要最小扩展 Mapper，而不应在 Service 中直接写 `JdbcTemplate`。

`popularity_ledger` 已有 `distribution_batch_id` 字段，`PopularityChangeRequest` 也已有 `distributionBatchId` 字段，`PopularityService.buildLedger(...)` 会把该字段写入流水。因此团队分配的“每人明细”可以按手册要求复用流水表，无需新建 `team_distribution_details` 表。[5]

| 能力 | 当前状态 | C8 建议 |
|---|---|---|
| 查询选手当前系数 | `StatsMapper.findPlayerCoefficient` 已有 | 复用。 |
| 初始化选手轮次统计行 | `StatsMapper.ensurePlayerRoundStats` 已有 | 复用。 |
| 更新选手 coefficient | 缺失 | 在 `StatsMapper` 新增 `incrementPlayerCoefficient(playerId, roundId, delta)`。 |
| 查询团队池人气 | `StatsMapper.findTeamPopularity` 已有 | 复用。 |
| 扣减团队池并累加已分配 | 缺失 | 在 `StatsMapper` 新增 `distributeTeamPopularity(teamId, roundId, totalValue)`，要求 `team_popularity >= totalValue`。 |
| 查询团队成员 | 缺失 | 新增 `PlayerRoundMapper.findPlayerIdsByTeam(teamId, roundId)`。 |
| 写 coefficient ledger | 缺失 | 新增 `CoefficientLedgerMapper.insert(...)` 与查询方法。 |
| 写 team distribution batch | 缺失 | 新增 `TeamDistributionBatchMapper.insert(...)`，使用生成主键返回 batchId。 |

## 四、拟新增代码结构

C8 的代码范围应保持清晰：新增两个 Service、两个结果 DTO、必要实体和 Mapper，以及一个或两个 JUnit/H2 集成测试类。C8 不应修改 `PopularityService` 的核心流程，也不应直接写 `popularity_ledger` 或 stats 表。

| 拟新增或扩展文件 | 类型 | 说明 |
|---|---|---|
| `CoefficientService.java` | 新增 Service | 实现系数调整、幂等写 ledger、更新 coefficient。 |
| `TeamDistributionService.java` | 新增 Service | 实现团队池分配、批次创建、份额计算、逐人入账和团队池扣减。 |
| `CoefficientResult.java` | 新增 DTO | 返回 success、duplicated、playerId、roundId、delta、coefficient、idempotencyKey。 |
| `DistributionResult.java` | 新增 DTO | 返回 batchId、teamId、roundId、totalValue、distributedValue、method、memberShares。 |
| `CoefficientLedgerEntity.java` | 新增 Entity | 映射 `coefficient_ledger`。 |
| `TeamDistributionBatchEntity.java` | 新增 Entity | 映射 `team_distribution_batches`。 |
| `CoefficientLedgerMapper.java` | 新增 Mapper | 插入系数流水、按幂等键计数。 |
| `TeamDistributionBatchMapper.java` | 新增 Mapper | 插入团队分配批次并回填生成主键。 |
| `PlayerRoundMapper.java` | 新增 Mapper | 查询某团队某轮成员 ID，按 player_id 升序用于 equal 余数分配。 |
| `StatsMapper.java` | 扩展 Mapper | 新增 coefficient 累加和团队池扣减/已分配累加方法。 |
| `CoefficientAndDistributionC8Test.java` | 新增 JUnit/H2 测试 | 覆盖 Claude 指定的 4 个验证场景。 |

## 五、CoefficientService 设计建议

`CoefficientService.adjustCoefficient(...)` 建议返回 `CoefficientResult`，而不是 `void`。理由与 C7 类似：返回对象便于测试断言、C9 API 层复用和后台操作反馈。该返回类型是对手册骨架的合理落地，不改变业务语义。

### 5.1 Delta 规则

`taskType` 建议支持两类：普通任务类和 `pk_win`。当 `taskType="pk_win"` 时，delta 使用 `AppConstants.COEFFICIENT_PK_WIN`，即 `+5`。普通任务类使用 `AppConstants.COEFFICIENT_TASK_DELTA`，即 `+10` 或 `-10`，其中 `completed=true` 为正，`completed=false` 为负。[3]

| taskType | completed | delta | 说明 |
|---|---:|---:|---|
| `task` 或其他普通任务类型 | true | `+10` | 完成任务，系数 100 → 110。 |
| `task` 或其他普通任务类型 | false | `-10` | 任务失败，系数 100 → 90。 |
| `pk_win` | true | `+5` | PK 获胜，系数 100 → 105。 |
| `pk_win` | false | 建议拒绝 | Claude 开卡只说明 `pk_win` 用 `+5`，未说明 PK 失败扣分。建议 `pk_win` 且 `completed=false` 抛出异常，避免自行发明规则。 |

### 5.2 幂等与事务顺序

建议 `adjustCoefficient` 加 `@Transactional`。流程为：校验参数；计算 idempotencyKey；确保 `player_round_stats` 行存在；尝试插入 `coefficient_ledger`；若唯一键冲突则返回 duplicated，不更新 coefficient；插入成功后再执行 coefficient 累加；最后读取最新 coefficient 并返回。

这里的关键是**先写幂等流水，再更新系数**。这样在同一事务内，如果重复调用命中 `coefficient_ledger.idempotency_key` 唯一约束，就不会执行第二次系数更新。H2 schema 已为 `coefficient_ledger.idempotency_key` 建唯一键。[6]

## 六、TeamDistributionService 设计建议

`TeamDistributionService.distribute(...)` 建议返回 `DistributionResult`。它需要完整记录分配批次、每个成员份额、分配总额，以及最终是否成功。团队分配必须采用事务，确保批次、每人流水和团队池扣减一致提交。

### 6.1 成员来源

成员应来自 `player_round` 表中 `team_id = ? AND round_id = ?` 的记录，按 `player_id ASC` 排序。这样 equal 分配的“余数按选手顺序逐个 +1”具有稳定顺序。`player_round` 表是项目中记录选手每轮团队归属的表。[7]

### 6.2 团队池总额来源

建议分配的 `totalValue` 取 `team_round_stats.team_popularity` 当前值。若团队池为空或小于等于 0，则抛出明确异常。完成分配后，通过一个原子 SQL 扣减 `team_popularity` 并累加 `distributed_popularity`，建议条件包含 `team_popularity >= totalValue`，避免并发下超分配。

拟扩展 `StatsMapper`：

```java
@Update("""
    UPDATE team_round_stats
    SET team_popularity = team_popularity - #{totalValue},
        distributed_popularity = distributed_popularity + #{totalValue}
    WHERE team_id = #{teamId}
      AND round_id = #{roundId}
      AND team_popularity >= #{totalValue}
""")
int distributeTeamPopularity(int teamId, int roundId, long totalValue);
```

### 6.3 equal 分配算法

`equal` 模式必须保证整数运算不丢分。设团队池总额为 `totalValue`，成员数为 `n`，则基础份额为 `base = totalValue / n`，余数为 `remainder = totalValue % n`。按 `player_id ASC` 排序后，前 `remainder` 个成员各多分 1。这样所有份额之和严格等于 `totalValue`。

| 示例 | 成员数 | totalValue | base | remainder | 份额 |
|---|---:|---:|---:|---:|---|
| 1000 平分 7 人 | 7 | 1000 | 142 | 6 | 前 6 人各 143，第 7 人 142，总和 1000。 |

### 6.4 custom 分配算法

`custom` 模式建议将 `customWeights` 视为正整数权重，key 为 playerId，value 为权重。必须校验所有团队成员都有正权重，且不得包含非团队成员。计算方式为：先按 `share = totalValue * weight / weightSum` 得到基础份额，计算余数 `totalValue - sum(baseShares)`，再按稳定顺序补余数。

这里有一个需要 Claude 裁定的小问题：custom 补余数的“稳定顺序”应按 player_id 升序，还是按小数余量从大到小（最大余数法）？我建议**按 player_id 升序补余数**，与 equal 的余数处理规则一致，简单可解释；如果 Claude 希望更公平，可改为最大余数法。

### 6.5 每人入账路径

每个成员份额应调用 `PopularityService.applyChange(...)`，设置：

| 字段 | 值 |
|---|---|
| `targetType` | `player` |
| `targetId` | 成员 playerId |
| `source` | `team_distribution` |
| `rawValue` | 该成员分得的人气值 |
| `roundId` | 参数 roundId |
| `distributionBatchId` | 新建批次 batchId |
| `idempotencyKey` | 建议 `teamdist_` + batchId + `_` + playerId |
| `operatorId` | 参数 operatorId |
| `reason` | 参数 reason |

`PopularityService.convert(...)` 已允许 `team_distribution` 作为直接人气值来源，因此这里不需要新增换算逻辑。[8]

## 七、事务与顺序的蓝军审视

团队分配的事务顺序有两种选择。第一种是先扣团队池，再写每人流水；第二种是先写批次和每人流水，再扣团队池。若先写每人流水后扣团队池失败，需要事务回滚；这在 Spring 事务下可行，但失败发生较晚。若先扣团队池，再逐人入账失败，同样依赖事务回滚。

我建议顺序为：读取团队池与成员 → 计算份额 → 建批次 → 原子扣团队池并累加已分配 → 逐人调用 `applyChange`。理由是先完成团队池原子扣减可以防并发超分配；如果后续任一成员入账失败，整个事务回滚，批次和扣减都会撤销。

| 步骤 | 操作 | 目的 |
|---|---|---|
| 1 | 查询团队池总额和成员列表 | 确定待分配金额与对象。 |
| 2 | 计算份额 | 确保份额总和等于团队池总额。 |
| 3 | 插入 `team_distribution_batches` | 生成 batchId，作为所有流水的关联 ID。 |
| 4 | 原子扣减团队池并增加已分配 | 防止并发超分配。 |
| 5 | 每人调用 `PopularityService.applyChange` | 通过唯一入口写 ledger 和 player stats。 |

## 八、测试设计建议

C8 建议继续使用 Spring Boot + H2 集成测试风格，真实验证 Service、Mapper、事务、唯一索引和统计表。测试不应使用 Mockito，因为 C8 的核心风险在数据库幂等、整数分配和事务一致性。

| 测试用例 | 前置条件 | 操作 | 关键断言 |
|---|---|---|---|
| 同一 taskId 重复调整系数只生效一次 | 插入 player、round、player_round_stats | 连续两次 `adjustCoefficient(... taskId same ...)` | `coefficient_ledger` 只有 1 条；系数只从 100 到 110。 |
| 任务完成 +0.1，失败 -0.1 | 插入两个 player 或分两次不同 taskId | completed=true 和 completed=false | 系数分别变为 110 和 90，delta 分别为 +10/-10。 |
| 团队池 1000 平分 7 人不丢分 | 插入 7 名队员，team_popularity=1000 | `distribute(equal, null, ...)` | 7 人份额总和=1000；前 6 人 143，第 7 人 142。 |
| 团队分配后流水与团队统计正确 | 插入团队池和成员 | 执行 distribute | 每人 ledger 带 batch_id；`team_popularity` 扣到 0；`distributed_popularity` 增加 1000。 |

## 九、需要 Claude 确认的问题

| 编号 | 问题 | Manus 建议 |
|---|---|---|
| Q1 | `adjustCoefficient` 是否可返回 `CoefficientResult`，而不是手册骨架中的 `void`？ | 建议返回，便于测试断言和 C9 API 复用。 |
| Q2 | `pk_win` 且 `completed=false` 时应如何处理？ | 建议抛异常，因为开卡只定义了 PK 胜利 +5，未定义失败扣分。 |
| Q3 | custom 分配余数按什么规则补齐？ | 建议按 player_id 升序补余数，与 equal 一致；如需更公平可改最大余数法。 |
| Q4 | 团队分配事务顺序是否采用“建批次 → 原子扣团队池 → 逐人 applyChange”？ | 建议采用，防并发超分配，并依赖事务回滚保证一致性。 |
| Q5 | 是否将 `TeamDistributionService.distribute` 的 totalValue 固定为当前团队池全部余额？ | 建议是。手册签名没有 totalValue 参数，因此应分配当前团队池全部余额。 |

## 十、拟提交验证物

Claude 确认后，我将按确认后的方案编码，并提交以下验证物。

| 验证物 | 说明 |
|---|---|
| `CoefficientAndDistributionC8Test.java` | JUnit/H2 集成测试，覆盖 Claude 指定 4 个场景。 |
| `reports/C8_mvn_test_output_v1.txt` | 完整 `mvn test` 输出。 |
| `C8_Coefficient_TeamDistribution_Fix_Report_v1.md` | C8 实施说明、测试结果和变更清单。 |

## 十一、结论

C8 的关键风险点不在普通业务分支，而在**幂等、整数余数、事务一致性和 Mapper 最小扩展**。我建议 C8 严格保持两个边界：第一，系数调整只改 `coefficient_ledger` 和 `player_round_stats.coefficient`；第二，团队分配明细只通过 `PopularityService.applyChange` 写入 `popularity_ledger`，不新建明细表、不绕过人气引擎。待 Claude 确认 Q1~Q5 后，再开始编码。

## References

[1]: docs/红颜局中局开发指导手册V2.0完整版.md "开发指导手册 V2.0，第 5.4 Service 签名"
[2]: /home/ubuntu/upload/Claude审查_C7通过_C8开卡.md "Claude 审查 — C7 通过，C8 开卡"
[3]: backend/redface-backend/src/main/java/com/redface/config/AppConstants.java "AppConstants 系数常量"
[4]: backend/redface-backend/src/main/java/com/redface/mapper/StatsMapper.java "StatsMapper 现有统计能力"
[5]: backend/redface-backend/src/main/java/com/redface/dto/PopularityChangeRequest.java "PopularityChangeRequest distributionBatchId 字段"
[6]: backend/redface-backend/src/test/resources/schema-h2.sql "H2 schema coefficient_ledger 唯一键"
[7]: backend/redface-backend/src/test/resources/schema-h2.sql "H2 schema player_round 团队归属表"
[8]: backend/redface-backend/src/main/java/com/redface/service/PopularityService.java "PopularityService 支持 team_distribution 来源"
