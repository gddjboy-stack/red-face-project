# C19-R3b-3 逐项完成对照表

| 要求项 | 对应文件 + 行号/函数 | 证据/实现说明 |
|---|---|---|
| 1. 当 `currentMode==='spy'` 且无目标时，`targetPopularityText` 置 `'--'` | `frontend/douyin-miniprogram/pages/home/index.js` : 34 | `targetPopularityText: (home.currentMode === 'spy' && home.targetId == null) ? '--' : formatNumber(home.targetPopularity)` |

**代码 diff**：
```diff
--- a/frontend/douyin-miniprogram/pages/home/index.js
+++ b/frontend/douyin-miniprogram/pages/home/index.js
@@ -31,7 +31,7 @@ Page({
       this.setData({
         home,
         updatedAtText: formatTime(home.updatedAt),
-        targetPopularityText: formatNumber(home.targetPopularity),
+        targetPopularityText: (home.currentMode === 'spy' && home.targetId == null) ? '--' : formatNumber(home.targetPopularity),
         teamPopularityText: formatNumber(home.teamPopularity)
       })
```

**说明**：由于沙箱环境无法运行抖音开发者工具，无法提供真实界面截图，但代码逻辑已严格按照指令修改，当 `currentMode` 为 `spy` 且 `targetId` 为 `null`（即阶段一）时，观众端首页的人气数字将显示为 `--`，避免了“卧底识破进行中”横幅旁出现无意义的 `0`。
