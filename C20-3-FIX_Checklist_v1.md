# C20-3-FIX 逐项完成对照表 v1

日期：2026-07-29　执行：Manus　验收：Claude
分支：`main`　基线：`910a7c1`（tabBar 图标修复）

---

## 〇、Claude 提问的正面答复：污染程度确认

FIX 卡要求先确认「当前 group_vote 写入时 `popularity_value` 实际写入什么值」。答复如下：

**写入的是票数原值（`popularity_value = rawValue`，即 1 票 = 1 人气），且污染是双重的。** 具体链路（返工前代码）：

1. `PopularityService.convertToPopularityValue` 中 `case "group_vote": return rawValue;`——30 票直接折算 30 人气，写入 `popularity_ledger`（`target_type='spy'`）。
2. 更严重的是 `applyChange` 第 4 步 `updateStats` 对 spy 目标会**同步累加 `player_round_stats.spy_popularity`**——即污染不止在流水表，统计表的卧底人气数字也被直接加了票数。

Claude 对隐患的判断正确，且实际影响比卡面描述（SUM 未排除 source）还多一层统计表污染。本次返工两处一并根除。

## 一、逐项完成对照

| # | 卡面要求 | 状态 | 实现说明 |
|---|---|---|---|
| 1 | 新建独立表 `group_vote_ledger`（含指定字段、幂等唯一约束、round+player 索引、MySQL 8 严格模式兼容） | 完成 | `db/db_schema.sql` 与测试用 `schema-h2.sql` 同步新增；`created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` 满足严格模式；`uq_gv_idem` 唯一约束 + `idx_gv_round_player` 索引 |
| 2 | 录入接口改写独立表，不再构造 PopularityChangeRequest、不触碰 popularity_ledger | 完成 | `AdminControlService.recordGroupVote` 全部改为写 `group_vote_ledger`（新建 `GroupVoteLedgerMapper`）；PopularityChangeRequest 相关代码全部移除 |
| 3 | summary 查询改从独立表按 round+player 汇总净值 | 完成 | `GroupVoteLedgerMapper.summarize` 联 `players` 表取选手名/编号，`SUM(votes)` 净值；**接口契约不变**（请求/响应字段与 C20-3 完全一致），因此 control-admin 前端零改动 |
| 4 | 幂等、冲销、operations_log 留痕全部保留 | 完成 | 幂等键仍用 `gv_` 前缀 + 表内唯一约束双保险；冲销仍为负数流水只增不改；操作日志格式不变 |
| 5 | 数据清理：确认既有污染数据并提供清理 SQL | 完成 | 见下文第三节 |
| 6 | 断言测试：录票前后该选手 spy 人气 SUM 不变 | 完成 | `votesNeverTouchPopularity` 测试，详见第二节 |

## 二、测试结果（含卡面必检的人气隔离断言）

`GroupVoteEntryTest` 由 5 项扩至 **7 项，全部通过**；全量回归 **110 项测试，0 失败 0 错误**（输出摘要存 `docs/C20-3-FIX_test_summary.md`）。

| 测试 | 验证内容 | 结果 |
|---|---|---|
| **votesNeverTouchPopularity（FIX 核心）** | 先造 100 人气基线，录 30 票 + 冲销 5 票后断言：popularity_ledger 行数不变、全表无 group_vote 来源、**人气 SUM 不变**、**spy_popularity 不变**、独立表净票数 = 25 | 通过 |
| **popularityEngineRejectsGroupVoteSource（加固）** | 直接向人气引擎构造 group_vote 来源请求，必须被拒绝（IllegalArgumentException） | 通过 |
| multipleEntriesAccumulate | 30 + 25 = 55 累加不覆盖 | 通过 |
| negativeEntryReverses | 40 − 10 = 30，独立表保留两笔流水（只增不改） | 通过 |
| duplicateIdempotencyKeyBlocked | 同幂等键连点只记一笔、不重复写日志 | 通过 |
| operationLogPersisted | 日志含操作人/轮次/选手/票数 | 通过 |
| invalidRequestsRejected | votes=0、缺幂等键、缺操作人均拒绝 | 通过 |

## 三、数据清理说明

生产库尚无真实数据（未部署），无需线上清理。各自本地如跑过 C20-3 版本的录入（含测试库、开发库），执行以下两条 SQL 即可根除污染——**顺序不可颠倒**（先按流水回减统计表，再删流水）：

```sql
-- 第一步：回减统计表中被污染的 spy_popularity
UPDATE player_round_stats s
JOIN (SELECT target_id, round_id, SUM(popularity_value) AS polluted
      FROM popularity_ledger WHERE source = 'group_vote'
      GROUP BY target_id, round_id) x
  ON s.player_id = x.target_id AND s.round_id = x.round_id
SET s.spy_popularity = s.spy_popularity - x.polluted;

-- 第二步：删除人气账本中的投票流水
DELETE FROM popularity_ledger WHERE source = 'group_vote';
```

H2 测试库每次由 schema 脚本重建，无残留问题。

## 四、超出卡面的三处改动（按纪律明示）

1. **人气引擎封死 group_vote 后门**：不止把录入改道，还从 `PopularityService` 整体移除了 group_vote 来源枚举与负数白名单——今后任何代码再构造 group_vote 人气请求会被直接拒绝（有测试兜底）。仅做改道而留着枚举，等于留了一扇随时能再污染的后门。
2. **删除 `PopularityLedgerMapper.summarizeBySource`**：C20-3 新增的方法，改道后已无任何调用方，按"不留死代码"清理。
3. **`SchemaInitializationTest` 白名单更新**：新表加入表清单、预期表数 21 → 22，属新表的必然连带。

## 五、给 Claude 的一条工程备注

改写录入时规避了一个事务陷阱：原打算沿用"直接 INSERT、靠唯一约束抓重复"的写法，但在 `@Transactional` 方法内捕获 `DuplicateKeyException` 后 Spring 已将事务标记为 rollback-only，后续查询累计值会抛 `UnexpectedRollbackException`。故改为**先查幂等键、再插入**，唯一约束仅作并发兜底（两名场控同毫秒撞键时由约束拦截，该场景 8/1 单人录入不会出现）。此细节写明供 review 时对照。

## 六、与 C20-4 的衔接确认

同意卡面顺序：本 FIX 先行，C20-4 的水位线（填总数算差值）将基于 `group_vote_ledger.SUM(votes)` 实现，存储已就位，C20-4 可随时下发。

---

**Commit**：见推送后的 main 分支（本文件与代码同 commit）。
**测试证据**：`docs/C20-3-FIX_test_summary.md`（110 项全过摘要）。
