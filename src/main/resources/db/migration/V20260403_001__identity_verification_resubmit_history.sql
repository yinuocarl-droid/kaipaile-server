-- Allow verify reject/resubmit to preserve per-user history while still reserving
-- one id_card_hash owner across accounts.

CREATE TABLE `identity_verification_owner` (
  `owner_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
  `id_card_hash` CHAR(64) NOT NULL COMMENT 'sha256 for cross-account reservation',
  `user_id` BIGINT NOT NULL COMMENT 'reserved owner user id',
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `rid` VARCHAR(64) DEFAULT NULL,
  `create_user_id` BIGINT DEFAULT NULL,
  `create_user_name` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` BIGINT DEFAULT NULL,
  `update_user_name` VARCHAR(64) DEFAULT '',
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`owner_id`),
  UNIQUE KEY `uk_identity_verification_owner_id_card_hash` (`id_card_hash`),
  KEY `idx_identity_verification_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='identity verification id card hash owner';

INSERT INTO `identity_verification_owner` (
  `id_card_hash`,
  `user_id`,
  `version`,
  `deleted`,
  `create_user_id`,
  `create_user_name`,
  `create_time`,
  `update_user_id`,
  `update_user_name`,
  `last_update`
)
SELECT
  `id_card_hash`,
  `user_id`,
  0,
  0,
  `create_user_id`,
  COALESCE(`create_user_name`, ''),
  COALESCE(`create_time`, CURRENT_TIMESTAMP),
  `update_user_id`,
  COALESCE(`update_user_name`, ''),
  COALESCE(`last_update`, CURRENT_TIMESTAMP)
FROM `identity_verification`
WHERE `deleted` = 0;

ALTER TABLE `identity_verification`
  DROP INDEX `uk_identity_verification_id_card_hash`,
  ADD KEY `idx_identity_verification_id_card_hash` (`id_card_hash`);
