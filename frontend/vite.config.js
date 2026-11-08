import { defineConfig } from 'vite'

// Vite phục vụ HTML/CSS/JS và chuyển /api sang backend cổng 8080.
export default defineConfig({
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
