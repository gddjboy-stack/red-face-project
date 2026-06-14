# API 契约复审：测试矩阵与蓝军风险 V1.0

## 现有测试基线

当前后端已有 C2 至 C6 的核心单元测试和集成测试，覆盖人气值入账、场控归属、积分衰减、卡密核销并发抢占、防爆破、无轮次保护，以及卡密生成和导出。现有测试为 C9 API 层提供了业务规则基线，但尚未覆盖 Controller 层、统一响应包、页面级 DTO、登录态和前端状态码。

## C9 建议新增测试矩阵

| API | 测试重点 | 必测用例 |
| --- | --- | --- |
| API-0 登录 | code 换取 userId、登录态建立、异常 code 处理 | code 正常返回 userId；重复登录同一 openid 稳定映射；无效 code 返回固定错误；后续 `/api/me/photos` 未登录返回未授权。 |
| API-1 首页直播状态 | active/idle、当前互动归属、显示字段聚合 | active 轮次返回 `liveStatus=live`；无 active 返回 `idle`；collect_state 为 player/team/spy/pool 时目标名正确；`spyChannelOpen=false` 时前端应隐藏识破入口。 |
| API-2 人气看板 | 三档 tab、按 number 升序、禁止排名化 | player tab 按选手 number 升序；team tab 按团队/配置顺序返回；spy tab 未激活时 `spyTabEnabled=false`；任何 tab 不得按 value 降序。 |
| API-3 卡密核销 | 成功 DTO、五类错误码、并发幂等 | 成功返回 playerNumber/playerName/teamName/points/photoPreviewUrl/collected；invalid_format、not_found、already_used、locked、round_not_available 均返回契约错误码；并发核销只有一个成功。 |
| API-4 我的写真 | 用户维度列表、空数据、图片字段 | 未登录返回未授权；无收藏返回 total=0/items=[]；有收藏时 join photo_assets 和 players 返回 previewUrl/playerName/createdAt；不同用户数据隔离。 |
| API-5 真相识破 | 当前不做 | C9 不应包含该接口的实现和测试，避免范围扩大。 |

## 蓝军风险意见

第一，Claude 契约中 API-3 的错误码同时出现 `40001 invalid_format` 这种数字码与字符串枚举，而现有 `RedeemResult` 使用的是字符串 code，例如 `invalid_format`、`not_found`、`already_used`、`locked`、`round_not_available`。C9 必须统一“外层 code=0/非0”与“业务错误码”的表达方式，建议采用外层 `code` 为数值，`data.businessCode` 或 `error` 为字符串枚举，避免前端和后端各自理解。

第二，API-0 登录是必要补充，但当前 schema 未发现用户表、openid 映射表或 session 表。如果 C9 只临时把 openid 作为 userId 传下去，必须明确是否脱敏、是否持久化、是否可跨设备稳定识别。若使用内存态，会影响“我的写真”长期可用性。

第三，API-1 和 API-2 都需要新增聚合查询，不能让前端分别请求多个单值接口再拼 UI。页面级 API 的价值就是让前端直接渲染；否则实时首页和人气页会出现多接口并发、刷新不一致和状态难处理的问题。

第四，API-4 虽然被 Claude 定义为“一条 SQL”，但它涉及用户身份、收藏表、写真表和选手表的 join，并且需要处理空数据和图片 URL 缺失。建议不要低估测试工作量。

第五，C9 验收不应只验接口返回 200，而应以 UI 字段、状态和测试用例为验收基准。所有 P0 页面必须能回答三个问题：字段从哪个 API 来，失败时显示什么，测试怎么证明不会破坏合规和主链路。
