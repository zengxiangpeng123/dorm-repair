import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/student/',
  plugins: [vue()],
  server: {
    strictPort: true,
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8082', changeOrigin: true } }
  },
  build: { outDir: 'dist', chunkSizeWarningLimit: 1200 }
})
