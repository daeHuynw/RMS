-- REVIEW B3: phiên bàn phải biết nó thuộc lượt đặt nào.
-- Trước đây hệ thống chỉ nối "lượt đặt → bàn → phiên đang mở của bàn", nên khách của lượt đặt cũ
-- có thể gọi món vào phiên của nhóm khác ngồi cùng bàn sau đó.
--
-- reservation_id NULL  = phiên khách vãng lai (staff mở tay) hoặc phiên cũ trước migration này.
-- UNIQUE               = mỗi lượt đặt chỉ check-in một lần nên có tối đa một phiên (nhiều NULL vẫn hợp lệ).
ALTER TABLE table_sessions
  ADD COLUMN reservation_id BIGINT NULL AFTER opened_by,
  ADD UNIQUE KEY uk_table_sessions_reservation (reservation_id),
  ADD CONSTRAINT fk_table_sessions_reservation
    FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE SET NULL;
