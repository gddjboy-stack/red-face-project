# C17「写真上传管理」实施报告 v1

> 作者：Manus AI  
> 日期：2026-06-19  
> 状态：已编码，待 Claude 复审  
> 前置裁定：Claude 已批准 C17 方案并要求落实文件头真实图片校验、后端生成落盘文件名、上传目录隔离、逻辑下架和审计日志。[1]

## 一、实施结论

C17 已按 John 授权与 Claude 裁定完成正式编码。本次实现把写真管理从“只能开发人员手写数据库”升级为“运营后台可自助上传、替换、下架、恢复、设封面”的受控后台能力。实现范围严格限定在后台写真资产管理、后端本地存储、`photo_assets` 扩展、后台 UI 与必要的用户端查询联动；没有改 C2~C16 核心业务，没有新增用户端接口，也没有做裁剪、水印、AI 生成或审核流。

| 模块 | 实施结果 | 边界说明 |
|---|---|---|
| 后台 API | 新增 `/api/admin/photos/**` | 复用 `X-Admin-Token` 管理鉴权，不走用户 Bearer 登录态 |
| 文件存储 | 新增 `PhotoStorageService` 与 `LocalPhotoStorageService` | P0 本地持久化目录 + 静态资源映射，后续可迁移对象存储 |
| 安全校验 | 落实扩展名、声明 MIME、真实文件头三重校验 | 只允许 jpg/png/webp，拒绝 SVG、伪装 jpg、空文件、超大文件 |
| 数据结构 | 扩展 `photo_assets` | 增加 status、is_cover、sort_order、file_name、content_type、file_size、updated_at |
| 后台 UI | `control-admin` 新增“写真管理”Tab | 支持上传、替换、下架/恢复、设封面、复制 URL |
| 审计日志 | 上传、替换、状态、设封面均写 `operations_log` | 所有写操作必须带 `operatorId` |
| 用户端联动 | C15 Player 查询优先取 active/cover 图片 | 不新增用户端接口，不改变 C11/C15/C16 响应契约 |
| 删除策略 | 只做逻辑下架 | 不物理删除文件，不删除 `user_photo_collection` |

## 二、后端实现

C17 后端新增了受控后台接口和存储抽象。`PhotoAdminController` 保持薄控制器风格，只负责 HTTP 参数适配；`PhotoAdminService` 负责业务校验、事务、审计日志和封面唯一；`PhotoAssetMapper` 负责写真资产读写；`LocalPhotoStorageService` 负责文件安全校验与落盘。后台接口全部挂在 `/api/admin/photos/**`，因此天然复用既有 `/api/admin/**` 管理口令拦截器。[2]

| 文件 | 说明 |
|---|---|
| `PhotoAdminController.java` | 写真管理 HTTP API |
| `PhotoAdminService.java` | 上传、替换、状态、封面与审计业务逻辑 |
| `PhotoAssetMapper.java` | `photo_assets` 查询、插入、更新与封面唯一操作 |
| `PhotoStorageService.java` | 文件存储抽象接口 |
| `LocalPhotoStorageService.java` | 本地文件存储与安全校验实现 |
| `StoredPhotoFile.java` | 存储结果对象 |
| `PhotoStorageProperties.java` | `redface.photo-storage.*` 配置绑定 |
| `AdminPhotoView.java` 等 DTO | 后台写真视图与请求体 |
| `WebConfig.java` | 增加 `/uploads/photos/**` 静态资源映射 |

本地存储落盘文件名只使用后端生成的 `assetId` 加真实格式扩展名，原始文件名仅记录在 `file_name` 字段用于后台展示，绝不参与路径拼接。上传目录由 `redface.photo-storage.upload-dir` 配置控制，默认在 `${user.home}/redface-uploads/photos`，并通过 `redface.photo-storage.public-path` 暴露静态访问路径。[3]

## 三、安全要求落实情况

Claude 在裁定中特别强调，文件上传是系统唯一允许外部文件进入服务器的入口，必须落实真实内容检测、防路径穿越、目录隔离和恶意文件拒绝。本次编码逐项落实如下。

| 安全要求 | 实现位置 | 验证方式 |
|---|---|---|
| 扩展名白名单 | `LocalPhotoStorageService.ensureAllowedExtension` | C17 测试覆盖 SVG 拒绝 |
| 声明 MIME 白名单 | `LocalPhotoStorageService.ensureAllowedDeclaredMime` | C17 测试覆盖 SVG 拒绝 |
| 真实文件头校验 | `LocalPhotoStorageService.detectImage` | C17 测试覆盖 txt 改名 `.jpg` 被拒 |
| 后端生成落盘文件名 | `LocalPhotoStorageService.store` | 文件名由 `assetId` 派生，原始名只入库展示 |
| 上传路径归一化 | `uploadDir.resolve(...).normalize()` 并检查 `startsWith(uploadDir)` | 静态边界扫描通过 |
| 上传目录隔离 | `WebConfig.addResourceHandlers` 映射专用上传目录 | 配置项独立于应用代码目录 |
| 逻辑下架 | `PhotoAssetMapper.updateStatus` | C17 测试确认收藏记录不删除 |
| 操作审计 | `PhotoAdminService.writeLog` | C17 测试确认 `operations_log` 写入 |

## 四、数据库与配置变更

`photo_assets` 表已按 C17 方案扩展管理字段，并同步更新 H2 测试 schema。新增字段均有默认值，已有数据可按 `active`、非封面、排序 0 的方式兼容迁移。生产环境需要运维执行对应 DDL，且需要配置持久化上传目录。

| 变更文件 | 说明 |
|---|---|
| `db/db_schema.sql` | 主库 `photo_assets` 增加状态、封面、排序、文件元数据和索引 |
| `schema-h2.sql` | 测试库同步 schema |
| `application.yml` | 增加 multipart 限制与 `redface.photo-storage.*` 配置 |
| `WebConfig.java` | 增加静态资源映射 |

新增配置如下：

| 配置项 | 默认值 | 用途 |
|---|---|---|
| `PHOTO_UPLOAD_DIR` | `${user.home}/redface-uploads/photos` | 上传文件持久化目录 |
| `PHOTO_PUBLIC_PATH` | `/uploads/photos/` | 静态访问路径 |
| `PHOTO_PUBLIC_BASE_URL` | 空 | 可选公网域名/CDN 前缀 |
| `PHOTO_MAX_SIZE_BYTES` | `5242880` | 服务层最大文件大小 |
| `PHOTO_MAX_FILE_SIZE` | `5MB` | Spring multipart 单文件限制 |
| `PHOTO_MAX_REQUEST_SIZE` | `6MB` | Spring multipart 请求限制 |

## 五、管理后台实现

`control-admin` 新增“写真管理”Tab，沿用现有 `operatorId`、`adminToken`、`runAction()` 和统一错误提示模式。由于上传使用 multipart，`http.ts` 已调整为当请求体为 `FormData` 时不手写 `Content-Type`，让浏览器自动生成 boundary；普通 JSON 请求仍保持原逻辑。

| 前端文件 | 说明 |
|---|---|
| `src/api/photos.ts` | C17 写真管理 API 封装 |
| `src/api/http.ts` | 增加 `multipartPost`，FormData 不设置 JSON Content-Type |
| `src/App.vue` | 新增“写真管理”Tab、上传、替换、下架/恢复、设封面、复制 URL |
| `src/styles.css` | 新增缩略图和替换文件输入样式 |

后台 UI 明确展示“仅上传清新/才艺/舞台风图片；禁止性感擦边素材；只接受 jpg/png/webp，禁止 SVG”的运营提示，降低素材合规误用风险。

## 六、用户端联动

C17 没有新增任何用户端接口，也没有改变 C11/C15/C16 的响应结构。唯一联动是 C15 `PlayerQueryMapper` 查询选手列表首图和详情写真时，优先消费 `status='active'` 的图片，并按 `is_cover DESC, sort_order ASC, created_at DESC` 选择稳定展示顺序。这样 C17 后台设封面和下架能自然影响选手列表/详情展示，但不会破坏核销、会员或我的写真契约。

> 蓝军说明：`user_photo_collection` 不被删除，C17 下架不会回滚用户历史收藏权益。当前实现主要让新查询不再把 inactive 图作为选手展示首图或详情图；已收藏写真权益仍保留在数据库中。

## 七、测试与验证

本次补充 `PhotoAdminControllerC17Test`，覆盖上传成功、恶意文件拒绝、封面唯一、逻辑下架、替换文件和后台鉴权。回归测试和前端构建均已通过。

| 验证项 | 结果 |
|---|---|
| C17 专项测试 | 4 tests，0 failures，0 errors |
| 恶意 SVG 拒绝 | 通过 |
| txt 改名 `.jpg` 文件头拒绝 | 通过 |
| 空文件拒绝 | 通过 |
| 不存在 playerId 拒绝 | 通过 |
| 封面唯一 | 通过 |
| 逻辑下架不删 `user_photo_collection` | 通过 |
| 未带 `X-Admin-Token` 拒绝 | 通过 |
| 替换文件 assetId 不变 | 通过 |
| 后端全量回归 | 85 tests，0 failures，0 errors |
| 管理后台前端构建 | `pnpm build` 通过 |
| 静态边界扫描 | 未发现物理删除收藏、原始文件名落盘、SVG 接受等风险模式 |

## 八、未处理与上线注意事项

C17 已完成 P0，但文件上传功能天然涉及运维。上线前必须确认上传目录是持久化目录，不会随部署、容器重建或服务器重启丢失；如未来多实例部署，必须迁移到对象存储或共享存储。C17 目前没有做图片裁剪、水印、审核流和 CDN 刷新，这是范围控制结果，不是遗漏。

| 事项 | 当前状态 | 建议 |
|---|---|---|
| 上传目录持久化 | 需要运维配置 | 生产必须设置 `PHOTO_UPLOAD_DIR` 到持久化磁盘 |
| 备份 | 未由代码实现 | 上传目录应纳入备份策略 |
| 多实例 | P0 不支持跨实例同步 | 上线初期单实例可用；多实例前迁移对象存储 |
| 内容审核 | 未做流程化审核 | 由 John/彬少控制素材，后续再做审核流 |
| CDN/缓存刷新 | 未做 | 如配置 CDN，需后续补缓存策略 |

## 九、提交 Claude 复审重点

建议 Claude 重点复审以下方面：第一，文件上传安全是否满足“扩展名 + MIME + magic number”三重校验；第二，落盘文件名是否完全由后端生成且不使用原始文件名；第三，`photo_assets` 扩展是否足够且没有过度设计版本表；第四，逻辑下架是否不删除用户收藏；第五，`PlayerQueryMapper` active/cover 联动是否没有改变用户端契约；第六，前端 multipart 是否未手写 JSON `Content-Type`。

## 十、参考来源

[1]: C17方案裁定_批准.md（用户提供附件）  
[2]: backend/redface-backend/src/main/java/com/redface/web/AdminAuthInterceptor.java  
[3]: backend/redface-backend/src/main/resources/application.yml  
[4]: backend/redface-backend/src/main/java/com/redface/service/LocalPhotoStorageService.java  
[5]: backend/redface-backend/src/test/java/com/redface/PhotoAdminControllerC17Test.java  
[6]: frontend/control-admin/src/api/http.ts  
[7]: frontend/control-admin/src/App.vue  
[8]: C17_Photo_Asset_Management_Technical_Plan_v1.md
