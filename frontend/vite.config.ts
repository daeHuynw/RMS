import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
// cho cong 5173 goi dc api tren cong 8080
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})