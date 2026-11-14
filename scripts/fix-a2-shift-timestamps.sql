-- REVIEW A2 — dịch dữ liệu CŨ từ giờ máy về UTC. CHẠY TAY MỘT LẦN, không phải migration Flyway.
--
-- Bối cảnh: trước khi sửa A2, các cột MySQL tự điền (DEFAULT CURRENT_TIMESTAMP / ON UPDATE)
-- được ghi theo giờ máy (+07) trong khi app đọc mọi cột thời gian như UTC.
-- Sau khi sửa (application.yml ép phiên MySQL về +00:00), dòng MỚI đã là UTC;
-- script này chỉ để kéo các dòng CŨ về cùng chuẩn.
--
-- Điều kiện chạy:
--   * Chạy TRƯỚC lần khởi động backend đầu tiên có bản sửa A2 (nếu đã có dòng mới ghi UTC,
--     chúng sẽ bị trừ nhầm).
--   * Chỉ dùng cho DB đã tạo trên máy chạy MySQL ở +07. DB tạo mới sau bản sửa: KHÔNG chạy.
--   * @gio là số giờ lệch của máy so với UTC lúc dữ liệu được ghi.
--
-- An toàn:
--   * Có chốt chặn: chỉ dịch khi table_sessions còn lệch đúng @gio tiếng giữa cột app ghi
--     (opened_at, UTC thật) và cột DB tự điền (created_at). Chạy lần hai sẽ tự bỏ qua.
--   * updated_at được gán tường minh trong cùng câu UPDATE, nếu không MySQL sẽ tự ghi đè
--     updated_at = bây giờ (ON UPDATE CURRENT_TIMESTAMP).
--   * Hoàn tác được: cộng lại @gio tiếng.
--
-- Cách chạy:  mysql -u <user> -p rms_db < scripts/fix-a2-shift-timestamps.sql

SET @gio := 7;

SET @lech := (SELECT ROUND(AVG(TIMESTAMPDIFF(MINUTE, opened_at, created_at))) FROM table_sessions);
SET @chay := IF(@lech BETWEEN @gio * 60 - 5 AND @gio * 60 + 5, 1, 0);

SELECT @lech AS lech_phut_truoc_khi_chay,
       IF(@chay = 1, 'SE DICH du lieu cu ve UTC', 'BO QUA: khong thay lech dung so gio — khong sua gi') AS hanh_dong;

START TRANSACTION;

-- Bảng có cả created_at và updated_at
UPDATE users          SET created_at = created_at - INTERVAL @gio HOUR, updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE categories     SET created_at = created_at - INTERVAL @gio HOUR, updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE menu_items     SET created_at = created_at - INTERVAL @gio HOUR, updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE dining_tables  SET created_at = created_at - INTERVAL @gio HOUR, updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE reservations   SET created_at = created_at - INTERVAL @gio HOUR, updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE orders         SET created_at = created_at - INTERVAL @gio HOUR, updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE order_items    SET created_at = created_at - INTERVAL @gio HOUR, updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE discounts      SET created_at = created_at - INTERVAL @gio HOUR, updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;

-- Bảng chỉ có một cột DB tự điền
UPDATE refresh_tokens      SET created_at = created_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE table_sessions      SET created_at = created_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE payments            SET created_at = created_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE invoices            SET issued_at  = issued_at  - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE audit_logs          SET created_at = created_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE notifications       SET created_at = created_at - INTERVAL @gio HOUR WHERE @chay = 1;
UPDATE restaurant_settings SET updated_at = updated_at - INTERVAL @gio HOUR WHERE @chay = 1;

COMMIT;

-- Không đụng: flyway_schema_history.installed_on (kiểu TIMESTAMP, MySQL tự lưu UTC)
-- và các cột app tự ghi bằng Instant (opened_at, paid_at, reservation_start, expires_at, ...).

SELECT ROUND(AVG(TIMESTAMPDIFF(MINUTE, opened_at, created_at))) AS lech_phut_sau_khi_chay FROM table_sessions;
