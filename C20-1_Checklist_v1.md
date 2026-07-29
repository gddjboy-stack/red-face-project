# C20-1 逐项完成对照表 v1

日期：2026-07-29　执行：Manus　分支：`release/miniapp-lite`　卡片：C20-1 小程序精简版 — 页面移除与入口清理

## 〇、执行前差异声明（按执行纪律第 3 条：与代码现实不符处停下写明）

| # | 卡集指令 | 代码现实 | 处理 |
|---|---|---|---|
| D1 | 路径写作 `frontend/douyin-miniapp/` | 实际目录为 `frontend/douyin-miniprogram/` | 按现实路径执行，功能范围不变 |
| D2 | `onSpyTap()` 约 43~49 行 | 实际为 `pages/home/index.js` 43~50 行（含收尾 `},`） | 按实际行删除 |
| D3 | `spy-entry` 区块约 29 行 | 实际为 `pages/home/index.ttml` 29~32 行（4 行区块） | 整块删除 |
| D4 | 页面目录移至 `archive/` | 若移到小程序目录内的 `archive/`，抖音打包仍会包含该目录（`project.config.json` 无 `packOptions.ignore` 配置） | 移至**仓库根** `archive/miniapp-pages/`，物理上位于上传包目录（`frontend/douyin-miniprogram/`）之外，从根本上保证"不得留在上传包内" |
| D5 | 验收要求"全工程 grep 无任何残留字符串" | `popularity` 在 home/players/player-detail 三页仍有 **10 处**数据展示相关命中（见第三节） | 这些命中与"人气数字展示"绑定，物理上属于 **C20-2 卡 A 部分（A2/A3/A4）**的删除对象。若在本卡一并删除即越界修改展示逻辑，违反"一张卡一个交付物"。**明写为未做项**，留待 C20-2，不静默绕过 |

## 一、逐项完成对照表

| # | 卡集要求 | 状态 | 文件/位置 | 证据 |
|---|---|---|---|---|
| 0 | 从 main 新建 `release/miniapp-lite` 分支 | ✅ 完成 | 分支基于 main `f26f70f` 创建 | `git branch` / 推送记录 |
| 1 | `tabBar.list` 4 项改 3 项，移除 popularity | ✅ 完成 | `app.json` tabBar 现仅剩 首页/选手/我的 | 见下方 app.json 终态 |
| 2 | `pages` 数组移除 suspicion 与 popularity | ✅ 完成 | `app.json` pages 现为 7 项 | 同上 |
| 3 | 两页面目录整体移出上传包 | ✅ 完成 | `git mv` 至仓库根 `archive/miniapp-pages/{suspicion,popularity}/`（见 D4） | commit 文件清单（rename 记录） |
| 4 | 移除 `onSpyTap()` | ✅ 完成 | `pages/home/index.js` 原 43~50 行，全文由 54 行减为 46 行 | commit diff；grep `onSpyTap` 零命中 |
| 5 | 移除 `spy-entry` 区块 | ✅ 完成 | `pages/home/index.ttml` 原 29~32 行 | commit diff；grep `spy-entry` 零命中 |
| 6 | 清除孤儿样式 | ✅ 完成 | `pages/home/index.ttss` 原 49~60 行（`.spy-entry` 与 `.spy-entry.disabled` 两个类，共 12 行） | commit diff；grep `spy` 零命中 |
| 7a | 全工程 grep `suspicion` 清零 | ✅ 完成 | 上传包目录内**零命中**（含大小写不敏感） | `C20-1_grep_result.txt` |
| 7b | 前端侧移除已无调用方的后端接口调用 | ✅ 完成 | `utils/api.js`：删除 `getSuspicionStatus`、`submitSuspicion`、`getPopularityBoard` 三个函数及 `module.exports` 中对应导出 | commit diff |
| 7c | 全工程 grep `popularity` 清零 | ⚠️ **部分完成** | 页面注册/tab/接口函数/跳转已清零；剩余 10 处为三页的人气**数字展示**代码（`targetPopularityText` 等） | 见 D5，属 C20-2 A2/A3/A4 范围，grep 结果已如实附上 |
| 8 | tabBar 截图 | ❌ **未做** | 沙箱内无抖音开发者工具，无法产生真实运行截图 | 替代证据：app.json 终态全文（下附）；请 John 在开发者工具导入 `release/miniapp-lite` 分支后补一张 3-tab 截图供 Claude 归档 |

## 二、app.json 终态（替代截图证据）

```json
{
  "pages": [
    "pages/home/index",
    "pages/players/index",
    "pages/player-detail/index",
    "pages/redeem/index",
    "pages/redeem-success/index",
    "pages/me/index",
    "pages/my-photos/index"
  ],
  "window": { "navigationBarTitleText": "红颜局中局", "navigationBarBackgroundColor": "#fff5f8", "navigationBarTextStyle": "black", "backgroundColor": "#fff5f8" },
  "tabBar": {
    "color": "#8d858a", "selectedColor": "#d79a39", "backgroundColor": "#ffffff", "borderStyle": "white",
    "list": [
      { "pagePath": "pages/home/index", "text": "首页" },
      { "pagePath": "pages/players/index", "text": "选手" },
      { "pagePath": "pages/me/index", "text": "我的" }
    ]
  }
}
```

## 三、grep 验收结果（完整输出另存 `C20-1_grep_result.txt`）

范围：`frontend/douyin-miniprogram/`（上传包目录）。

| 关键词 | 结果 |
|---|---|
| `suspicion`（不区分大小写） | **零命中** |
| `onSpyTap` / `spy-entry` / `卧底识破` | **零命中** |
| `popularity`（不区分大小写） | **10 处命中**，全部为 home（6）/player-detail（3）/players（1）三页的人气数字展示代码，属 C20-2 A2/A3/A4 删除对象 |

剩余 10 处命中明细（供 C20-2 直接对账）：

```
pages/home/index.js:10,11,34,35（targetPopularityText / teamPopularityText 数据字段与赋值）
pages/home/index.ttml:35,39（两处 metric-box 数字绑定 → C20-2 A4）
pages/player-detail/index.js:11,34 + index.ttml:17（当前人气值展示 → C20-2 A3）
pages/players/index.js:26（选手列表人气数字 → C20-2 A2）
```

## 四、验收标准自检

| 验收标准 | 自检结果 |
|---|---|
| 小程序仅 3 个 tab，无路径可进投票页/人气榜页 | ✅ tabBar 3 项；两页面已从 `pages` 注册表移除，`navigateTo` 跳转入口（onSpyTap）已删——**未注册页面即使存在跳转也会报错，现跳转与注册均已清除** |
| 上传包内不含 suspicion/popularity 相关文件 | ✅ 两目录已物理移出至仓库根 `archive/miniapp-pages/` |
| 全工程 grep 为空（或仅剩 archive/） | ⚠️ suspicion 全清；popularity 余 10 处展示代码，已明写为 C20-2 范围（见 D5），非静默遗留 |

## 五、蓝军备注（一条，供 Claude 裁定）

卡集第 7 步"后端接口调用如已无调用方，前端侧一并移除"已执行（api.js 三函数删除）。但**后端**的 `/api/suspicion/*` 与 `/api/popularity/board` 接口本体仍在 main 分支正常存在——这是正确的（C20-3/5 还要用人气数据，且 release 分支只做减法不动后端）。提请 Claude 在 C20-5 大屏页设计时确认：大屏页复用的"后台现有人气接口"指 admin 侧接口还是这条小程序侧 `/api/popularity/board`，两者鉴权模型不同，影响 C20-5 第 6 步的令牌方案。
