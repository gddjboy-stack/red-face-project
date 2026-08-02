import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 培训演示专用 Vite 配置（2026-08-02，临时用途，非交付物）。
 *
 * 与仓库主配置 vite.config.ts 的唯一差别：allowedHosts 放开，
 * 以便沙箱临时公网域名可以访问 dev server。
 *
 * 注意：allowedHosts: true 会关闭 Vite 的 Host 头校验，
 * 仅可用于临时培训环境，切勿用于生产或长期环境。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
