import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发时前端 5173，接口打后端 8082；生产构建把 dist 交给 nginx 或后端静态托管。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8082', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200
  }
})
