-- REVIEW C1: danh sách đơn của staff lấy "N đơn mới nhất" (ORDER BY created_at DESC, id DESC LIMIT N).
-- Chưa có index theo created_at thì MySQL phải đọc và sắp xếp cả bảng mỗi lần.
-- InnoDB tự gắn khoá chính (id) vào cuối index phụ nên index này phủ luôn cả cặp (created_at, id).
CREATE INDEX idx_orders_created_at ON orders (created_at);
