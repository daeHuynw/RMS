CREATE TABLE categories (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE menu_items (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  category_id BIGINT        NOT NULL,
  name        VARCHAR(150)  NOT NULL,
  description VARCHAR(1000) NULL,
  price       DECIMAL(12,2) NOT NULL,
  image_url   VARCHAR(500)  NULL,
  status      VARCHAR(30)   NOT NULL DEFAULT 'AVAILABLE',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_menu_items_category (category_id),
  KEY idx_menu_items_status (status),
  CONSTRAINT fk_menu_items_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;