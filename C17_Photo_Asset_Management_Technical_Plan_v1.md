# C17「写真上传管理」编程方案 v1（提交 Claude 审批版）

> 作者：Manus AI  
> 日期：2026-06-19  
> 当前状态：仅编程方案，**未编码**  
> 前置状态：C15 已由 Claude 审查通过；用户端小程序结构已完整；C17 进入开卡设计阶段。[1]

## 〇、方案结论

C17 建议定义为**后台写真资产管理卡**，目标是让 John 或运营人员不再依赖开发者手写数据库，就能在管理后台完成选手写真资产的上传、查看、替换、下架和设封面。它应服务 C11/C15/C16 已完成的用户端展示链路，但不改用户端核心契约：小程序仍通过 `photo_assets.preview_url` 展示图片，核销成功页、我的写真页、选手列表和选手详情页继续消费现有查询结果。

> C17 的蓝军原则是：**先让 John 能安全自助换图，再考虑复杂素材工作流；先做受控后台管理，不做公开上传、不做 AI 生成、不做裁剪美化、不做审核流。**

| 结论项 | Manus 建议 | 原因 |
|---|---|---|
| C17 主目标 | 管理后台新增写真资产管理能力 | C11 已明确当前 `photo_assets` 只能查询，没有写入接口和后台管理页。[2] |
| 存储方案 | P0 采用本地文件存储 + 后端静态资源映射 | 当前 `application.yml` 没有对象存储、上传目录或 URL 前缀配置。[3] |
| 数据结构 | 扩展 `photo_assets`，不新增复杂版本表 | P0 只需上传、替换、下架、封面排序。 |
| 后台 API | 新增 `/api/admin/photos/**` | 复用现有 `X-Admin-Token` 管理鉴权模型。[4] |
| 审计要求 | 所有写操作必须带 `operatorId` 并写 `operations_log` | C19 基础数据写操作已形成此模式。[5] |
| 用户端改动 | 原则上不改用户端接口，只调整后台维护的 URL 和状态 | C11/C15/C16 已通过 `preview_url` 消费图片。 |

## 一、背景与现状审计

C15 通过后，小程序用户端页面已经形成闭环：首页、人气看板、选手列表、选手详情、核销、核销成功、我的首页、我的写真和真相识破均已完成。Claude 的 C15 审查明确指出用户端结构已从“彩排极简版”升级为“正式内测可用的完整结构”。[1] 因此，C17 不应再重复开发用户端页面，而应补齐后台素材管理能力。

C17 的必要性来自 C11 裁定。C11 文档明确说明，当前系统没有任何上传/管理写真功能，`photo_assets` 当前只能被查询，没有写入接口、没有后台管理页；如果没有 C17，John 无法自行替换图片，只能让开发人员手写数据库。[2]

| 现有对象 | 当前能力 | C17 判断 |
|---|---|---|
| `photo_assets` | 仅有 `asset_id/player_id/preview_url/download_url/created_at` | 字段不足以支持状态、封面、排序、文件元数据 |
| `tokens.photo_asset_id` | 卡密核销时绑定写真资产 | C17 不应破坏已有 token 绑定 |
| `user_photo_collection.asset_id` | 用户核销后收藏写真资产 | C17 应避免物理删除导致历史收藏断链 |
| `/api/me/photos` | 查询用户已收藏写真，返回 `previewUrl` | 只要 `photo_assets.preview_url` 可维护，用户端可自然生效 |
| C15 Player API | 选手列表/详情展示写真预览 | 需要让查询优先取 active/cover 图片，但不改业务含义 |
| 管理后台 | 当前只有场控、基础数据等页面 | 需要新增“写真管理”Tab 或面板 |

## 二、C17 建议纳入范围

C17 的最小可交付范围应围绕“John 能自己换图”设计。功能不宜过重，但必须覆盖上传、替换、下架、封面管理和审计，否则无法满足上线前真实运营需求。

| 模块 | 是否纳入 C17 | 说明 |
|---|---|---|
| 写真列表 | 纳入 | 后台按选手筛选查看当前素材、状态、封面和 URL |
| 上传写真 | 纳入 | 运营选择选手、选择图片、填写排序/封面，上传生成 `photo_assets` |
| 替换写真文件 | 纳入 | 保持 `assetId` 不变，更新文件与 URL，避免 token/收藏断链 |
| 逻辑下架/恢复 | 纳入 | 合规风险图要能快速隐藏，但不物理删除历史记录 |
| 设为封面 | 纳入 | C15 选手列表/详情需要稳定首图展示 |
| 复制图片 URL | 纳入 | 便于人工排查和转给彬少/Claude 检查 |
| 操作日志 | 纳入 | 记录上传、替换、下架、恢复、设封面 |
| 类型/大小校验 | 纳入 | 仅允许 jpg/png/webp，禁止 SVG/脚本伪装，限制大小 |

## 三、C17 明确不做范围

C17 的最大风险是被理解成“素材生产系统”。蓝军判断这会立刻引入 AI 生成、裁剪、水印、审核、CDN、对象存储和商业权限等复杂问题，超过当前上线前收尾卡的目标。C17 应只做受控后台管理，不做内容生产和复杂工作流。

| 不纳入 C17 | 原因 | 后续归属 |
|---|---|---|
| AI 生成写真 | 内容生产与提示词审核复杂 | 独立内容生产流程 |
| 图片裁剪、美化、水印 | 前端交互复杂，容易拖慢上线 | 后续体验优化 |
| 多级审核流 | 当前团队规模小，先靠 admin 鉴权 + 审计 | 后续合规流程卡 |
| 对象存储直传 | 当前项目无对象存储配置 | 后续存储升级卡 |
| CDN 缓存刷新 | 依赖部署与域名策略 | 部署运维阶段 |
| 付费下载权限 | 当前用户端只展示预览图 | 后续会员/订单体系 |
| 删除用户收藏记录 | 会破坏已获得权益 | 禁止 |
| 改 C15/C16 用户端展示文案 | 与 C17 目标无关 | 不做 |

## 四、数据结构方案

### 4.1 现有表问题

当前 `photo_assets` 只有五个字段，足以让小程序展示图片，但不足以让后台安全管理素材。它缺少状态、封面、排序、文件名、文件大小、文件类型、更新时间等管理字段。[6]

| 现有字段 | 问题 |
|---|---|
| `asset_id` | 可继续作为稳定主键，建议由后端生成 |
| `player_id` | 可继续绑定选手 |
| `preview_url` | 可继续供用户端展示 |
| `download_url` | 当前可选，可暂时与 preview_url 相同或为空 |
| `created_at` | 缺少 `updated_at`，不利于排查替换时间 |

### 4.2 建议扩展字段

建议在 `photo_assets` 上直接扩展字段，而不是 P0 新增 `photo_asset_versions`。理由是 C17 当前只需要管理当前可用素材；复杂版本回滚可以后续再做。

```sql
ALTER TABLE photo_assets
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
  ADD COLUMN is_cover TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否选手封面图',
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '同一选手下展示排序',
  ADD COLUMN file_name VARCHAR(255) NULL COMMENT '原始文件名',
  ADD COLUMN content_type VARCHAR(100) NULL COMMENT 'MIME 类型',
  ADD COLUMN file_size BIGINT NULL COMMENT '文件大小',
  ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  ADD KEY idx_photo_player_status (player_id, status, is_cover, sort_order);
```

H2 测试 schema 需要同步字段；已有数据通过默认值保持兼容。若 Claude 担心直接修改主表，可改为新增 `photo_asset_metadata`，但 Manus 建议 P0 直接扩字段，减少 join 和复杂度。

## 五、存储方案

### 5.1 P0 推荐：本地文件存储 + 静态资源映射

当前后端配置没有上传目录、对象存储、文件大小限制或资源 URL 前缀配置。[3] 因此 C17 应显式新增存储配置。P0 推荐本地文件存储，并通过后端静态资源映射暴露图片 URL。

| 配置项 | 建议默认值 | 说明 |
|---|---|---|
| `redface.photo-storage.upload-dir` | `${user.home}/redface-uploads/photos` | 上传文件落盘目录 |
| `redface.photo-storage.public-path` | `/uploads/photos/` | 后端静态访问路径 |
| `redface.photo-storage.public-base-url` | 空，默认根据请求拼相对 URL | 生产可配置 CDN/域名前缀 |
| `redface.photo-storage.max-size-bytes` | `5242880` | 单图 5MB 上限 |
| `spring.servlet.multipart.max-file-size` | `5MB` | Spring multipart 限制 |
| `spring.servlet.multipart.max-request-size` | `6MB` | 请求整体限制 |

后端 `WebConfig` 需要增加 `/uploads/photos/**` 到本地目录的资源映射。上传后文件保存为 `{assetId}.{ext}`，`preview_url` 可保存为 `/uploads/photos/{assetId}.{ext}` 或 `${publicBaseUrl}/uploads/photos/{assetId}.{ext}`。

### 5.2 蓝军风险：本地存储不是长期最优

本地文件存储的弱点是多实例部署、容器重启、备份和迁移。C17 方案必须明确：P0 本地存储是为了上线前快速让 John 自助换图；未来如果项目进入稳定运营，应抽象 `PhotoStorageService`，把本地实现替换为对象存储实现。编码时可以先定义接口，P0 只实现 `LocalPhotoStorageService`，为后续迁移留口。

## 六、后端 API 方案

C17 后台接口应全部挂在 `/api/admin/photos` 下，复用现有 `AdminAuthInterceptor` 对 `/api/admin/**` 的 `X-Admin-Token` 鉴权模型。[4] 写操作必须包含 `operatorId`，并写入 `operations_log`，延续 C19 基础数据管理的审计规范。[5]

| 方法 | 路径 | 用途 | 说明 |
|---|---|---|---|
| GET | `/api/admin/photos?playerId=&status=` | 写真列表 | 支持按选手和状态筛选 |
| POST | `/api/admin/photos/upload` | 上传写真 | `multipart/form-data`，含 `file/playerId/operatorId/isCover/sortOrder` |
| PUT | `/api/admin/photos/{assetId}` | 更新元数据 | 更新选手、状态、封面、排序、downloadUrl |
| POST | `/api/admin/photos/{assetId}/replace` | 替换文件 | 保持 assetId 不变，更新文件和 URL |
| POST | `/api/admin/photos/{assetId}/cover` | 设为封面 | 同选手其他图片自动取消封面 |
| PUT | `/api/admin/photos/{assetId}/status` | 下架/恢复 | `active/inactive`，不物理删除 |

### 6.1 上传请求

```http
POST /api/admin/photos/upload
Content-Type: multipart/form-data
X-Admin-Token: ********

operatorId=john
playerId=3
isCover=true
sortOrder=0
file=@stage_photo.webp
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "assetId": "photo_3_20260619_abcdef",
    "playerId": 3,
    "playerName": "林夏",
    "previewUrl": "/uploads/photos/photo_3_20260619_abcdef.webp",
    "status": "active",
    "isCover": true,
    "sortOrder": 0,
    "fileName": "stage_photo.webp",
    "contentType": "image/webp",
    "fileSize": 384120
  }
}
```

### 6.2 校验规则

| 校验项 | 规则 |
|---|---|
| `operatorId` | 必填，空则拒绝 |
| `playerId` | 必填且必须存在 |
| 文件类型 | 只允许 `image/jpeg`、`image/png`、`image/webp` |
| 文件扩展名 | 只允许 `.jpg/.jpeg/.png/.webp` |
| 文件大小 | 默认不超过 5MB |
| SVG | 禁止，避免脚本与审核风险 |
| 文件名 | 不信任原始文件名，只记录展示；落盘名由后端生成 |
| 封面 | 同一选手最多一个 active 封面 |

## 七、后端实现文件建议

| 文件 | 说明 |
|---|---|
| `PhotoAdminController.java` | 后台写真管理 API |
| `PhotoAdminService.java` | 业务校验、事务、审计日志 |
| `PhotoAssetMapper.java` | `photo_assets` 查询、插入、更新、状态、封面操作 |
| `PhotoStorageService.java` | 存储接口 |
| `LocalPhotoStorageService.java` | 本地文件存储实现 |
| `PhotoStorageProperties.java` | `redface.photo-storage.*` 配置绑定 |
| `AdminPhotoRequests.java` | 更新状态/封面/元数据请求 DTO |
| `AdminPhotoViews.java` | 后台列表/详情响应 DTO |
| `WebConfig.java` | 增加静态资源映射 |
| `PhotoAdminControllerC17Test.java` | API、鉴权、校验和审计测试 |

C17 还应调整 C15 的 `PlayerQueryMapper` 与 C9 的写真查询：优先选择 `status='active'` 的图片；列表首图优先 `is_cover=1`，其次 `sort_order`，再按 `created_at`。这属于 C17 对写真资产状态的必要联动，不属于用户端业务重构。

## 八、管理后台前端方案

管理后台当前只有场控监控、场控操作、基础数据等 Tab，尚无写真资产管理入口。C17 建议新增 `写真管理` Tab，沿用现有 `operatorId`、`adminToken`、`runAction()`、`withOperator()` 交互模式。[7]

| 区块 | 功能 |
|---|---|
| 筛选区 | 选手下拉、状态筛选、刷新按钮 |
| 上传区 | 选手下拉、文件选择、是否设封面、排序、上传按钮 |
| 列表区 | 缩略图、assetId、选手、状态、封面、排序、URL、创建/更新时间 |
| 操作区 | 设封面、下架、恢复、复制 URL、替换文件 |
| 风险提示 | “仅上传清新/才艺/舞台风图片，禁止性感擦边素材” |

前端 API 建议新增 `frontend/control-admin/src/api/photos.ts`，而不是塞进 `basicData.ts`。原因是写真管理涉及 multipart 上传、状态变更和图片 URL，不属于基础数据新增。

`http.ts` 目前只提供 JSON 请求方法，并固定设置 `Content-Type: application/json`。[8] C17 需要新增 `multipartPost<T>(url, formData)`，该方法不能手动设置 `Content-Type`，应让浏览器自动生成 boundary，同时继续对 `/api/admin/**` 注入 `X-Admin-Token`。

## 九、测试与验收方案

C17 必须重点测试“上传成功”和“风险拒绝”。由于上传涉及文件系统，测试可使用 JUnit `@TempDir` 指向临时上传目录，不污染真实环境。

| 测试类别 | 建议用例 |
|---|---|
| 后端上传 | jpg/png/webp 成功写文件、写 `photo_assets`、返回 URL |
| 类型拒绝 | SVG、txt、空文件、超大文件被拒绝 |
| 选手校验 | 不存在 playerId 被拒绝 |
| 封面规则 | 设置新封面后同选手其他封面取消 |
| 逻辑下架 | 下架后用户端查询不再选中该图片 |
| 替换文件 | assetId 不变，文件 URL 或更新时间更新 |
| 审计日志 | 上传/替换/下架/恢复/设封面均写 `operations_log` |
| 后台鉴权 | 无 `X-Admin-Token` 生产配置下 401 |
| 前端静态 | `pnpm build` 通过；multipart 上传不设置 JSON Content-Type |
| 回归 | `mvn test` 全量通过，C15/C16 不退化 |

## 十、上线与运维注意事项

C17 不是纯代码功能，它涉及文件持久化。上线前必须确认上传目录在服务器上具备写权限，并且不随部署清空。若使用 Docker 或云服务器，应将上传目录挂载到持久化磁盘，并纳入备份策略。

| 运维项 | 要求 |
|---|---|
| 上传目录 | 生产必须使用持久化目录，不放在临时构建目录 |
| 文件备份 | 上传目录需要定期备份 |
| 访问 URL | 小程序端必须能访问 `preview_url`，必要时配置公网域名 |
| 图片大小 | 建议运营先压缩到 1MB 左右，后端上限 5MB |
| 内容标准 | 只允许清新、才艺、舞台风，禁止性感擦边 |
| 应急下架 | 后台一键下架应优先于物理删除 |

## 十一、蓝军风险清单

| 风险 | 影响 | 控制建议 |
|---|---|---|
| 本地存储在多实例环境失效 | 某实例上传后另一实例看不到 | P0 单实例可用；后续抽象存储接口迁移对象存储 |
| 物理删除导致历史收藏断链 | 用户已获得写真无法展示 | C17 采用逻辑下架，不删除收藏记录 |
| 上传违规图片 | 审核风险、项目下架风险 | 后台提示 + 类型限制 + John/彬少内容标准；后续再做审核流 |
| SVG/脚本伪装 | 安全风险 | 禁 SVG，仅允许 jpg/png/webp，服务端按 MIME 和扩展名双校验 |
| multipart 破坏现有 JSON http 封装 | 后台上传失败 | 新增专用 `multipartPost`，不改 JSON 请求 |
| C17 改动用户端契约 | 影响 C11/C15/C16 已通过功能 | 用户端接口原则不改，只让查询过滤 active 图片 |
| 封面逻辑不唯一 | 列表首图不稳定 | 同一选手事务内唯一封面 |

## 十二、提交 Claude 裁定的问题

| 编号 | 问题 | Manus 建议 |
|---|---|---|
| C17-Q1 | 是否批准 P0 采用本地文件存储 + 后端静态映射，而不是立即接对象存储？ | 批准，但要求抽象 `PhotoStorageService`，未来可迁移 |
| C17-Q2 | 是否批准直接扩展 `photo_assets`，暂不新增版本表？ | 批准，P0 简化，避免过度设计 |
| C17-Q3 | 是否采用逻辑下架，不物理删除文件和收藏记录？ | 必须批准，避免破坏用户历史权益 |
| C17-Q4 | 是否允许后台只做上传/替换/下架/恢复/设封面，不做裁剪、水印、AI 生成和审核流？ | 批准，避免范围膨胀 |
| C17-Q5 | 是否强制只允许 jpg/png/webp，禁止 SVG，默认 5MB 上限？ | 必须批准，降低安全和审核风险 |
| C17-Q6 | 用户端是否不新增接口，只让已有查询消费 active/cover 图片？ | 批准，保护 C11/C15/C16 契约 |
| C17-Q7 | 是否要求所有写操作带 `operatorId` 并写 `operations_log`？ | 必须批准，延续后台审计规范 |
| C17-Q8 | 是否将 C17 后台 UI 放入 `control-admin` 的新 Tab，而非另建后台项目？ | 批准，减少部署复杂度 |

## 十三、预期交付物

如果 Claude 批准本方案，C17 编码阶段建议交付以下内容。

| 交付物 | 说明 |
|---|---|
| 后端写真管理 API | `/api/admin/photos/**` |
| 本地存储实现 | `PhotoStorageService` + `LocalPhotoStorageService` |
| schema 扩展 | `photo_assets` 新增状态、封面、排序和文件元数据字段 |
| 管理后台 UI | `写真管理` Tab、上传、替换、下架、设封面 |
| 用户端查询联动 | 只过滤 active/cover，不改用户端契约 |
| 测试 | 后端上传/校验/审计测试，前端构建，回归测试 |
| 文档 | `C17_Implementation_Report_v1.md` 与 `C17_Integration_Checklist_v1.md` |

## 十四、参考来源

[1]: /home/ubuntu/upload/Claude审查_C15通过.md "Claude 审查 — 任务卡 C15 通过（用户附件，2026-06-18）"  
[2]: C11_Douyin_MiniProgram_Approved_Delta_v1.md "C11 抖音小程序前端裁定更新说明 v1：C17 写真资产管理提出原因"  
[3]: backend/redface-backend/src/main/resources/application.yml "后端默认配置：当前无上传目录、对象存储或静态资源前缀配置"  
[4]: backend/redface-backend/src/main/java/com/redface/web/AdminAuthInterceptor.java "后台 Admin 鉴权模型：/api/admin/** + X-Admin-Token"  
[5]: backend/redface-backend/src/main/java/com/redface/service/BasicDataService.java "后台写操作 operatorId 校验与 operations_log 审计模式"  
[6]: db/db_schema.sql "photo_assets、tokens、user_photo_collection 表结构"  
[7]: frontend/control-admin/src/App.vue "管理后台 operatorId/adminToken/runAction/withOperator 集成模式"  
[8]: frontend/control-admin/src/api/http.ts "管理后台 JSON 请求封装与 Admin Token 注入逻辑"  
