# C19-R3b-1 逐项完成对照表

| 要求项 | 对应文件 + 行号/函数 | 证据/实现说明 |
|---|---|---|
| 1. `MODE_SPY` 允许 `targetId=null` | `CollectStateService.java` : 67 | 校验条件改为 `if (!MODE_POOL.equals(mode) && !MODE_SPY.equals(mode) && targetId == null)`，放开 spy+null 限制。 |
| 2. `LiveHomeService` 新增 spy+null 分支 | `LiveHomeService.java` : 49, 58-65 | `getHome()` 早退条件排除了 `spy`；在 `MODE_SPY` 分支内判断：若 `targetId == null` 则置 `targetDisplayName="卧底识破进行中"`, `targetPopularity=0`；否则调用 `fillPlayerTarget` 显示真实选手。`isSpyChannelOpen` 仍为 `true`。 |
| 3. 归属语义：spy+null 记入公共池，spy+targetId 记给选手 | `PopularityService.java` : 179-183 | 在 `resolveTarget` 中判断，若 `current.getMode().equals("spy") && current.getTargetId() == null`，则将 `resolvedType` 设为 `"pool"`，使后续增量记入 `pool_round_stats`；若有 targetId 则按 `"spy"` 处理，记入 `spy_popularity`。 |
| 4. 补充 4 条测试 | `C19R3b1Test.java` : 48-96 | `spyWithNullTargetShouldBeAcceptedAndReflectedInLiveHome` 测试设置成功及横幅显示；`spyWithNullTargetShouldRouteLikeToPool` 测试增量入池；`spyWithTargetIdShouldRouteLikeToPlayerSpyPopularity` 测试增量入选手。 |

**测试输出证据**：
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.358 s -- in com.redface.C19R3b1Test
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
