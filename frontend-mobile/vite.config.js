import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 手机端独立部署在 /m/ 路径下,与桌面端(SPA 根路径)互不冲突
export default defineConfig({
  base: '/m/',
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8090', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})
