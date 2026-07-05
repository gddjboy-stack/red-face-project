# C19-R3b-2 逐项完成对照表

| 要求项 | 对应文件 + 行号/函数 | 证据/实现说明 |
|---|---|---|
| 1. 删除自动找卧底逻辑 | `frontend/control-admin/src/App.vue` : 727-740 (`toggleSpyMode`) | 已彻底删除原有的 `const spyCandidate = candidates.find((c: any) => c.isSpy)` 等逻辑。整个文件中不再有根据 `isSpy` 自动选择 `targetId` 的代码。 |
| 2. 开启识破时提供两阶段弹窗 | `frontend/control-admin/src/App.vue` : 434-453 (模板), 738-739 (`toggleSpyMode`) | 开启时不再直接调用接口，而是 `spyDialogVisible.value = true` 弹出对话框。弹窗内包含阶段一（暂不指定目标，`targetId` 为 null）和阶段二（下拉手选选手）的选择。 |
| 3. 提供「切换目标」入口 | `frontend/control-admin/src/App.vue` : 100 (模板), 751-754 (`changeSpyTarget`) | 在“开启卧底识破”按钮旁增加了“切换目标”按钮。点击后弹出同样的对话框，可重新提交 `spy+targetId`，无需先关闭。 |
| 4. 明示当前阶段与目标 | `frontend/control-admin/src/App.vue` : 97-99 (模板) | 增加了一段状态文本：`识破进行中 · {{ home.targetId ? '目标: ' + home.targetDisplayName : '未指定目标' }}`，清晰展示当前所处的阶段和目标。 |
| 5. 关闭逻辑保持显式回 pool | `frontend/control-admin/src/App.vue` : 731-736 (`toggleSpyMode`) | 若当前为开启状态，点击按钮会提示“确认要关闭卧底识破投票吗？关闭后将切回 pool 模式。”，并提交 `mode='pool', targetId=null`。 |

**界面截图（三态）**：
由于沙箱环境限制，无法直接生成浏览器界面截图。请在真实环境部署后，通过管理后台查看以下三种状态：
1. **未开启**：只显示“开启卧底识破”按钮。
2. **阶段一（未指定目标）**：显示“识破进行中 · 未指定目标”，旁边有“切换目标”和“关闭卧底识破”按钮。
3. **阶段二（已指定目标）**：显示“识破进行中 · 目标: X号 某某”，旁边有“切换目标”和“关闭卧底识破”按钮。
