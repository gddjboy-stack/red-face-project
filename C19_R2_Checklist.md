# C19-R2 逐项完成对照表

| 要求项 | 对应文件 + 行号/函数 | 证据/实现说明 |
|---|---|---|
| 1. 剪贴板 fail 回调改为中文 toast | `frontend/douyin-miniprogram/pages/redeem/index.js` : 31 | `fail: () => tt.showToast({ title: '无法读取剪贴板，请长按输入框手动粘贴', icon: 'none' })`。不阻塞后续手动输入与核销。 |
| 2. request.js fail 分支不直出英文 errMsg | `frontend/douyin-miniprogram/utils/request.js` : 49-51 | `console.error('[request fail]', err.errMsg || err)` 保留原始信息供排查；用户侧统一返回 `'网络异常，请检查连接后重试'`。 |
| 3. 识破页介绍文案更新（Claude R2 新增项） | `frontend/douyin-miniprogram/pages/suspicion/index.ttml` : 15 | 原文「每轮仅可提交一次判断，提交后请等待直播间揭晓。」→ 新文「每位选手仅可投一次，可分多次提交不同选手，提交后请等待直播间揭晓。」 |

**联调证据**：
由于沙箱无法运行抖音开发者工具，断网/拒权模拟的截图需在真机环境补齐。代码 diff 如下：

```diff
# redeem/index.js
-      fail: () => tt.showToast({ title: '读取剪贴板失败', icon: 'none' })
+      fail: () => tt.showToast({ title: '无法读取剪贴板，请长按输入框手动粘贴', icon: 'none' })

# request.js
-      fail(err) {
-        reject({ code: -1, message: err.errMsg || '网络异常，请稍后重试', data: null })
-      }
+      fail(err) {
+        console.error('[request fail]', err.errMsg || err)
+        reject({ code: -1, message: '网络异常，请检查连接后重试', data: null })
+      }

# suspicion/index.ttml
-    <view class="intro-text">根据直播线索，选择你认为最可疑的选手。每轮仅可提交一次判断，提交后请等待直播间揭晓。</view>
+    <view class="intro-text">每位选手仅可投一次，可分多次提交不同选手，提交后请等待直播间揭晓。</view>
```
