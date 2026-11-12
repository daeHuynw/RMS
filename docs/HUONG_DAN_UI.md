# Hướng dẫn UI — RMS học

Tài liệu này gồm cách chạy FE, sửa UI ở đâu, và map toàn bộ file trang.

## Project

Có hai chương trình chạy cùng lúc.

**Backend** Spring Boot, cổng 8080. Giữ dữ liệu trong MySQL, kiểm tra đăng nhập và trả JSON.

```bash
cd backend
mvn spring-boot:run
```

**Frontend** HTML, CSS và JavaScript. Vite mở trang ở cổng 5173. Trang gọi `/api/v1/...`. Vite proxy sang backend cổng 8080. Người dùng chỉ thấy địa chỉ 5173.

```bash
cd frontend
npm install
npm run dev
```

```text
Trình duyệt  →  http://127.0.0.1:5173
                      │
                      ▼
                    Vite
                      │  /api  +  /ws
                      ▼
              Backend cổng 8080  →  MySQL
```

Đăng nhập xong vào `home.html`. Backend phải đang chạy: `http://127.0.0.1:5173`.

## Luồng khách (không QR)

```text
Guest: chỉ xem menu.html
  → Đăng nhập / Đăng ký
Customer: đặt bàn (reservations.html)
  → Staff confirm + check-in (mở table_session)
  → menu.html: Thêm giỏ / Đặt ngay
  → nav «Giỏ» → cart.html → Đặt món
  → my-orders.html theo dõi đơn
```

`qr.html` chỉ stub → chuyển `menu.html` (không gọi món bằng token QR).

## File dùng chung

```text
frontend/src/
├── style.css       CSS chung + .app-nav*
├── api.js          api() / apiBlob() — không viết fetch mới
├── auth.js         fetchMe / requireRoles / logout
├── nav.js          thanh nav theo vai + nút Giỏ
├── cart.js         giỏ localStorage
└── main.js         login → home.html
```

- Trang khác gọi `api()`, **không viết `fetch` mới**.
- Chuyển trang: `window.location.href`.
- Ẩn nút theo vai chỉ để gọn UI; BE vẫn chặn quyền.

## Sửa giao diện ở đâu

| Muốn làm gì | Sửa file |
| --- | --- |
| Đổi chữ / khối login | `frontend/index.html` |
| Màu / khoảng cách chung | `frontend/src/style.css` |
| Style riêng nhóm màn | `frontend/src/styles/<nhóm>.css` |
| Login | `frontend/src/main.js` |
| Logic màn khác | JS trong bảng bên dưới |

Dùng lại class `page`, `card`, `muted`, `error` trước khi invent class mới.

## Map trang

### Khách / Customer

| Trang | HTML | JS | CSS |
| --- | --- | --- | --- |
| Login | `index.html` | `src/main.js` | `style.css` |
| Trang chủ | `home.html` | `src/pages/home.js` | `styles/home.css` |
| Đăng ký | `register.html` | `src/pages/auth/register.js` | — |
| Hồ sơ | `profile.html` | `src/pages/auth/profile.js` | — |
| Thực đơn (+ gọi món) | `menu.html` | `src/pages/menu/menu.js` | `styles/menu.css`, `orders.css` |
| Giỏ hàng | `cart.html` | `src/pages/cart/cart.js` | `styles/orders.css` |
| Đặt bàn | `reservations.html` | `src/pages/reservations/reservations.js` | `styles/reservations.css` |
| Đơn của tôi | `my-orders.html` | `src/pages/orders/my-orders.js` | `styles/orders.css` |
| QR (stub) | `qr.html` | redirect → `menu.html` | — |

### Nhân viên

| Trang | HTML | JS | CSS | Ghi chú UI |
| --- | --- | --- | --- | --- |
| Sơ đồ bàn | `staff/tables.html` | `src/pages/tables/staff-tables.js` | `styles/staff.css` | Timer `openedAt`, lọc khu vực, màu status |
| Đơn / thanh toán | `staff/orders.html` | `src/pages/orders/staff-orders.js` | `styles/staff.css` | |
| Đặt bàn staff | `staff/reservations.html` | `src/pages/reservations/staff-reservations.js` | `styles/staff.css` | Check-in mở session |

### Bếp

| Trang | HTML | JS | CSS |
| --- | --- | --- | --- |
| KDS | `kitchen/index.html` | `src/pages/kitchen/kitchen.js` | `styles/kitchen.css` |

### Admin

| Trang | HTML | JS | CSS | Ghi chú UI |
| --- | --- | --- | --- | --- |
| Dashboard | `admin/dashboard.html` | `src/pages/admin/dashboard.js` | `styles/admin.css` | Biểu đồ SVG doanh thu + peak-hours |
| Danh mục | `admin/categories.html` | `src/pages/admin/categories.js` | `styles/admin.css` | |
| Món | `admin/menu.html` | `src/pages/admin/menu.js` | `styles/admin.css` | |
| Bàn | `admin/tables.html` | `src/pages/admin/tables.js` | `styles/admin.css` | QR admin giữ schema, không dùng cho khách |
| Users | `admin/users.html` | `src/pages/admin/users.js` | `styles/admin.css` | |
| Giảm giá | `admin/discounts.html` | `src/pages/admin/discounts.js` | `styles/admin.css` | |
| Cài đặt | `admin/settings.html` | `src/pages/admin/settings.js` | `styles/admin.css` | |
| Audit | `admin/audit.html` | `src/pages/admin/audit.js` | `styles/admin.css` | |

## Tài liệu liên quan

- Use case: [`docs/useCaseDiagram.md`](useCaseDiagram.md) + ảnh `docs/diagrams/`
- Demo theo vai: [`docs/DEMO.md`](DEMO.md)
- Việc nên làm: [`docs/Viecnenlam.md`](Viecnenlam.md)
- Chạy local: [`README.md`](../README.md)
