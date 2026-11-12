CREATE TABLE restaurant_settings (
  id                           TINYINT       NOT NULL,
  tax_rate                     DECIMAL(8,4)  NOT NULL,
  reservation_duration_minutes INT           NOT NULL,
  updated_at                   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO restaurant_settings (id, tax_rate, reservation_duration_minutes)
VALUES (1, 0.0800, 180);

CREATE TABLE audit_logs (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  actor_id    BIGINT       NULL,
  action      VARCHAR(60)  NOT NULL,
  target_type VARCHAR(40)  NOT NULL,
  target_id   VARCHAR(40)  NULL,
  detail      VARCHAR(500) NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  title      VARCHAR(150) NOT NULL,
  content    TEXT         NULL,
  type       VARCHAR(40)  NOT NULL DEFAULT 'INFO',
  is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_notifications_user_read (user_id, is_read),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
