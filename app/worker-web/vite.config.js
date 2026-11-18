import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/worker/',
  plugins: [vue()],
  server: {
    strictPort: true,
    port: 5174,
    proxy: { '/api': { target: 'http://localhost:8082', changeOrigin: true } }
  },
  build: { outDir: 'dist', chunkSizeWarningLimit: 1200 }
})
