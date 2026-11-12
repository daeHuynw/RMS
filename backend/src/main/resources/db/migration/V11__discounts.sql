CREATE TABLE discounts (
  id                      BIGINT         NOT NULL AUTO_INCREMENT,
  code                    VARCHAR(40)    NOT NULL,
  description             VARCHAR(500)   NULL,
  discount_type           VARCHAR(20)    NOT NULL,
  value                   DECIMAL(12,2)  NOT NULL,
  minimum_order_amount    DECIMAL(12,2)  NULL,
  maximum_discount_amount DECIMAL(12,2)  NULL,
  usage_limit             INT            NULL,
  used_count              INT            NOT NULL DEFAULT 0,
  start_at                DATETIME(3)    NOT NULL,
  end_at                  DATETIME(3)    NOT NULL,
  is_active               BOOLEAN        NOT NULL DEFAULT TRUE,
  created_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_discounts_code (code),
  KEY idx_discounts_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_discounts (
  order_id        BIGINT         NOT NULL,
  discount_id     BIGINT         NOT NULL,
  discount_amount DECIMAL(12,2)  NOT NULL,
  PRIMARY KEY (order_id, discount_id),
  CONSTRAINT fk_order_discounts_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_discounts_discount FOREIGN KEY (discount_id) REFERENCES discounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE orders
  ADD COLUMN discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER subtotal;

ALTER TABLE invoices
  ADD COLUMN discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER subtotal;
