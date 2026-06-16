# C11 抖音小程序前端实施报告 v1

**作者：Manus AI**  
**日期：2026-06-16**  
**适用范围：C11 用户端抖音原生小程序前端；`frontend/douyin-miniprogram`**

## 一、执行背景

本次 C11 编码依据 Claude 对 `C11_Douyin_MiniProgram_Implementation_Plan_v2` 的最终确认执行。Claude 明确批准启动 C11 编码，并裁定本期实现首页、人气看板、卡密核销、核销成功、极简我的写真 5 个 P0 页面；底部“选手”Tab 采纳方案 B，即点击进入复用 API-2 的 player 视图，而不是实现完整选手详情动态页。[1]

Claude 同时重申了 C11 的编码红线：人气看板严格按后端顺序展示，前端禁止按 `value` 排序；核销成功页必须使用“数字写真已自动存入我的收藏”文案；剪贴板只在用户点击“粘贴”时读取；核销不传 `userId`，只使用 Bearer token；图片必须走接口 `previewUrl`、清新占位图和加载失败降级；错误五态按 40001~40005 固定文案处理；不得出现“打榜/排名/应援/PK打赏”等敏感词。[1]

## 二、实现范围

本次新增 `frontend/douyin-miniprogram` 抖音原生小程序工程。该工程与 C10 的 `frontend/control-admin` 完全分离，不共用 Vue 后台代码。工程使用原生 `ttml/ttss/js/json` 结构，并提供统一请求封装、登录态封装、格式化工具、API 封装和清新舞台风占位图。

| 类型 | 文件/目录 | 说明 |
|---|---|---|
| 工程入口 | `app.js`、`app.json`、`app.ttss`、`project.config.json` | 抖音原生小程序入口、页面注册、底部 Tab 与全局样式。 |
| 配置 | `config/env.js` | dev/staging/prod baseURL 预留，默认 dev。 |
| 工具 | `utils/request.js` | 统一 `{code,message,data}` 响应处理和 Bearer token 注入。 |
| 工具 | `utils/auth.js` | `tt.login`、API-0 登录、token/userId 本地缓存。 |
| 工具 | `utils/api.js` | 封装 API-1 至 API-4 调用。 |
| 工具 | `utils/constants.js`、`utils/format.js` | 错误五态文案、缓存 key、数字和时间格式化。 |
| 组件 | `components/empty-state` | 通用空态组件。 |
| 资源 | `assets/placeholders/photo_clean_stage.svg` | 清新舞台风占位图，用于写真缺失或加载失败降级。 |

## 三、页面实现

| 页面 | 路由 | API | 实现说明 |
|---|---|---|---|
| 首页 | `pages/home/index` | API-0、API-1 | 登录后展示直播状态、当前互动计入对象、目标人气、团队人气、真相识破灰置/开启态、卡密核销入口。 |
| 选手 Tab | `pages/players/index` | API-2 player | 按 Claude 方案 B 实现：保留底部“选手”Tab，但只复用 player 看板数据，不做完整选手详情。 |
| 人气看板 | `pages/popularity/index` | API-2 | player/team/spy Tab；前端不排序，直接渲染后端返回顺序；spy 未开启时显示灰置说明。 |
| 卡密核销 | `pages/redeem/index` | API-3 | 手动输入、用户点击后粘贴、提交 loading、防重复提交、错误五态文案。 |
| 核销成功 | `pages/redeem-success/index` | API-3 成功 data | 使用临时缓存展示核销成功结果；文案为“数字写真已自动存入我的收藏”；图片失败降级。 |
| 我的写真 | `pages/my-photos/index` | API-4 | 极简只读列表、空态、图片加载失败降级。 |

需要特别说明的是，工程中虽然存在 `pages/players/index`，但它不是完整选手详情页，而是底部 Tab 的 P0 兼容页。该页面明确提示完整选手详情留 C15，不展示个人动态、关注、距离、相关视频或周边入口，从而避免重新引入无后端支撑的模块。[1]

## 四、Claude 裁定执行情况

| 裁定/红线 | 执行结果 |
|---|---|
| 首页彻底删除四入口，不灰置 | 首页仅保留直播状态、人气摘要、真相识破状态和卡密核销入口。 |
| 底部“选手”Tab 采用方案 B | 已新增 `pages/players/index`，复用 API-2 player 数据，不做选手详情。 |
| C11 为 5 个 P0 页面 | 已实现 5 个 P0 页面；选手 Tab 为导航兼容页，不扩展动态详情。 |
| 核销成功页新文案 | 已使用“数字写真已自动存入我的收藏”。 |
| 剪贴板合规 | `tt.getClipboardData` 仅存在于 `pasteToken()` 点击事件中。 |
| 核销不传 userId | `redeemToken(token)` 只提交 `{ token }`，身份由 `request.js` 注入 Bearer token。 |
| 图片不硬编码风险示例图 | 写真展示使用接口 `previewUrl`；失败时使用 `photo_clean_stage.svg`。 |
| 人气看板不按 value 排序 | 静态检查确认页面代码未调用 `sort()`；代码注释明确禁止按 value 排序。 |
| 无敏感词 | 静态检查未发现“打榜/排名/榜首/冠军/应援/PK打赏/会员有效期+7天/送礼”等红线词。 |
| “LOVAL LEVEL”暂按图实现 | 首页保留 `LOVAL LEVEL`，并在本报告遗留风险中记录待 Vincent/彬少确认。 |

## 五、静态验证结果

由于抖音原生小程序没有本仓库内可直接运行的 JUnit 式测试环境，本次按 Claude 要求提供结构验收与静态验证输出。完整输出已保存到 `reports/C11_static_validation_output_v1.txt`。[2]

验证覆盖如下：

| 验证项 | 结果 |
|---|---|
| 文件树检查 | 通过，工程包含 app、config、utils、component、pages、assets。 |
| JSON 解析 | 通过，所有 `.json` 文件均可解析。 |
| JavaScript 语法检查 | 通过，所有 `.js` 文件均通过 `node --check`。 |
| 必需页面文件 | 通过，home、players、popularity、redeem、redeem-success、my-photos 均包含 `js/json/ttml/ttss`。 |
| 红线关键词检查 | 通过，未发现禁止词。 |
| value 排序检查 | 通过，未发现 `sort()` 或 value 运算排序。 |
| 剪贴板检查 | 通过，仅 `pages/redeem/index.js` 的用户点击粘贴事件调用 `tt.getClipboardData`。 |
| 占位图检查 | 通过，`assets/placeholders/photo_clean_stage.svg` 存在。 |

```text
OK JSON frontend/douyin-miniprogram/app.json
OK JS frontend/douyin-miniprogram/pages/home/index.js
OK no prohibited redline keywords
OK no sort or value arithmetic in frontend board rendering
OK placeholder exists
```

## 六、待人工验收项

抖音小程序前端最终仍需要在抖音开发者工具或真机中进行视觉和接口联调。当前代码已具备进入开发者工具验收的条件，但以下截图/录屏需要在实际运行环境补齐：

| 验收项 | 期望结果 |
|---|---|
| 首页截图 | 无四入口；无强营销词；直播状态、人气对象和团队人气展示正确。 |
| 人气页截图 | 即使 3 号人气最高，也仍按后端序号顺序展示，不显示名次。 |
| 核销错误五态 | 40001~40005 均显示固定文案，locked 显示倒计时。 |
| 核销成功截图 | 展示“数字写真已自动存入我的收藏”，无会员 +7 天。 |
| 我的写真截图 | 空态、有收藏、图片加载失败降级均可见。 |

## 七、遗留风险与后续任务

| 风险/事项 | 当前处理 | 后续归属 |
|---|---|---|
| 首页 `LOVAL LEVEL` 拼写 | 按彬少图实现，不擅自改设计 | John 询问 Vincent/彬少，上线前确认是否为品牌造词或 `LOYAL` 笔误。 |
| 写真素材合规 | C11 不硬编码风险图，使用接口图与清新占位降级 | C17 写真上传管理后台负责正式替换。 |
| 完整选手详情 | 本期不做，只做选手 Tab 的 player 看板别名 | C15 小程序其余页。 |
| 会员有效期 | 本期不展示 | C16 会员有效期。 |
| 真相识破提交 | 首页仅灰置/提示，不提交 | C13 卧底识破。 |
| H5 中间页与全链路联调 | 本期不做 | C12。 |

## 八、结论

C11 抖音小程序前端 P0 已完成代码实现和静态验证。工程严格限制在前端 `frontend/douyin-miniprogram`，未修改任何后端代码；已实现首页、人气看板、卡密核销、核销成功、极简我的写真，并按 Claude 裁定处理底部“选手”Tab。静态验证结果显示：JSON 解析通过、JS 语法通过、必要页面文件齐全、红线关键词未出现、未按 value 排序、剪贴板只在用户点击后读取、清新占位图存在。

## References

[1]: /home/ubuntu/upload/Claude确认_C11方案v2.md "Claude 确认 — C11 小程序前端方案 v2"
[2]: reports/C11_static_validation_output_v1.txt "C11 静态验证输出 v1"
[3]: C11_Douyin_MiniProgram_Implementation_Plan_v2.md "C11 抖音小程序前端技术实施方案 v2"
[4]: docs/API契约/当前页面级API契约定稿_C9依据_V1.0.md "当前页面级 API 契约定稿_C9依据_V1.0"
