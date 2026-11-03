-- 00-264 B5: stable person/background AI inputs, separate from decoration layout.
-- Safe to replay manually: table creation and task-column additions are conditional.

CREATE TABLE IF NOT EXISTS `actor_card_visual_input` (
  `card_id` BIGINT NOT NULL COMMENT 'actor_card.id; one current input per card',
  `user_id` BIGINT NOT NULL COMMENT 'owner id copied for ACL and CAS',
  `generation_mode` VARCHAR(32) NOT NULL COMMENT 'person_ai | person_background_ai',
  `person_source_type` VARCHAR(32) NOT NULL COMMENT 'asset | managed_url',
  `person_asset_id` BIGINT DEFAULT NULL COMMENT 'stable owned private photo id',
  `person_image_url` VARCHAR(1024) DEFAULT NULL COMMENT 'stable controlled URL; never a short signature',
  `background_id` BIGINT DEFAULT NULL COMMENT 'actor_card_background.id, independent from person source',
  `background_image_url` VARCHAR(1024) DEFAULT NULL COMMENT 'immutable background URL snapshot included in input fingerprint',
  `revision` BIGINT NOT NULL DEFAULT 1 COMMENT 'owner input CAS revision',
  `input_fingerprint` CHAR(64) NOT NULL COMMENT 'mode/person/background-content/generation-config SHA-256',
  `generation_config_version` VARCHAR(64) NOT NULL COMMENT 'image instruction version; excludes decoration layout',
  `generated_image_url` VARCHAR(1024) DEFAULT NULL COMMENT 'managed output for the current fingerprint',
  `generated_fingerprint` CHAR(64) DEFAULT NULL COMMENT 'fingerprint guarded result identity',
  `latest_task_id` VARCHAR(64) DEFAULT NULL COMMENT 'latest task for the current fingerprint',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`card_id`),
  KEY `idx_actor_card_visual_input_user` (`user_id`, `last_update`),
  KEY `idx_actor_card_visual_input_background` (`background_id`, `generation_mode`),
  KEY `idx_actor_card_visual_input_fingerprint` (`input_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='演员卡AI首图稳定输入与当前结果（不含装修布局）';

SET @schema_name = DATABASE();

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'actor_card_visual_input'
     AND COLUMN_NAME = 'background_image_url') = 0,
  'ALTER TABLE `actor_card_visual_input` ADD COLUMN `background_image_url` VARCHAR(1024) NULL COMMENT ''immutable background URL snapshot included in input fingerprint'' AFTER `background_id`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
