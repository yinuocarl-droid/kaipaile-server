-- 00-245: stable source identity for actor-card internal automatic expansion.
-- Every DDL branch is guarded so the repository migration can be rerun safely.

SET @schema_name = DATABASE();

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'actor_ai_profile_card_task'
     AND COLUMN_NAME = 'actor_card_id') = 0,
  'ALTER TABLE `actor_ai_profile_card_task` ADD COLUMN `actor_card_id` BIGINT NULL COMMENT ''actor card owner of an internal expansion task'' AFTER `share_card_id`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'actor_ai_profile_card_task'
     AND COLUMN_NAME = 'source_asset_id') = 0,
  'ALTER TABLE `actor_ai_profile_card_task` ADD COLUMN `source_asset_id` BIGINT NULL COMMENT ''stable private source asset id'' AFTER `actor_card_id`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'actor_ai_profile_card_task'
     AND COLUMN_NAME = 'source_fingerprint') = 0,
  'ALTER TABLE `actor_ai_profile_card_task` ADD COLUMN `source_fingerprint` VARCHAR(64) NULL COMMENT ''sha256 of the stable source identity'' AFTER `source_asset_id`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `actor_ai_profile_card_task`
  MODIFY COLUMN `source_image_url` VARCHAR(1024) NULL COMMENT 'stable public source URL; NULL for private assets';

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'actor_ai_profile_card_task'
     AND INDEX_NAME = 'uk_actor_card_expand_source') = 0,
  'ALTER TABLE `actor_ai_profile_card_task` ADD UNIQUE KEY `uk_actor_card_expand_source` (`actor_card_id`, `generation_mode`, `source_fingerprint`, `deleted`)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'actor_card'
     AND COLUMN_NAME = 'expanded_source_fingerprint') = 0,
  'ALTER TABLE `actor_card` ADD COLUMN `expanded_source_fingerprint` VARCHAR(64) NULL COMMENT ''source fingerprint for expanded_image_url'' AFTER `expanded_image_url`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
