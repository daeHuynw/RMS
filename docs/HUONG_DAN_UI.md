
Tài liệu này gồm cách chạy FE, sửa UI ở đâu, và map toàn bộ file trang (kể cả khung trống).

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
                      │  /api/v1/...
                      ▼
              Backend cổng 8080  →  MySQL
```

Hiện trang đăng nhập đã chạy: gửi email/mật khẩu → backend trả hai token → lưu localStorage → gọi `/auth/me` → hiện tên, email, vai. Đăng xuất thì hủy refresh token và về form.

Backend phải đang chạy thì đăng nhập mới được. Mở `http://127.0.0.1:5173`.

## File đã có nội dung

```text
frontend/
├── index.html       Form đăng nhập / thẻ hồ sơ
├── vite.config.js   Cổng 5173 và proxy /api
├── package.json     Lệnh npm run dev
└── src/
    ├── style.css    CSS dùng chung (page, card, muted, error…)
    ├── api.js       Gọi backend và giữ token — dùng hàm api()
    └── main.js      Sự kiện đăng nhập / đăng xuất
```

- `api.js` đã có hàm `api()`. Trang khác cũng gọi hàm này, **không viết `fetch` mới**. Hàm tự gắn token nếu đã đăng nhập.
- `main.js` chỉ lo màn login. Màn mới viết JS riêng trong `src/pages/...`.
- `vite.config.js` và `package.json` không phải chỗ sửa giao diện.

## Sửa giao diện ở đâu

| Muốn làm gì | Sửa file |
| --- | --- |
| Đổi chữ, thêm ô, thêm khối trên trang login | `frontend/index.html` |
| Đổi màu, cỡ, khoảng cách dùng chung | `frontend/src/style.css` |
| Style riêng một nhóm màn | `frontend/src/styles/<nhóm>.css` |
| Đổi việc xảy ra sau khi bấm (login) | `frontend/src/main.js` |
| Logic màn khác | JS tương ứng trong bảng bên dưới |

Dùng lại class `page`, `card`, `muted`, `error` trước khi đặt class mới. Nút phụ thì thêm class mới, không sửa style của mọi `button` nếu chỉ muốn đổi một nút.

## Quy ước

1. HTML mới: gắn `src/style.css` + CSS riêng nếu có + `src/api.js` + JS của trang.
2. Không viết `fetch` mới; dùng `api()` trong `src/api.js`.
3. Đăng nhập xong chuyển trang bằng `window.location.href`, ví dụ `/admin/dashboard.html`.
4. Ẩn nút theo vai chỉ để giao diện gọn. Backend vẫn là nơi chặn quyền.
5. Không sửa file Java, SQL, hay `.env` khi chỉ làm UI. Không ghi mật khẩu vào tài liệu hay Git.

## Khung trang (file trống )

Các file dưới đây đã tạo sẵn, đang **trống**. Khi làm màn nào thì viết vào file tương ứng.

### Khách / công khai

| Trang | HTML | JS | CSS riêng |
| --- | --- | --- | --- |
| Trang chủ | `home.html` | `src/pages/home.js` | `src/styles/home.css` |
| Đăng ký | `register.html` | `src/pages/auth/register.js` | — |
| Thực đơn | `menu.html` | `src/pages/menu/menu.js` | `src/styles/menu.css` |
| Đặt bàn | `reservations.html` | `src/pages/reservations/reservations.js` | `src/styles/reservations.css` |
| Gọi món QR | `qr.html` | `src/pages/orders/qr.js` | `src/styles/orders.css` |
| Hồ sơ | `profile.html` | `src/pages/auth/profile.js` | — |
| Đơn của tôi | `my-orders.html` | `src/pages/orders/my-orders.js` | `src/styles/orders.css` |

### Nhân viên

| Trang | HTML | JS | CSS |
| --- | --- | --- | --- |
| Sơ đồ bàn | `staff/tables.html` | `src/pages/tables/staff-tables.js` | `src/styles/staff.css` |
| Đơn và thanh toán | `staff/orders.html` | `src/pages/orders/staff-orders.js` | `src/styles/staff.css` |
| Đặt bàn (staff) | `staff/reservations.html` | `src/pages/reservations/staff-reservations.js` | `src/styles/staff.css` |

### Bếp

| Trang | HTML | JS | CSS |
| --- | --- | --- | --- |
| Màn hình bếp | `kitchen/index.html` | `src/pages/kitchen/kitchen.js` | `src/styles/kitchen.css` |

### Admin

| Trang | HTML | JS | CSS |
| --- | --- | --- | --- |
| Dashboard | `admin/dashboard.html` | `src/pages/admin/dashboard.js` | `src/styles/admin.css` |
| Danh mục | `admin/categories.html` | `src/pages/admin/categories.js` | `src/styles/admin.css` |
| Món | `admin/menu.html` | `src/pages/admin/menu.js` | `src/styles/admin.css` |
| Bàn / QR | `admin/tables.html` | `src/pages/admin/tables.js` | `src/styles/admin.css` |
| Người dùng | `admin/users.html` | `src/pages/admin/users.js` | `src/styles/admin.css` |
| Giảm giá | `admin/discounts.html` | `src/pages/admin/discounts.js` | `src/styles/admin.css` |
| Cài đặt | `admin/settings.html` | `src/pages/admin/settings.js` | `src/styles/admin.css` |
| Audit | `admin/audit.html` | `src/pages/admin/audit.js` | `src/styles/admin.css` |

### File dùng chung (trống, viết sau)

- `src/auth.js` — đọc/ghi hồ sơ user, kiểm tra vai
- `src/nav.js` — thanh điều hướng theo vai

## Việc nên làm tiếp trên giao diện

1. Giữ màn đăng nhập ổn (lỗi / chào / đăng xuất).
2. Viết `register.html` + `src/pages/auth/register.js` (gọi `api('/auth/register', …)`).
3. Viết `src/nav.js` — hiện mục theo `roles` từ `/auth/me`.
4. Các trang menu / bàn / đơn / bếp: vẽ khung trước; gắn dữ liệu thật khi backend đã có API.
