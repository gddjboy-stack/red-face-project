# C13「卧底识破」技术方案 v1（提交 Claude 审批版）

> 作者：Manus AI  
> 日期：2026-06-17  
> 当前状态：仅技术方案，**未编码**  
> 前置状态：C12 已通过 Claude 审查，彩排底线 C1~C12 + C19 已完成。[1] [2]

## 〇、开卡背景与目标

Claude 在 C12 审查中建议下一张卡优先推进 C13「卧底识破」，理由是它是今晚内测中最有玩法亮点的一张。[1] 从蓝军角度看，C13 不能直接编码，因为它同时牵涉**玩法规则、用户提交限制、轮次状态、后台开关、实时进度展示和合规文案**。因此本文件只提出技术方案，等待 Claude 审批后再进入编码。

C13 的目标是把 C11 首页中已经预留的“真相识破”入口从占位状态升级为可参与互动，但仍保持最小可控范围：用户只能在后台开启的轮次中提交一次判断；页面按选手序号展示候选与进度，不按数值排序；后台不新增复杂活动编排系统，优先复用现有 `collect_state`、`player_round`、`suspicion_votes` 与 C19 基础数据能力。[3] [4] [5]

> 本方案的核心原则是：**先做一个能内测、能讲清楚、能审计的最小闭环，而不是一次性做完整玩法系统**。C13 不应破坏 C1~C12 的彩排底线，也不应引入新的平台依赖。

## 一、现有基础能力审计

仓库中已经存在 C13 的一部分技术地基。C11 首页已有“真相识破”入口，但当前点击后只提示“真相识破将在 C13 开放”；后端 `LiveHomeResponse` 已有 `spyChannelOpen` 字段，但 `LiveHomeService` 当前初始化为 `false`，并未真正根据场控状态开启。[3] [6] 数据层方面，`player_round` 已保存 `is_spy` 与 `spy_status` 字段，C19 后台基础数据页也已经支持“分队与卧底设置”；`suspicion_votes` 表已经存在，可作为用户提交判断的记录表。[4] [5] [7]

| 模块 | 现状 | C13 可复用点 | 风险 |
|---|---|---|---|
| 小程序首页 | 已有“真相识破”入口，但只提示 C13 开放 | 可将入口改为跳转新页面 | 入口开启规则必须由后端决定，前端不能自行判断 |
| 后端首页 API | `spyChannelOpen` 字段已存在，但目前始终为 false | 可改为当 active 轮次且当前场控模式为 `spy` 时开启 | 需确认是否允许 C13 修改 `LiveHomeService` |
| 基础数据 | `player_round.is_spy` 已存在，后台可设置是否卧底 | 可作为揭晓真相与候选校验依据 | 不应在小程序未揭晓前泄露 `is_spy` |
| 识破记录 | `suspicion_votes` 表已存在 | 可记录 user、round、team、suspect_player_id、voted_at | 现有唯一键允许同一用户对不同候选重复插入，需 Service 层限制一次提交 |
| 人气看板 | 已支持 `spy` tab 与 `spy_popularity` | C13 可展示卧底相关热度，但不作为判断提交依据 | 不得按数值排序或制造榜单刺激 |

## 二、范围裁定建议

C13 建议定义为 **P1 最小可参与版**，不做完整赛制引擎。该版本的边界是：用户可以在开启环节中进入页面、查看候选、提交一次判断、看到按序号排列的总体分布；后台可以通过既有“集赞目标切换”为 `spy` 模式来开启入口，通过 C19“分队与卧底设置”维护卧底身份。结果揭晓可以先在页面中以“直播间揭晓为准”处理，不在 C13 自动改变选手状态或发放奖励。

| 纳入 C13 | 暂不纳入 C13 |
|---|---|
| 新增小程序 `pages/suspicion/index` 页面 | 不做复杂任务引擎或多阶段活动编排 |
| 新增 `GET /api/suspicion/status` 查询状态与候选 | 不自动淘汰选手、不自动改 `player_status` |
| 新增 `POST /api/suspicion/submit` 提交一次判断 | 不做奖金、权益、会员或写真发放 |
| 后端 Service 层限制每用户每轮只能提交一次 | 不做 WebSocket，先用刷新或短轮询 |
| `spyChannelOpen` 改为由 active 轮次 + `collect_state.mode=spy` 决定 | 不新增后端生成平台链接，不碰 C12 H5 |
| 后台最小展示提交分布与候选状态 | 不新增大规模后台活动配置系统 |

## 三、玩法与合规文案建议

用户侧应统一使用“识破”“判断”“线索”“真相”等表达，避免出现敏感词。页面不能展示“第几名”“领先”“冲榜”等排名化文案，也不能按提交数量或人气值排序。候选集合与进度集合必须一致，并按选手序号升序展示，避免早期 UI 审查指出的“候选名单与进度名单错位”问题。[8]

| 场景 | 建议文案 | 禁止或避免 |
|---|---|---|
| 首页入口未开启 | “该环节暂未开启” | 不暗示用户可以提前提交 |
| 首页入口开启 | “真相识破进行中 · 点击参与” | 不使用排名、冲榜、诱导性措辞 |
| 页面说明 | “根据直播线索，选择你认为最可疑的选手。每轮仅可提交一次判断。” | 不承诺奖励，不引导金钱行为 |
| 提交按钮 | “确认提交判断” | 不使用敏感词 |
| 已提交状态 | “本轮判断已提交，请等待直播间揭晓。” | 不允许重复提交 |
| 进度展示 | “当前判断分布，按选手序号排列” | 不按数量排序，不显示名次 |

## 四、后端技术方案

后端保持 Java 17、Spring Boot 3、MyBatis、构造器注入和现有 `ApiResponse` 包裹风格。C13 不引入 JPA，不新增 ORM 框架，不修改 C2~C12 已完成主链路。建议新增 `SuspicionController`、`SuspicionService`、`SuspicionMapper` 与 C13 专用 DTO。

### 4.1 API 契约草案

| API | 方法 | 用途 | 登录要求 | 说明 |
|---|---|---|---|---|
| `/api/suspicion/status?roundId=1` | GET | 查询识破页面状态、候选、分布、用户是否已提交 | 需要 Bearer 登录态 | `roundId` 可选，缺省使用 active 轮次 |
| `/api/suspicion/submit` | POST | 提交本轮判断 | 需要 Bearer 登录态 | userId 只从 `@CurrentUser` 获取，不接受前端传 userId |
| `/api/admin/suspicion/status?roundId=1` | GET | 后台查看本轮提交分布 | 暂沿用 operatorId 之外的彩排后台风格 | 可在 C13 实施时判断是否必须做 |

`GET /api/suspicion/status` 建议返回以下结构。这里的 `submittedPlayerId` 只表示当前用户是否已经提交过判断；`actualSpyPlayerId` 默认不返回，避免在小程序端提前泄露卧底身份。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "roundId": 1,
    "roundName": "第1轮",
    "open": true,
    "submitted": false,
    "submittedPlayerId": null,
    "candidates": [
      {
        "playerId": 1,
        "number": 3,
        "playerName": "林夏",
        "teamId": 10,
        "teamName": "A组",
        "count": 12,
        "ratio": 0.34
      }
    ],
    "updatedAt": 1781234567
  }
}
```

`POST /api/suspicion/submit` 建议请求体如下。为了保持一次提交规则，后端必须先检查同一 `userId + roundId` 是否已经存在任意记录；不能只依赖现有 `uq_user_round_suspect`，因为该唯一键只能阻止同一用户对同一候选重复插入，不能阻止同一用户对多个候选分别提交。[5]

```json
{
  "roundId": 1,
  "suspectPlayerId": 3
}
```

成功响应建议返回提交后的状态摘要，而不是只返回 true，便于前端立即刷新 UI。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "roundId": 1,
    "submitted": true,
    "submittedPlayerId": 3,
    "message": "本轮判断已提交，请等待直播间揭晓。"
  }
}
```

### 4.2 错误码建议

| code | businessCode | 场景 | 前端固定文案 |
|---|---|---|---|
| 41001 | `not_open` | 当前无 active 轮次或 `spyChannelOpen=false` | “该环节暂未开启。” |
| 41002 | `invalid_candidate` | 候选不属于当前轮次、已离场或参数非法 | “该选手暂不可选择，请刷新后重试。” |
| 41003 | `already_submitted` | 当前用户本轮已经提交过判断 | “本轮判断已提交，请等待直播间揭晓。” |
| 41004 | `round_mismatch` | 请求 roundId 与当前可参与轮次不一致 | “环节状态已更新，请刷新页面。” |
| 41000 | `unknown` | 兜底错误 | “提交失败，请稍后重试。” |

### 4.3 开启规则建议

C13 建议将 `spyChannelOpen` 定义为：存在 active 轮次，且当前 `collect_state.mode='spy'`，且 `collect_state.round_id` 与 active 轮次一致或为空时视为当前轮次。这样可以复用 C10 后台的“集赞目标切换”能力，无需新增活动开关表。[6] 但这会改变 `LiveHomeService` 中当前始终返回 `false` 的占位逻辑，因此需要 Claude 明确批准。

| 条件 | 结果 |
|---|---|
| 无 active 轮次 | `open=false` |
| active 轮次存在，但 `collect_state.mode` 不是 `spy` | `open=false` |
| active 轮次存在，`collect_state.mode='spy'`，且 round 匹配 | `open=true` |
| `collect_state.target_id` 指向某位候选 | 可作为直播当前聚焦对象，但不作为唯一候选 |

### 4.4 候选集合建议

候选集合应来自当前轮次 `player_round` 中的选手，并关联 `players` 与 `teams` 展示基础信息。建议仅包含 `player_status IN ('normal','free')` 的选手，排除 `eliminated`。候选排序固定为 `players.number ASC, players.player_id ASC`，严禁按提交数量、比例或人气值排序。

后端聚合分布时可以用 `LEFT JOIN suspicion_votes` 统计每个候选的 `count`，再由 Service 计算 `ratio = count / totalCount`。当 totalCount 为 0 时所有 ratio 为 0。该 ratio 只用于进度条长度，不作为排序依据。

### 4.5 数据一致性与事务

提交接口应使用事务。核心步骤为：校验轮次开启状态；校验候选属于当前轮次；检查当前用户在该轮是否已有任意提交；插入 `suspicion_votes`；返回提交结果。由于现有表没有 `operator_id` 字段，用户侧提交不进入 `operations_log`，但后台开关仍沿用现有 `collect_state` 与操作日志。

## 五、小程序前端方案

小程序新增 `pages/suspicion/index`，并在 `app.json` 注册。首页 `onSpyTap` 逻辑由提示改为：如果 `home.spyChannelOpen` 为 false，继续提示“该环节暂未开启”；如果为 true，则跳转到 `/pages/suspicion/index?roundId=${home.roundId}`。[3]

| 页面状态 | 前端表现 | 数据来源 |
|---|---|---|
| 加载中 | 展示轻量 loading | 页面 `onLoad` 调用 status API |
| 未开启 | 展示“该环节暂未开启”与返回按钮 | `open=false` |
| 可提交 | 展示候选卡片、说明与确认按钮 | `candidates` |
| 已提交 | 高亮已提交候选，按钮置灰 | `submitted=true` |
| 提交失败 | Toast + 页面错误提示 | 错误码映射固定文案 |
| 刷新分布 | 用户下拉或点击刷新按钮 | 重新调用 status API |

前端候选卡片应按序号排列，展示“选手序号、姓名、队伍、当前判断分布”。分布可以使用非排行化进度条，但不显示“领先”“第一”等文案。若 Vincent 后续希望增强娱乐性，可在视觉层面增加“线索卡”“剧情热度”等包装，但不得改变排序逻辑或引入敏感词。

## 六、管理后台方案

为了降低 C13 复杂度，后台建议先做最小增强。第一，现有“集赞目标切换”已经支持 `spy` 模式，可以作为开启入口；第二，现有“分队与卧底设置”已经支持 `isSpy`，可以作为卧底身份维护入口；第三，可选新增“识破监控”卡片，用于查看当前轮次各候选的提交分布与总提交数。

| 后台能力 | C13 建议 | 理由 |
|---|---|---|
| 开启/关闭识破 | 复用 `collect_state.mode=spy` 开启，切回其他模式关闭 | 避免新增活动开关系统 |
| 维护卧底身份 | 复用 C19 “分队与卧底设置” | 已有 `isSpy` 字段和后台 UI |
| 查看提交分布 | 可新增 `GET /api/admin/suspicion/status` 与一个监控卡片 | 对内测有帮助，但不是用户侧闭环的必要条件 |
| 揭晓真相 | C13 暂不做自动揭晓逻辑 | 直播中由主持/导演揭晓，避免提前泄露 |
| 回滚用户提交 | C13 暂不做 | 避免引入复杂审计与争议处理 |

## 七、测试方案

C13 编码获批后必须补 JUnit 测试，重点覆盖服务层和 Controller 层。由于 C12 已经确认后端 58 测试全绿，C13 应在此基础上新增测试而不是降低原有覆盖。[2]

| 测试类建议 | 覆盖场景 |
|---|---|
| `SuspicionServiceC13Test` | 未开启不能提交、非法候选不能提交、每用户每轮只能提交一次、提交后分布正确 |
| `SuspicionControllerC13Test` | 未登录 401、status 成功、submit 成功、错误码映射 |
| `LiveHomeControllerC13Test` | `collect_state.mode=spy` 时 `spyChannelOpen=true`，其他模式为 false |
| `AdminSuspicionControllerC13Test`（如做后台监控） | 后台分布查询与轮次参数校验 |

前端验证建议以静态检查和开发者工具为主：确认页面注册、入口跳转、未开启提示、已提交置灰、错误码文案和候选排序。真机验证可纳入 6/22 真实环境测试补充，不应在本地沙箱中伪造。

## 八、蓝军风险清单

| 风险 | 影响 | 建议控制 |
|---|---|---|
| `spyChannelOpen` 当前始终 false | 首页入口无法真正进入 C13 页面 | 需 Claude 批准修改 `LiveHomeService` 开启规则 |
| 现有唯一键不能限制每用户每轮一次 | 用户可能选择多个候选 | Service 层先查 `userId + roundId` 任意记录，命中即返回 `already_submitted` |
| 候选集合与进度集合错位 | 用户误解玩法，开发容易误接 | status API 后端一次性返回候选与分布，前端不自行拼接 |
| 提前泄露卧底身份 | 破坏节目悬念 | 用户 API 不返回 `isSpy` 或实际卧底 ID，后台也谨慎展示 |
| 排序或文案触碰合规红线 | 影响小程序审核与直播表达 | 固定按选手序号排序，避免敏感词与排行刺激 |
| 后台能力过度膨胀 | 拖慢今晚内测 | C13 只做最小闭环，复杂揭晓/回滚/奖励后置 |

## 九、建议提交给 Claude 裁定的 6 个问题

| 编号 | 问题 | Manus 建议 |
|---|---|---|
| C13-Q1 | 是否批准 `spyChannelOpen` 改为由 active 轮次 + `collect_state.mode='spy'` 决定？ | 批准，这是复用 C10 场控能力的最小方案 |
| C13-Q2 | 用户每轮是否只允许提交一次判断？ | 批准，Service 层用 `userId + roundId` 限制一次提交 |
| C13-Q3 | 候选集合是否来自当前轮次 `player_round`，并排除 `eliminated`？ | 批准，避免候选和进度不一致 |
| C13-Q4 | 用户侧 API 是否禁止返回 `isSpy` 与实际卧底身份？ | 批准，避免提前泄露剧情 |
| C13-Q5 | C13 是否暂不做自动揭晓、奖励、回滚和复杂活动配置？ | 批准，先完成最小可参与闭环 |
| C13-Q6 | 后台是否只做可选监控卡片，不阻塞用户侧 C13？ | 批准，如时间不足可先只做用户侧 API 与页面 |

## 十、预期交付物

如 Claude 批准，本卡编码阶段建议交付以下内容。所有技术决策与实施报告继续以 Markdown 归档到 GitHub，供 Claude 与团队复审。

| 交付物 | 说明 |
|---|---|
| 后端 C13 API | `SuspicionController`、`SuspicionService`、`SuspicionMapper`、DTO、错误码映射 |
| 小程序 C13 页面 | `pages/suspicion/` 及首页入口跳转 |
| 可选后台监控 | Admin 端识破分布卡片与 API |
| 测试 | 新增 C13 JUnit 测试，确保原 58 项回归不退化 |
| 文档 | `C13_Implementation_Report_v1.md` 与 C13 联调清单 |

## 十一、引用

[1]: docs/c12/Claude_Review_C12_Passed_Rehearsal_Baseline_Complete.md "Claude 审查 — C12 通过，彩排底线完成"
[2]: C12_Closure_Summary_v1.md "C12 结项总结与彩排底线完成确认 v1"
[3]: frontend/douyin-miniprogram/pages/home/index.js "小程序首页：onSpyTap 当前占位提示 C13 开放"
[4]: frontend/control-admin/src/App.vue "Admin 基础数据：分队与卧底设置"
[5]: backend/redface-backend/src/test/resources/schema-h2.sql "测试 schema：player_round 与 suspicion_votes 表"
[6]: backend/redface-backend/src/main/java/com/redface/query/LiveHomeService.java "LiveHomeService：spyChannelOpen 当前初始化为 false"
[7]: backend/redface-backend/src/main/java/com/redface/mapper/BasicDataMapper.java "BasicDataMapper：upsertPlayerRound 支持 is_spy"
[8]: docs/API契约/当前页面级API契约定稿_C9依据_V1.0.md "页面级 API 契约：真相识破页候选与进度一致性问题、API-5 待 C13 定稿"
