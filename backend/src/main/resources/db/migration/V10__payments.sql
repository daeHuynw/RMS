CREATE TABLE payments (
  id                    BIGINT         NOT NULL AUTO_INCREMENT,
  order_id              BIGINT         NOT NULL,
  amount                DECIMAL(12,2)  NOT NULL,
  method                VARCHAR(30)    NOT NULL,
  status                VARCHAR(30)    NOT NULL DEFAULT 'SUCCESS',
  transaction_reference VARCHAR(120)   NULL,
  idempotency_key       VARCHAR(120)   NULL,
  paid_at               DATETIME(3)    NULL,
  created_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_idempotency (idempotency_key),
  KEY idx_payments_order (order_id),
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoices (
  id              BIGINT         NOT NULL AUTO_INCREMENT,
  order_id        BIGINT         NOT NULL,
  invoice_number  VARCHAR(30)    NOT NULL,
  subtotal        DECIMAL(12,2)  NOT NULL,
  tax_amount      DECIMAL(12,2)  NOT NULL DEFAULT 0,
  total_amount    DECIMAL(12,2)  NOT NULL,
  issued_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoices_order (order_id),
  UNIQUE KEY uk_invoices_number (invoice_number),
  CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
