INSERT INTO categories (name, description, is_active) VALUES
  ('Khai vị', 'Món mở đầu', TRUE),
  ('Món chính', 'Món mặn chính', TRUE),
  ('Đồ uống', 'Nước và trà', TRUE);

INSERT INTO menu_items (category_id, name, description, price, image_url, status)
SELECT c.id, 'Gỏi cuốn', 'Tôm thịt', 45000.00, NULL, 'AVAILABLE'
FROM categories c WHERE c.name = 'Khai vị';

INSERT INTO menu_items (category_id, name, description, price, image_url, status)
SELECT c.id, 'Phở bò', 'Phở tái nạm', 65000.00, NULL, 'AVAILABLE'
FROM categories c WHERE c.name = 'Món chính';

INSERT INTO menu_items (category_id, name, description, price, image_url, status)
SELECT c.id, 'Cơm gà', 'Cơm gà hội an', 55000.00, NULL, 'AVAILABLE'
FROM categories c WHERE c.name = 'Món chính';

INSERT INTO menu_items (category_id, name, description, price, image_url, status)
SELECT c.id, 'Trà đào', 'Trà đào cam sả', 35000.00, NULL, 'AVAILABLE'
FROM categories c WHERE c.name = 'Đồ uống';

INSERT INTO menu_items (category_id, name, description, price, image_url, status)
SELECT c.id, 'Pepsi', 'Lon 330ml', 20000.00, NULL, 'UNAVAILABLE'
FROM categories c WHERE c.name = 'Đồ uống';