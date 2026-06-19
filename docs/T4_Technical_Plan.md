# 红颜局中局 · T4 场控后台下拉优化技术方案

> **提交人**：Manus (T4 流)
> **日期**：2026.06.20
> **审查人**：Claude

## 一、需求背景与目标

目前场控后台 (`frontend/control-admin/src/App.vue`) 中，多处涉及目标选手、目标团队、轮次等输入的表单均采用 `el-input-number` 手动输入 ID。运营人员难以记忆数字 ID，极易出错。
本任务（T4）的目标是将这些数字 ID 输入框改造为**下拉选择框（Select）**，下拉列表中显示名称与编号，但绑定的值（v-model）依然是原有的数字 ID，以确保**不改动任何后端接口与业务逻辑**。

## 二、边界与纪律声明

根据《三流并行协调原则(C18 / T4 / C14)》及最新代码勘察结果，T4 团队承诺严格遵守以下边界：

1. **专属领地**：仅修改 `frontend/control-admin/src/App.vue` 内部的 `el-form` 表单交互部分（将 ID 输入替换为下拉选择）。
2. **禁区（绝对不碰）**：
   - 不修改任何后端接口代码及业务逻辑。
   - **不碰鉴权机制**：不修改 `App.vue` 顶部的 `adminToken` 输入、保存逻辑及 `operatorId` 逻辑。
   - **不碰请求层**：绝不修改 `http.ts` 及任何已有的 token 注入与 401 处理逻辑。
3. **基于现状**：经查，C18 相关的鉴权前端（`adminToken` 等）已合入 main。T4 将基于最新 main 分支进行修改，绝不覆盖或回退 C18 的成果。

## 三、改造方案详述

改造范围主要集中在 `App.vue` 的模板 `<template>` 部分，将现有的 `<el-input-number>` 替换为 `<el-select>`。数据源利用现有的响应式变量（`players`, `teams`, `rounds`）。

### 1. 依赖的数据源
当前 `App.vue` 中已在 `onMounted` 时通过 `refreshBasicData()` 拉取了基础数据：
- `players`：选手列表，包含 `playerId`, `number`, `name`。
- `teams`：团队列表，包含 `teamId`, `name`。
- `rounds`：轮次列表，包含 `roundId`, `name`。

### 2. 具体改造点清单

以下是所有需要将 `el-input-number` 替换为 `el-select` 的表单字段：

| Tab 页签 | 面板/表单 | 字段名 | 改造方式 (使用 el-select) |
| --- | --- | --- | --- |
| **场控监控** | 人气看板 | roundId (`boardRoundId`) | 绑定 `boardRoundId`，遍历 `rounds`，显示 `[ID] 名称` |
| **场控操作** | 集赞目标切换 | 目标 ID (`collectForm.targetId`) | 依据 `collectForm.mode` 动态切换：<br> - player/spy: 遍历 `players` 显示 `[序号] 姓名`<br> - team: 遍历 `teams` 显示队名<br> - pool: 禁用 |
| **场控操作** | 集赞目标切换 | 轮次 ID (`collectForm.roundId`) | 遍历 `rounds` |
| **场控操作** | 模拟注入 | 目标选手 (`simulateForm.targetId`) | 遍历 `players` |
| **场控操作** | 手动调分 | 目标 ID (`manualForm.targetId`) | 依据 `manualForm.targetType` 动态切换（同集赞目标） |
| **场控操作** | 手动调分 | 轮次 ID (`manualForm.roundId`) | 遍历 `rounds` |
| **场控操作** | 团队人气均分 | 团队 ID (`distributionForm.teamId`) | 遍历 `teams` |
| **场控操作** | 团队人气均分 | 轮次 ID (`distributionForm.roundId`) | 遍历 `rounds` |
| **基础数据** | 分队与卧底设置 | 查询条件：轮次 ID (`playerRoundFilterRoundId`) | 遍历 `rounds` |
| **基础数据** | 分队与卧底设置 | 选手 ID (`playerRoundForm.playerId`) | 遍历 `players` |
| **基础数据** | 分队与卧底设置 | 队伍 ID (`playerRoundForm.teamId`) | 遍历 `teams` |

*(注：写真管理 tab 下的 `playerId` 筛选已经是 `el-select`，本次无需修改，保持原样。基础数据管理中新增选手/队伍/轮次时输入的自身 ID/序号保持输入框不变。)*

### 3. 代码修改示例

以**手动调分**面板的“目标 ID”为例：

**修改前：**
```html
<el-form-item label="目标 ID">
  <el-input-number v-model="manualForm.targetId" :min="1" :disabled="manualForm.targetType === 'pool'" />
</el-form-item>
```

**修改后：**
```html
<el-form-item label="目标 ID">
  <el-select v-model="manualForm.targetId" :disabled="manualForm.targetType === 'pool'" filterable placeholder="请选择目标">
    <!-- 当模式为 player 或 spy 时显示选手列表 -->
    <template v-if="manualForm.targetType === 'player' || manualForm.targetType === 'spy'">
      <el-option v-for="p in players" :key="p.playerId" :label="`${p.number}号 ${p.name}`" :value="p.playerId" />
    </template>
    <!-- 当模式为 team 时显示团队列表 -->
    <template v-else-if="manualForm.targetType === 'team'">
      <el-option v-for="t in teams" :key="t.teamId" :label="t.name" :value="t.teamId" />
    </template>
  </el-select>
</el-form-item>
```

对于纯轮次选择，例如：
```html
<el-form-item label="轮次 ID">
  <el-select v-model="manualForm.roundId" filterable placeholder="请选择轮次">
    <el-option v-for="r in rounds" :key="r.roundId" :label="`[${r.roundId}] ${r.name}`" :value="r.roundId" />
  </el-select>
</el-form-item>
```

### 4. 数据加载与默认值处理

- 现有代码在 `onMounted` 时已调用 `refreshBasicData()`。
- 当数据加载完毕后，下拉框会自动映射已有的默认 ID（例如 `1`）。
- 改造纯粹为 UI 展现层替换，不改变传递给 API 接口的 payload 结构。

## 四、执行步骤

1. 等待 Claude 审查并批准本方案。
2. 再次执行 `git pull --rebase origin main` 确保基于最新代码。
3. 在 `frontend/control-admin/src/App.vue` 中进行上述 UI 替换。
4. 启动本地前端与后端服务，进行全量回归测试：
   - 验证各个表单下拉框是否正常显示名称。
   - 验证表单提交时传递的参数是否依然是正确的 ID。
   - 验证 C18 的鉴权逻辑是否完好无损。
5. 提交代码并推送到远程 `main` 分支。

---
请 John 将此方案转交 Claude 裁定。若无异议，我将立即开始编码。
