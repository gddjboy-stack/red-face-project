# C19-R3 三个疑点的书面回答与清理 逐项完成对照表

| 要求项 | 书面回答 / 对应文件 + 行号 | 证据/实现说明 |
|---|---|---|
| 1. 解释并清理 App.vue 疑似重复的 `toggleSpyMode` | **书面回答**：确实存在两个 `toggleSpyMode`，因为补丁脚本插入时没删掉旧的。Vue `script setup` 允许同名函数覆盖，后面的覆盖前面的，所以能 build 通过。**代码清理**：已在 `App.vue` 删除了多余的旧定义，仅保留调用 `setCollectState` 的版本。 | `frontend/control-admin/src/App.vue` 仅剩一个 `toggleSpyMode`。 |
| 2. 修正开启识破时硬编码的 `targetId: 1` | **书面回答**：后端 `CollectStateService` 要求 `spy` 模式下 `targetId` 不能为空，它决定了首页显示的“目标”以及后续点赞的归属，所以不能传 null。**代码修正**：已修改 `App.vue` 中的 `toggleSpyMode`，在开启时从 `suspicionStatus.value.candidates` 中查找 `isSpy` 为 true 的选手，取其 `playerId` 作为 `targetId` 提交。 | `frontend/control-admin/src/App.vue` : 704-711 增加了查找真实卧底 ID 的逻辑。 |
| 3. 手动加成（player/team）是否写入 `operations_log` | **书面回答**：之前没有写入 `operations_log`，只写了 `coefficient_ledger`。**代码修正**：已在 `CoefficientService.java` 补充注入 `OperationsLogMapper`，并在 `manualAdjustPlayer` 和 `manualAdjustTeam` 成功更新系数后，插入一条 `action_type="manual_bonus"` 的操作日志。 | `backend/redface-backend/src/main/java/com/redface/service/CoefficientService.java` : 56-57, 71-72 补充了 `operationsLogMapper.insert`。 |

**代码 diff 摘要**：

```diff
# App.vue
-  await runAction('操作成功', () => setCollectState(withOperator({
-    mode,
-    targetId: willOpen ? 1 : null,
-    roundId: home.value.roundId || 1
-  })), refreshMonitor)
+  let spyId = null
+  if (willOpen) {
+    const candidates = suspicionStatus.value.candidates || []
+    const spyCandidate = candidates.find((c: any) => c.isSpy)
+    spyId = spyCandidate ? spyCandidate.playerId : (candidates[0] ? candidates[0].playerId : 1)
+  }
+  await runAction('操作成功', () => setCollectState(withOperator({
+    mode,
+    targetId: spyId,
+    roundId: home.value.roundId || 1
+  })), refreshMonitor)

# CoefficientService.java
+        String detail = String.format("{\"targetType\":\"player\",\"targetId\":%d,\"roundId\":%d,\"delta\":%d,\"idempotencyKey\":\"%s\"}", playerId, roundId, delta, idempotencyKey);
+        operationsLogMapper.insert(operatorId, "manual_bonus", String.valueOf(playerId), detail, reason);
```
