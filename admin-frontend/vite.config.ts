import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

/**
 * 后端没有配置 CORS，浏览器直连 8080 必被拦，因此开发期一律走同源代理。
 * 端口固定 5174（strictPort），与旧 frontend/ 的 5173 并存，避免混淆。
 */
const target = process.env.ADMIN_API_ORIGIN || 'http://localhost:8080'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: 'components.d.ts',
      // importStyle 必须是 css：换成 sass 会把整套 Element Plus 主题打进产物
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      '/api': { target, changeOrigin: true },
      '/uploads': { target, changeOrigin: true },
      // 聊天长连接：漏 ws:true 会在握手阶段 426/404
      '/ws': { target, ws: true, changeOrigin: true },
    },
  },
  preview: {
    port: 5174,
    strictPort: true,
  },
})
