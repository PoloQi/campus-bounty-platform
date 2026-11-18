import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const backend = new URL(process.env.BOUNTY_API_ORIGIN ?? 'http://127.0.0.1:8080');
if (!['http:', 'https:'].includes(backend.protocol) || backend.username || backend.password ||
    backend.pathname !== '/' || backend.search || backend.hash) {
  throw new Error('BOUNTY_API_ORIGIN 必须是 HTTP(S) 服务地址，例如 http://127.0.0.1:8080');
}

const httpProxy = {
  target: backend.origin,
  changeOrigin: true,
  configure(proxy) {
    proxy.on('error', (_error, _request, response) => {
      if (!response?.writeHead || response.headersSent || response.destroyed) return;
      response.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
      response.end(JSON.stringify({ code: 50200, message: '无法连接后端，请确认 Spring Boot 服务已启动。' }));
    });
  },
};

const proxy = {
  '/api/': httpProxy,
  '/uploads/': httpProxy,
  '^/ws/chat(?:\\?|$)': { target: backend.origin, changeOrigin: true, ws: true },
};

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: Number(process.env.FRONTEND_PORT ?? 5173),
    strictPort: true,
    proxy,
  },
  preview: { host: '127.0.0.1', strictPort: true, proxy },
});
