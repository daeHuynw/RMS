-- REVIEW C3: thanh toán và hoá đơn là chứng từ tiền. Trước đây cả hai ON DELETE CASCADE theo orders,
-- nên xoá một đơn sẽ lặng lẽ xoá luôn chứng từ của nó. Đổi sang mặc định (RESTRICT): đơn đã có
-- thanh toán hoặc hoá đơn thì CSDL từ chối xoá.
--
-- order_items và order_discounts vẫn CASCADE: chúng là một phần của đơn, không phải chứng từ độc lập.
-- Tách DROP và ADD thành hai câu lệnh vì cùng dùng lại tên khoá ngoại.
ALTER TABLE payments DROP FOREIGN KEY fk_payments_order;
ALTER TABLE payments
  ADD CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id);

ALTER TABLE invoices DROP FOREIGN KEY fk_invoices_order;
ALTER TABLE invoices
  ADD CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES orders (id);
