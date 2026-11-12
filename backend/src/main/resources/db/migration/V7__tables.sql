CREATE TABLE dining_tables (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  table_code    VARCHAR(30)  NOT NULL,
  capacity      INT          NOT NULL,
  area          VARCHAR(60)  NULL,
  status        VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE',
  qr_token      VARCHAR(255) NULL,
  qr_token_hash VARCHAR(100) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dining_tables_code (table_code),
  UNIQUE KEY uk_dining_tables_qr_hash (qr_token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE table_sessions (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  table_id   BIGINT      NOT NULL,
  opened_by  BIGINT      NULL,
  opened_at  DATETIME(3) NOT NULL,
  closed_at  DATETIME(3) NULL,
  status     VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  open_table_id BIGINT GENERATED ALWAYS AS (IF(status = 'OPEN', table_id, NULL)) STORED,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_table_sessions_open (open_table_id),
  KEY idx_table_sessions_opened_by (opened_by),
  CONSTRAINT fk_table_sessions_table FOREIGN KEY (table_id) REFERENCES dining_tables (id),
  CONSTRAINT fk_table_sessions_opened_by FOREIGN KEY (opened_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO dining_tables (table_code, capacity, area, status) VALUES
  ('T01', 2, 'Tầng 1', 'AVAILABLE'),
  ('T02', 4, 'Tầng 1', 'AVAILABLE'),
  ('T03', 4, 'Sân vườn', 'AVAILABLE'),
  ('T04', 6, 'VIP', 'AVAILABLE');
