# API 契约复审：UI 覆盖核查记录 V1.0

## 核查范围

本记录基于墨刀 UI 观察记录与 Claude 页面级 API 契约，对首页、人气页、卡密核销页、核销成功页、我的页、选手详情页、真相识破页进行字段与状态覆盖检查。

## UI 覆盖结论

Claude API-0 至 API-4 基本覆盖了 P0 主链路，但仍需要明确“哪些 UI 模块不在 C9/P0 范围内”。最重要的范围边界是：选手详情页没有独立 API，首页四入口已删除，个人动态/关注/距离/订单查询/会员群/会员有效期均不应进入彩排开发稿。真相识破 API-5 后置，因此真相识破页面不应作为 6/14 彩排可交互页面。

| UI 页面 | Claude API 覆盖 | 覆盖结论 | 仍需注意 |
| --- | --- | --- | --- |
| 首页 | API-1 覆盖 `liveStatus/roundName/currentMode/targetDisplayName/targetPopularity/teamDisplayName/teamPopularity/spyChannelOpen/updatedAt`。 | 主信息覆盖较完整。 | 首页顶部轮播、本周精选推送是否静态；首页四入口已决策删除；非直播态需 UI 出图。 |
| 人气页 | API-2 覆盖 `tab/roundId/spyTabEnabled/items[]`，并要求按 number 升序。 | 看板主字段覆盖。 | 需明确团队 tab 的 item 字段形态；spy tab 未激活时前端灰置，不能请求后报错。 |
| 卡密核销页 | API-3 覆盖提交与五类错误码。 | 主流程覆盖。 | 需要 UI 出图覆盖 `invalid_format/not_found/already_used/locked/round_not_available`，并补提交中、防重复点击、网络失败。 |
| 核销成功页 | API-3 成功响应覆盖 `playerNumber/playerName/teamName/points/photoAssetId/photoPreviewUrl/collected`。 | 已采纳 Manus 字段建议。 | 会员有效期必须删除；写真加载失败需要降级状态。 |
| 我的页 | API-0 + API-4 覆盖登录和写真列表。 | 我的写真闭环可做。 | 会员有效期、订单查询、加入会员群不在 P0；未登录、空收藏、图片加载失败需出状态。 |
| 选手详情页 | 当前契约无独立选手详情 API。 | 不应作为 P0 动态页面开发。 | 若保留，建议仅静态或复用人气 board 基础数据；个人动态/关注/距离删除。 |
| 真相识破页 | API-5 标记 P1，彩排后随 C13。 | 不进入 C9/P0。 | 若 UI 保留入口，应由 `spyChannelOpen=false` 隐藏或灰置，不做提交。 |

## 蓝军重点

Claude 契约已基本解决“页面字段有无接口来源”的问题，但必须把 UI 修改清单和 API 范围冻结同步给彬少与前端。否则即使 API-0 到 API-4 正确，前端仍可能实现选手详情页社交模板、首页静态四入口、会员有效期、订单查询等未在契约内的模块，造成范围反弹。
