CREATE TABLE `share_card_view_history` (
  `history_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
  `viewer_user_id` BIGINT NOT NULL COMMENT 'viewer user id',
  `share_card_id` BIGINT NOT NULL COMMENT 'independent share card id',
  `viewed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'view time',
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `rid` VARCHAR(64) DEFAULT NULL,
  `create_user_id` BIGINT DEFAULT NULL,
  `create_user_name` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` BIGINT DEFAULT NULL,
  `update_user_name` VARCHAR(64) DEFAULT '',
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`history_id`),
  KEY `idx_share_card_view_history_viewer_viewed_at` (`viewer_user_id`, `viewed_at`),
  KEY `idx_share_card_view_history_share_card_viewed_at` (`share_card_id`, `viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='share card real view histories';
