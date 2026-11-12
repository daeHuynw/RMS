# Việc nên làm (ghi chú học)

Đọc [`HUONG_DAN_UI.md`](HUONG_DAN_UI.md) và [`useCaseDiagram.md`](useCaseDiagram.md) trước khi sửa UI.

## Luồng đã chốt

1. Guest chỉ xem thực đơn.
2. Customer đăng nhập → đặt bàn.
3. Staff confirm + check-in → mở phiên bàn.
4. Customer gọi món trên `menu.html` (Thêm giỏ / Đặt ngay) hoặc `cart.html`.
5. Không dùng QR để khách gọi món.
6. Không làm CRM VIP/điểm, HĐĐT nghị định, kho NL trong scope học này.

## Đã polish (Sapo-lite)

- Staff sơ đồ bàn: màu trạng thái + timer từ `openedAt` + lọc khu vực.
- Admin dashboard: biểu đồ cột SVG doanh thu / peak-hours (API sẵn có).
- Security: `permitAll` `/error` để tránh 401 giả khi API lỗi.

## UI

- Style chung: `src/style.css`; riêng nhóm: `src/styles/*.css`.
- Không `fetch` mới — dùng `api()` trong `src/api.js`.
- Nav theo vai: `src/nav.js`; giỏ: `src/cart.js`.

## Việc còn lại (tuỳ)

1. Chạy checklist tay [`DEMO.md`](DEMO.md).
2. (Tuỳ) PR GitHub / review nhánh FE.

## Demo

Checklist tay: [`DEMO.md`](DEMO.md).
