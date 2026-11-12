import { defineConfig } from 'vite'
import { resolve } from 'node:path'

// Vite phục vụ HTML/CSS/JS và chuyển /api + /ws sang backend cổng 8080.
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    rollupOptions: {
      input: {
        index: resolve(__dirname, 'index.html'),
        home: resolve(__dirname, 'home.html'),
        register: resolve(__dirname, 'register.html'),
        profile: resolve(__dirname, 'profile.html'),
        menu: resolve(__dirname, 'menu.html'),
        cart: resolve(__dirname, 'cart.html'),
        reservations: resolve(__dirname, 'reservations.html'),
        myOrders: resolve(__dirname, 'my-orders.html'),
        qr: resolve(__dirname, 'qr.html'),
        staffTables: resolve(__dirname, 'staff/tables.html'),
        staffOrders: resolve(__dirname, 'staff/orders.html'),
        staffReservations: resolve(__dirname, 'staff/reservations.html'),
        kitchen: resolve(__dirname, 'kitchen/index.html'),
        adminDashboard: resolve(__dirname, 'admin/dashboard.html'),
        adminUsers: resolve(__dirname, 'admin/users.html'),
        adminMenu: resolve(__dirname, 'admin/menu.html'),
        adminCategories: resolve(__dirname, 'admin/categories.html'),
        adminTables: resolve(__dirname, 'admin/tables.html'),
        adminDiscounts: resolve(__dirname, 'admin/discounts.html'),
        adminSettings: resolve(__dirname, 'admin/settings.html'),
        adminAudit: resolve(__dirname, 'admin/audit.html'),
      },
    },
  },
})
