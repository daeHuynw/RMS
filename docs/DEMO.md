# Checklist demo theo vai

Chạy BE `:8080` + FE `:5173` trước. Tài khoản demo: tự đăng ký CUSTOMER; STAFF/KITCHEN tạo qua Admin Users; ADMIN gán bằng SQL.

```sql
-- Ví dụ gán ADMIN (thay email)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@example.com' AND r.name = 'ADMIN'
ON DUPLICATE KEY UPDATE user_id = user_id;
```

## Customer

- [ ] Đăng ký / đăng nhập → `home.html`
- [ ] Chưa login: `menu.html` chỉ xem (không Thêm giỏ / Đặt ngay)
- [ ] Login: nav có **Giỏ** cạnh Đăng xuất
- [ ] Đặt bàn `reservations.html` → PENDING
- [ ] Staff xác nhận → **check-in** (tự mở `table_session`)
- [ ] Đặt bàn `CHECKED_IN` → **Gọi món** → `menu.html`
- [ ] **Thêm giỏ** vài món → mở **Giỏ** trên nav → chỉnh SL → **Đặt món**
- [ ] (Tuỳ) **Đặt ngay** một món trên menu
- [ ] `my-orders.html` thấy đơn / trạng thái
- [ ] Hồ sơ: đổi tên / mật khẩu

Không dùng `qr.html` để gọi món (trang stub chuyển về menu).

## Staff

- [ ] Login → home: sơ đồ bàn / đơn / lịch đặt
- [ ] `staff/reservations.html`: confirm → check-in → complete
- [ ] `staff/tables.html`: sơ đồ màu trạng thái · **timer ngồi** khi có phiên OPEN · **lọc khu vực**
- [ ] Đóng phiên khi xong
- [ ] `staff/orders.html`: walk-in theo session · status · thanh toán · PDF

## Kitchen

- [ ] Login KITCHEN → `kitchen/`
- [ ] Thấy ticket từ đơn customer/staff (WebSocket STOMP)
- [ ] Đổi item → READY; staff SERVED

## Admin

- [ ] `admin/dashboard.html`: overview + **biểu đồ** doanh thu theo ngày + giờ cao điểm (chọn from/to → Xem)
- [ ] Users: tạo STAFF|KITCHEN · gán role · khóa/mở
- [ ] Menu / categories / tables / discounts
- [ ] Settings (tax, reservation duration)
- [ ] Audit log
- [ ] Nav chỉ hiện link ADMIN (CUSTOMER không thấy)

## Ngoài demo (không làm trong scope học)

- CRM VIP / điểm tích lũy
- HĐĐT nghị định, sổ thuế TT88
- Kho nguyên liệu / định mức món
- Cổng thanh toán online / split bill

## Smoke nhanh

```bash
bash scripts/smoke-api.sh
bash scripts/smoke-fe.sh
cd backend && mvn -q test
```
