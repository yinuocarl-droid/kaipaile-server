-- 00-199 real share-card favorites.

CREATE TABLE IF NOT EXISTS share_card_favorite (
  favorite_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  share_card_id BIGINT NOT NULL,
  version INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  rid VARCHAR(64) DEFAULT NULL,
  create_user_id BIGINT DEFAULT NULL,
  create_user_name VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_user_id BIGINT DEFAULT NULL,
  update_user_name VARCHAR(64) DEFAULT '',
  last_update DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  active_share_card_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN share_card_id ELSE NULL END) STORED,
  PRIMARY KEY (favorite_id),
  UNIQUE KEY uk_share_card_favorite_user_active_card (user_id, active_share_card_id),
  KEY idx_share_card_favorite_user_created (user_id, deleted, create_time),
  KEY idx_share_card_favorite_card (share_card_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user share-card favorites';
