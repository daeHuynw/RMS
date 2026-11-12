# RMS (học) — Restaurant Management System

Stack: **Spring Boot / Java 21** (modular monolith) + **Vite HTML/CSS/JS** + **MySQL** (`rms_db`).  
JWT access + refresh · RBAC: `CUSTOMER` / `STAFF` / `KITCHEN` / `ADMIN`.

## Luồng nghiệp vụ (đã chốt)

1. Guest xem thực đơn công khai.
2. Customer đăng nhập → đặt bàn.
3. Staff confirm + **check-in** → mở `table_session`.
4. Customer gọi món (giỏ / đặt ngay) — **không** QR khách.
5. Kitchen cập nhật món realtime; Staff thanh toán + PDF.
6. Admin: menu, bàn, giảm giá, users, **dashboard + biểu đồ**, settings, audit.

Ngoài scope học: CRM VIP/điểm, HĐĐT nghị định, kho NL, cổng thanh toán online.

## Yêu cầu

- JDK 21, Maven 3.9+
- Node.js 20+ (FE)
- MySQL 8 — database `rms_db`, user học (không dùng DB project cũ `rms`)

## Cấu hình

```bash
cp .env.example .env
# Điền RMS_MYSQL_* và RMS_JWT_SECRET (openssl rand -base64 48)
```

`.env` đã nằm trong `.gitignore` — **không commit**. Chỉ commit `.env.example`.

## MySQL (gợi ý)

```sql
CREATE DATABASE rms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'rms_learn'@'%' IDENTIFIED BY 'your-password';
GRANT ALL ON rms_db.* TO 'rms_learn'@'%';
FLUSH PRIVILEGES;
```

Flyway chạy migration `V1`…`V12` khi BE start (`ddl-auto: validate`).

## Chạy local

```bash
# Backend
cd backend
mvn spring-boot:run
# → http://127.0.0.1:8080  health: GET /api/v1/health

# Frontend (terminal khác)
cd frontend
npm install
npm run dev
# → http://127.0.0.1:5173  (proxy /api → :8080)
```

Đăng nhập xong vào `home.html`. Gán ADMIN bằng SQL (không bootstrap) — xem [`docs/DEMO.md`](docs/DEMO.md).

## Test / smoke

```bash
# Slice MockMvc: auth · menu · order create (không cần MySQL)
cd backend && mvn test

# API smoke (BE đang chạy)
bash scripts/smoke-api.sh

# FE build + (tuỳ) GET các trang HTML
bash scripts/smoke-fe.sh
```

## Tài liệu

| File | Nội dung |
| --- | --- |
| [`docs/DEMO.md`](docs/DEMO.md) | Checklist demo theo vai |
| [`docs/HUONG_DAN_UI.md`](docs/HUONG_DAN_UI.md) | Map trang FE + quy ước UI |
| [`docs/useCaseDiagram.md`](docs/useCaseDiagram.md) | Use case + ảnh `docs/diagrams/` |
| [`docs/Viecnenlam.md`](docs/Viecnenlam.md) | Scope / việc còn lại |
| `AGENTS.md` | Hướng dẫn AI mentor |
| `checklist.md` / `flow.md` | Tiến độ học (local; `flow.md` gitignored) |

## Cấu trúc nhanh

| Phần | Đường dẫn |
| --- | --- |
| BE modules | `backend/src/main/java/com/rms/modules/*` |
| Flyway | `backend/src/main/resources/db/migration/` |
| FE pages | `frontend/*.html`, `frontend/staff|admin|kitchen/` |
| API helper | `frontend/src/api.js` · nav `src/nav.js` · auth `src/auth.js` |

## Bảo mật local

- Không đưa mật khẩu / JWT secret vào README hay git
- Upload ảnh món: thư mục `uploads/` (gitignored)
- BE vẫn là nơi chặn quyền; FE chỉ ẩn/hiện menu theo `roles`
