# C19 收尾阶段整改实施报告 v1

> **基线**：GitHub main `95fbc1a`  
> **内容**：P0-1 下载修复、P0-2 投票改造、P0-3 手动加成与识破开关、P1/P2 全项  
> **作者**：Manus AI

## 一、核心实现摘要

### 1. P0-1 下载卡库 40101 修复
- **实现**：前端废弃 `window.open`，改用 `fetch` 请求，在 Header 注入 `X-Admin-Token`。响应为 200 时转 Blob 并触发下载，文件名含 batchId；非 200 时捕获 JSON 抛出中文错误。
- **合规**：彻底避免了在 URL 暴露管理凭证。

### 2. P0-2 识破投票规则变更（按组多选）
- **后端**：`SuspicionSubmitRequest` 改为接收 `List<Integer> suspectPlayerIds`。事务内逐个尝试 `insertSubmission`，捕获 `DuplicateKeyException` 以忽略重复（复用现有唯一索引），最终响应明确的 `accepted` 与 `duplicated` 列表。
- **前端**：`suspicion/index.ttml` 按 `teamId` 对候选人进行分组渲染。支持组内/跨组多选，已投过的选手（后端 `status` 接口返回）置灰标为“已投”。

### 3. P0-3 手动加成与识破开关
- **手动加成**：
  - **选手级**：复用 `coefficient_ledger`，新增 `manualAdjustPlayer`。
  - **团队级**：新增 `team_round_stats.coefficient` 字段和 `team_coefficient_ledger` 审计表。团队池人气读取点（`C9QueryMapper.findTeamBoard` 等）已全量接入系数乘法 `* coefficient / 100`。
- **识破开关**：后台新增“开启/关闭卧底识破”按钮，封装了底层 `spy/pool` 模式切换，并带二次确认和审计日志。

### 4. P1-3 选手编号与序号拆分
- **原则**：**未改动主键 `player_id`**。
- **实现**：数据库新增 `display_code` 字段并加唯一约束。前端录入改为输入 4 位数 `displayCode`，原 `number` 字段在后端改为 `MAX(number) + 1` 自动递增且不可修改。

### 5. P1 其他项与 P2
- **中英文清理**：前端展示全中文，状态枚举保持英文。全局改名“真相识破”为“卧底识破”。
- **卧底 Tab 过滤**：`PopularityBoardService` 中查出列表后，过滤出 `isSpy == true` 的成员。
- **后台 UX**：增加“退出登录”按钮（清空 localStorage）；补齐退款 UI（只扣人气，不回收会员/写真）。
- **小项**：粘贴卡密失败降级提示；导出空批次返回空内容；发码无写真增加警告。

## 二、测试结果
- **C13 投票专项测试**：已更新为多选结构，验证了同人重复投同一选手被拒且不影响新增选手，测试通过。
- **后端全量回归**：`mvn test` 共 100 个测试用例，**全部通过（0 failures, 0 errors）**。
- **前端构建**：`pnpm build` 修复 TS 类型后编译通过。

## 三、请 Claude 验收
代码已推送到 `main` 分支。请拉库复核上述实现，尤其是：
1. P0-2 投票多选的宽容处理策略。
2. P0-3 团队系数在 `C9QueryMapper` 中的应用。
3. P1-3 `display_code` 的无损新增。
