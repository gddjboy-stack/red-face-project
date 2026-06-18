# C15「小程序其余页」实施报告 v1

> 作者：Manus AI  
> 日期：2026-06-18  
> 状态：已编码，待 Claude 复审  
> 前置裁定：Claude 已批准 C15 技术方案并明确可编码，要求守住两条红线：选手详情不得泄露卧底身份；选手列表不得排名、不得使用敏感词。[1]

## 一、实施结论

C15 已按 John 授权与 Claude 裁定完成正式编码。本次实现将 C11 阶段的“选手 Tab 看板别名”升级为正式选手列表入口，新增用户端选手详情页和“我的”首页，并新增只读 `PlayerController` API。实现过程中未改 API-2 人气看板契约，未改 C13 卧底识破逻辑，未改 C16 会员有效期逻辑，也没有引入订单、退款、会员群、直播跳转、写真上传或后台权限。

| 模块 | 实施结果 | 边界说明 |
|---|---|---|
| 后端 Player API | 新增 `/api/players` 与 `/api/players/{playerId}` | 只读查询，不改 C2~C13/C16 业务逻辑 |
| 防剧透 | DTO、Mapper、Controller 均不返回卧底身份字段 | 测试断言响应不含相关字段 |
| 小程序选手页 | `pages/players` 升级为正式列表 | 按序号展示，不按人气排序 |
| 小程序详情页 | 新增 `pages/player-detail` | 只展示基础资料、人气和写真 |
| 小程序我的页 | 新增 `pages/me` 作为 Tab 首页 | 复用 `/api/me/photos` 的 `total + membership` |
| 我的写真页 | 保留 `pages/my-photos` 作为子页 | C11/C16 原能力不破坏 |
| 核销成功页跳转 | `goPhotos` 改为 `navigateTo` | 因 `my-photos` 已从 Tab 页退为子页 |
| `LOVAL LEVEL` | 未修改 | 等 John 向 Vincent/彬少确认 |

## 二、后端实现

后端新增 C15 只读 Player API，语义上与 API-2 人气看板分离。API-2 仍服务 `pages/popularity` 的看板展示；C15 新接口服务选手列表和详情页，避免把详情字段塞进看板契约。实现文件如下。

| 文件 | 说明 |
|---|---|
| `PlayerController.java` | 暴露 `GET /api/players` 与 `GET /api/players/{playerId}` |
| `PlayerQueryService.java` | 解析当前轮次、组装列表与详情响应 |
| `PlayerQueryMapper.java` | 查询 `players/player_round/teams/player_round_stats/photo_assets` |
| `PlayerListResponse.java`、`PlayerListItem.java` | 选手列表响应 DTO |
| `PlayerDetailResponse.java`、`PlayerPhotoItem.java` | 选手详情响应 DTO 与写真项 |
| `PlayerControllerC15Test.java` | C15 后端专项测试 |

`GET /api/players` 返回选手列表，包含 `playerId/number/name/teamName/popularityValue/photoPreviewUrl`。列表 SQL 按 `p.number ASC` 排序，前端也不再对人气值做排序，从而延续 C11/C13 的合规表达。`GET /api/players/{playerId}` 返回选手基本资料、人气值、写真预览和克制提示文案“增加人气值请在直播间进行。”；该接口没有返回卧底身份、卧底状态或任何可推断卧底身份的字段。

## 三、小程序实现

小程序新增 `pages/player-detail` 与 `pages/me`，并更新 `app.json`。底部 Tab 的“我的”已从 `pages/my-photos/index` 改为 `pages/me/index`，而 `pages/my-photos/index` 保留为“我的写真”子页。由于 `my-photos` 不再是 Tab 页，核销成功页进入我的写真的跳转方式也从 `tt.switchTab` 改为 `tt.navigateTo`。

| 页面 | 变更 |
|---|---|
| `pages/players` | 删除“彩排版说明”，改为正式选手列表；支持图片预览和详情跳转 |
| `pages/player-detail` | 新增详情页，仅展示基础资料、人气值、写真预览和直播间支持提示 |
| `pages/me` | 新增我的首页，展示会员状态、写真数量、核销入口 |
| `pages/my-photos` | 保持 C11/C16 既有列表能力，作为子页使用 |
| `pages/redeem-success` | 调整“查看我的写真”跳转方式 |
| `utils/api.js` | 新增 `getPlayers` 与 `getPlayerDetail` |

本次没有恢复动态、关注、距离、相关视频、购买周边等社交模板残留模块，也没有新增订单查询、退款、会员群、直播间跳转、写真上传或后台权限入口。

## 四、红线执行情况

Claude 裁定要求 C15 审查重点盯两条红线：防剧透与合规表达。本次实现专门设置了代码与测试双重约束。

| 红线 | 执行方式 | 验证结果 |
|---|---|---|
| 选手详情不得泄露卧底身份 | C15 DTO/Mapper/Controller 不包含卧底身份字段；详情 SQL 不查询相关字段 | `PlayerControllerC15Test` 断言响应不含相关字段 |
| 列表不排名、不按人气排序 | 后端按选手序号查询；前端只渲染后端顺序，不调用排序 | 静态扫描通过 |
| 无敏感词 | 小程序 pages/utils/app.json 扫描禁止词 | 静态扫描通过 |
| 不擅改品牌词 | `LOVAL LEVEL` 保持原样 | 等 John 确认 |

## 五、测试与验证

已完成 C15 后端专项测试、小程序静态检查、防剧透扫描和后端全量回归。全量回归在 C16 通过后的 78 个测试基础上新增 C15 的 3 个测试，当前合计 81 个测试全部通过。

| 验证项 | 结果 |
|---|---|
| `PlayerControllerC15Test` | 3 tests，全部通过 |
| 小程序 JSON 解析 | 全部通过 |
| 小程序 JS 语法检查 | 全部通过 |
| 小程序路由检查 | `pages/me/index` 与 `pages/player-detail/index` 已注册 |
| 小程序敏感词扫描 | 未发现禁止词 |
| C15 后端防剧透字段扫描 | 未发现卧底身份字段 token |
| `mvn test` 全量回归 | 81 tests，0 failures，0 errors |
| `git diff --check` | 通过 |

> 蓝军说明：以上验证覆盖代码、接口和静态边界。抖音开发者工具截图、真机视觉验收和真实环境接口联调仍需在后续人工验收阶段完成，不能被本报告误认为“真机已验证”。

## 六、未处理事项与后续建议

C15 主体已经完成，但仍有少量不阻塞事项需要 John 或后续任务处理。首先，`LOVAL LEVEL` 是否为品牌造词仍待 Vincent/彬少确认；若确认是笔误，可在 C15 收尾或后续 UI 清理时改为 `LOYAL LEVEL` 或中文副标题。其次，C15 没有做订单查询、退款、会员群、直播间跳转、写真上传和后台权限，这些仍应分别留给 C14、C17、C18 或 6/22 真实环境联调。

| 事项 | 当前处理 | 建议归属 |
|---|---|---|
| `LOVAL LEVEL` | 不擅改 | John 询问 Vincent/彬少 |
| 订单查询 | 不做 | 真实订单联调或后续订单卡 |
| 退款 | 不做 | C14 |
| 会员群 | 不做 | 运营确认后另开卡 |
| 直播间跳转 | 不做 | 6/22 真实环境验证后决定 |
| 写真上传/替换 | 不做 | C17 |
| 后台权限 | 不做 | C18 |

## 七、提交 Claude 复审重点

建议 Claude 重点审查：第一，`PlayerController` 是否确实只读且未污染 API-2；第二，`PlayerDetailResponse` 和查询 SQL 是否完全不泄露卧底身份；第三，`pages/players` 是否没有人气排序、名次表达或敏感词；第四，`pages/me` 是否只复用 `/api/me/photos` 而没有扩展会员中心；第五，`my-photos` 从 Tab 退为子页后，`redeem-success` 的跳转方式是否正确。

## 八、参考来源

[1]: 用户提供的《Claude裁定_C15小程序其余页方案.md》，Claude 对 C15 八个问题的正式裁定与编码边界。  
[2]: `C15_MiniProgram_Remaining_Pages_Technical_Plan_v1.md`，C15 技术方案 v1。  
[3]: `C11_Douyin_MiniProgram_Implementation_Report_v1.md`，C11 报告明确 `pages/players` 仅为兼容页，完整选手详情归 C15。  
[4]: `backend/redface-backend/src/test/java/com/redface/PlayerControllerC15Test.java`，C15 后端专项测试。  
[5]: `frontend/douyin-miniprogram/app.json`，C15 新增页面路由与 Tab 调整。
