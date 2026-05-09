SET @retired_scene_column = CONCAT('scene', '_key');
SET @retired_owner_column = CONCAT('owner', '_user', '_id');
SET @retired_contact_config_column = CONCAT('actor_card', '_config', '_id');
SET @retired_card_config_column = CONCAT('latest', '_config', '_id');
SET @retired_scene_code_to_replace = CONCAT('gen', 'eral');

SET @has_config_share_card_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = 'share_card_id'
);
SET @ddl = IF(
  @has_config_share_card_column = 0,
  'ALTER TABLE `actor_card_config` ADD COLUMN `share_card_id` BIGINT NULL COMMENT ''user share card id'' AFTER `config_id`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_preference_share_card_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_share_preference' AND COLUMN_NAME = 'share_card_id'
);
SET @ddl = IF(
  @has_preference_share_card_column = 0,
  'ALTER TABLE `actor_share_preference` ADD COLUMN `share_card_id` BIGINT NULL COMMENT ''user share card id'' AFTER `preference_id`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_contact_share_card_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND COLUMN_NAME = 'share_card_id'
);
SET @ddl = IF(
  @has_contact_share_card_column = 0,
  'ALTER TABLE `share_card_contact_request` ADD COLUMN `share_card_id` BIGINT NULL COMMENT ''independent share card id'' AFTER `viewer_user_id`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_history_share_card_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_view_history' AND COLUMN_NAME = 'share_card_id'
);
SET @ddl = IF(
  @has_history_share_card_column = 0,
  'ALTER TABLE `share_card_view_history` ADD COLUMN `share_card_id` BIGINT NULL COMMENT ''independent share card id'' AFTER `viewer_user_id`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_card_scene_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_share_card' AND COLUMN_NAME = @retired_scene_column
);
SET @has_card_retired_config_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_share_card' AND COLUMN_NAME = @retired_card_config_column
);
SET @has_config_user_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = 'user_id'
);
SET @has_config_profile_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = 'actor_profile_id'
);
SET @has_config_scene_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = @retired_scene_column
);
SET @has_config_template_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = 'template_id'
);
SET @dml = IF(
  @has_card_scene_column = 1,
  CONCAT('UPDATE `user_share_card` SET `', @retired_scene_column, '` = ''classic'' WHERE `', @retired_scene_column, '` = ''', @retired_scene_code_to_replace, ''''),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dml = IF(
  @has_config_scene_column = 1,
  CONCAT('UPDATE `actor_card_config` SET `', @retired_scene_column, '` = ''classic'' WHERE `', @retired_scene_column, '` = ''', @retired_scene_code_to_replace, ''''),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dml = IF(
  @has_card_scene_column = 1
    AND @has_card_retired_config_column = 1
    AND @has_config_user_column = 1
    AND @has_config_profile_column = 1
    AND @has_config_scene_column = 1
    AND @has_config_template_column = 1,
  CONCAT(
    'INSERT INTO `user_share_card` (`user_id`, `actor_profile_id`, `template_id`, `', @retired_scene_column, '`, `', @retired_card_config_column, '`, `share_status`, `default_card`, `version`, `deleted`, `rid`, `create_user_id`, `create_user_name`, `create_time`, `update_user_id`, `update_user_name`, `last_update`) ',
    'SELECT config.`user_id`, config.`actor_profile_id`, config.`template_id`, config.`', @retired_scene_column, '`, config.`config_id`, ''active'', CASE WHEN config.`', @retired_scene_column, '` = ''classic'' THEN 1 ELSE 0 END, 0, 0, NULL, NULL, '''', NOW(), NULL, '''', NOW() ',
    'FROM `actor_card_config` config ',
    'LEFT JOIN `user_share_card` existing ON existing.`user_id` = config.`user_id` AND existing.`', @retired_scene_column, '` = config.`', @retired_scene_column, '` AND existing.`deleted` = 0 ',
    'WHERE config.`deleted` = 0 AND config.`user_id` IS NOT NULL AND config.`', @retired_scene_column, '` IS NOT NULL AND existing.`share_card_id` IS NULL ',
    'ON DUPLICATE KEY UPDATE `actor_profile_id` = COALESCE(VALUES(`actor_profile_id`), `user_share_card`.`actor_profile_id`), `template_id` = COALESCE(VALUES(`template_id`), `user_share_card`.`template_id`), `', @retired_card_config_column, '` = COALESCE(VALUES(`', @retired_card_config_column, '`), `user_share_card`.`', @retired_card_config_column, '`), `last_update` = NOW()'
  ),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_template_scene_code_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'card_scene_template' AND COLUMN_NAME = 'template_scene_code'
);
SET @dml = IF(
  @has_card_scene_column = 1 AND @has_template_scene_code_column = 1,
  CONCAT(
    'UPDATE `user_share_card` card JOIN `card_scene_template` template ON template.`template_scene_code` = card.`', @retired_scene_column, '` ',
    'SET card.`template_id` = template.`template_id` ',
    'WHERE card.`template_id` IS NULL AND card.`deleted` = 0'
  ),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zz_backup_20260425_011_user_share_card_unresolved_template` LIKE `user_share_card`;
INSERT IGNORE INTO `zz_backup_20260425_011_user_share_card_unresolved_template`
SELECT * FROM `user_share_card` WHERE `template_id` IS NULL OR `template_id` <= 0;

DELETE FROM `user_share_card` WHERE `template_id` IS NULL OR `template_id` <= 0;

SET @dml = IF(
  @has_card_scene_column = 1 AND @has_config_user_column = 1 AND @has_config_scene_column = 1,
  CONCAT(
    'UPDATE `actor_card_config` config JOIN `user_share_card` card ON card.`user_id` = config.`user_id` AND card.`', @retired_scene_column, '` = config.`', @retired_scene_column, '` AND card.`deleted` = 0 ',
    'SET config.`share_card_id` = card.`share_card_id` ',
    'WHERE config.`deleted` = 0 AND (config.`share_card_id` IS NULL OR config.`share_card_id` <= 0)'
  ),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_preference_user_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_share_preference' AND COLUMN_NAME = 'user_id'
);
SET @has_preference_scene_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_share_preference' AND COLUMN_NAME = @retired_scene_column
);
SET @dml = IF(
  @has_preference_scene_column = 1,
  CONCAT('UPDATE `actor_share_preference` SET `', @retired_scene_column, '` = ''classic'' WHERE `', @retired_scene_column, '` = ''', @retired_scene_code_to_replace, ''''),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dml = IF(
  @has_card_scene_column = 1 AND @has_preference_user_column = 1 AND @has_preference_scene_column = 1,
  CONCAT(
    'UPDATE `actor_share_preference` pref JOIN `user_share_card` card ON card.`user_id` = pref.`user_id` AND card.`', @retired_scene_column, '` = pref.`', @retired_scene_column, '` AND card.`deleted` = 0 ',
    'SET pref.`share_card_id` = card.`share_card_id` ',
    'WHERE pref.`deleted` = 0 AND (pref.`share_card_id` IS NULL OR pref.`share_card_id` <= 0)'
  ),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_contact_owner_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND COLUMN_NAME = @retired_owner_column
);
SET @has_contact_scene_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND COLUMN_NAME = @retired_scene_column
);
SET @dml = IF(
  @has_contact_scene_column = 1,
  CONCAT('UPDATE `share_card_contact_request` SET `', @retired_scene_column, '` = ''classic'' WHERE `', @retired_scene_column, '` = ''', @retired_scene_code_to_replace, ''''),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dml = IF(
  @has_card_scene_column = 1 AND @has_contact_owner_column = 1 AND @has_contact_scene_column = 1,
  CONCAT(
    'UPDATE `share_card_contact_request` req JOIN `user_share_card` card ON card.`user_id` = req.`', @retired_owner_column, '` AND card.`', @retired_scene_column, '` = req.`', @retired_scene_column, '` AND card.`deleted` = 0 ',
    'SET req.`share_card_id` = card.`share_card_id` ',
    'WHERE req.`deleted` = 0 AND (req.`share_card_id` IS NULL OR req.`share_card_id` <= 0)'
  ),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_history_owner_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_view_history' AND COLUMN_NAME = @retired_owner_column
);
SET @has_history_scene_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_view_history' AND COLUMN_NAME = @retired_scene_column
);
SET @dml = IF(
  @has_history_scene_column = 1,
  CONCAT('UPDATE `share_card_view_history` SET `', @retired_scene_column, '` = ''classic'' WHERE `', @retired_scene_column, '` = ''', @retired_scene_code_to_replace, ''''),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dml = IF(
  @has_card_scene_column = 1 AND @has_history_owner_column = 1 AND @has_history_scene_column = 1,
  CONCAT(
    'UPDATE `share_card_view_history` hist JOIN `user_share_card` card ON card.`user_id` = hist.`', @retired_owner_column, '` AND card.`', @retired_scene_column, '` = hist.`', @retired_scene_column, '` AND card.`deleted` = 0 ',
    'SET hist.`share_card_id` = card.`share_card_id` ',
    'WHERE hist.`deleted` = 0 AND (hist.`share_card_id` IS NULL OR hist.`share_card_id` <= 0)'
  ),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_acc_before_cleanup` LIKE `actor_card_config`;
INSERT IGNORE INTO `zz_bak_20260425_011_acc_before_cleanup`
SELECT * FROM `actor_card_config`;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_pref_before_cleanup` LIKE `actor_share_preference`;
INSERT IGNORE INTO `zz_bak_20260425_011_pref_before_cleanup`
SELECT * FROM `actor_share_preference`;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_card_before_cleanup` LIKE `user_share_card`;
INSERT IGNORE INTO `zz_bak_20260425_011_card_before_cleanup`
SELECT * FROM `user_share_card`;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_req_before_cleanup` LIKE `share_card_contact_request`;
INSERT IGNORE INTO `zz_bak_20260425_011_req_before_cleanup`
SELECT * FROM `share_card_contact_request`;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_hist_before_cleanup` LIKE `share_card_view_history`;
INSERT IGNORE INTO `zz_bak_20260425_011_hist_before_cleanup`
SELECT * FROM `share_card_view_history`;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_acc_bad_card` LIKE `actor_card_config`;
INSERT IGNORE INTO `zz_bak_20260425_011_acc_bad_card`
SELECT * FROM `actor_card_config` WHERE `share_card_id` IS NULL OR `share_card_id` <= 0;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_pref_bad_card` LIKE `actor_share_preference`;
INSERT IGNORE INTO `zz_bak_20260425_011_pref_bad_card`
SELECT * FROM `actor_share_preference` WHERE `share_card_id` IS NULL OR `share_card_id` <= 0;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_req_bad_card` LIKE `share_card_contact_request`;
INSERT IGNORE INTO `zz_bak_20260425_011_req_bad_card`
SELECT * FROM `share_card_contact_request` WHERE `share_card_id` IS NULL OR `share_card_id` <= 0;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_011_hist_bad_card` LIKE `share_card_view_history`;
INSERT IGNORE INTO `zz_bak_20260425_011_hist_bad_card`
SELECT * FROM `share_card_view_history` WHERE `share_card_id` IS NULL OR `share_card_id` <= 0;

DELETE FROM `actor_card_config` WHERE `share_card_id` IS NULL OR `share_card_id` <= 0;
DELETE FROM `actor_share_preference` WHERE `share_card_id` IS NULL OR `share_card_id` <= 0;
DELETE FROM `share_card_contact_request` WHERE `share_card_id` IS NULL OR `share_card_id` <= 0;
DELETE FROM `share_card_view_history` WHERE `share_card_id` IS NULL OR `share_card_id` <= 0;

ALTER TABLE `actor_card_config` MODIFY COLUMN `share_card_id` BIGINT NOT NULL COMMENT 'user share card id';
ALTER TABLE `actor_share_preference` MODIFY COLUMN `share_card_id` BIGINT NOT NULL COMMENT 'user share card id';
ALTER TABLE `share_card_contact_request` MODIFY COLUMN `share_card_id` BIGINT NOT NULL COMMENT 'independent share card id';
ALTER TABLE `share_card_view_history` MODIFY COLUMN `share_card_id` BIGINT NOT NULL COMMENT 'independent share card id';

SET @old_index = CONCAT('uk_actor_card_config_user_', @retired_scene_column);
SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND INDEX_NAME = @old_index
);
SET @ddl = IF(@has_old_index > 0, CONCAT('ALTER TABLE `actor_card_config` DROP INDEX `', @old_index, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND INDEX_NAME = 'idx_actor_card_config_actor_profile_id'
);
SET @ddl = IF(@has_old_index > 0, 'ALTER TABLE `actor_card_config` DROP INDEX `idx_actor_card_config_actor_profile_id`', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND INDEX_NAME = 'idx_actor_card_config_template_id'
);
SET @ddl = IF(@has_old_index > 0, 'ALTER TABLE `actor_card_config` DROP INDEX `idx_actor_card_config_template_id`', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_index = CONCAT('uk_actor_share_preference_user_', @retired_scene_column);
SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_share_preference' AND INDEX_NAME = @old_index
);
SET @ddl = IF(@has_old_index > 0, CONCAT('ALTER TABLE `actor_share_preference` DROP INDEX `', @old_index, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_index = CONCAT('uk_user_share_card_user_', @retired_scene_column);
SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_share_card' AND INDEX_NAME = @old_index
);
SET @ddl = IF(@has_old_index > 0, CONCAT('ALTER TABLE `user_share_card` DROP INDEX `', @old_index, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_index = CONCAT('idx_user_share_card_', @retired_card_config_column);
SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_share_card' AND INDEX_NAME = @old_index
);
SET @ddl = IF(@has_old_index > 0, CONCAT('ALTER TABLE `user_share_card` DROP INDEX `', @old_index, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND INDEX_NAME = 'idx_share_card_contact_request_owner_status_requested_at'
);
SET @ddl = IF(@has_old_index > 0, 'ALTER TABLE `share_card_contact_request` DROP INDEX `idx_share_card_contact_request_owner_status_requested_at`', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_index = CONCAT('idx_share_card_contact_request_viewer_owner_', @retired_scene_column, '_requested_at');
SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND INDEX_NAME = @old_index
);
SET @ddl = IF(@has_old_index > 0, CONCAT('ALTER TABLE `share_card_contact_request` DROP INDEX `', @old_index, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_index = CONCAT('idx_share_card_view_history_owner_', @retired_scene_column, '_viewed_at');
SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_view_history' AND INDEX_NAME = @old_index
);
SET @ddl = IF(@has_old_index > 0, CONCAT('ALTER TABLE `share_card_view_history` DROP INDEX `', @old_index, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_old_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_view_history' AND INDEX_NAME = 'idx_share_card_view_history_owner_viewed_at'
);
SET @ddl = IF(@has_old_index > 0, 'ALTER TABLE `share_card_view_history` DROP INDEX `idx_share_card_view_history_owner_viewed_at`', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_new_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND INDEX_NAME = 'uk_actor_card_config_share_card_id'
);
SET @ddl = IF(@has_new_index = 0, 'ALTER TABLE `actor_card_config` ADD UNIQUE KEY `uk_actor_card_config_share_card_id` (`share_card_id`)', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_new_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND INDEX_NAME = 'idx_actor_card_config_last_update'
);
SET @ddl = IF(@has_new_index = 0, 'ALTER TABLE `actor_card_config` ADD KEY `idx_actor_card_config_last_update` (`last_update`)', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_new_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_share_preference' AND INDEX_NAME = 'uk_actor_share_preference_share_card_id'
);
SET @ddl = IF(@has_new_index = 0, 'ALTER TABLE `actor_share_preference` ADD UNIQUE KEY `uk_actor_share_preference_share_card_id` (`share_card_id`)', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_new_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND INDEX_NAME = 'idx_share_card_contact_request_viewer_card_requested_at'
);
SET @ddl = IF(@has_new_index = 0, 'ALTER TABLE `share_card_contact_request` ADD KEY `idx_share_card_contact_request_viewer_card_requested_at` (`viewer_user_id`, `share_card_id`, `requested_at`)', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_new_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND INDEX_NAME = 'idx_share_card_contact_request_card_status_requested_at'
);
SET @ddl = IF(@has_new_index = 0, 'ALTER TABLE `share_card_contact_request` ADD KEY `idx_share_card_contact_request_card_status_requested_at` (`share_card_id`, `status`, `requested_at`)', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_new_index = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_view_history' AND INDEX_NAME = 'idx_share_card_view_history_share_card_viewed_at'
);
SET @ddl = IF(@has_new_index = 0, 'ALTER TABLE `share_card_view_history` ADD KEY `idx_share_card_view_history_share_card_viewed_at` (`share_card_id`, `viewed_at`)', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'user_id';
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `actor_card_config` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'actor_profile_id';
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `actor_card_config` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_scene_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `actor_card_config` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'template_id';
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_card_config' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `actor_card_config` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'user_id';
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_share_preference' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `actor_share_preference` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_scene_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'actor_share_preference' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `actor_share_preference` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_scene_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_share_card' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `user_share_card` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_card_config_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_share_card' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `user_share_card` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_owner_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `share_card_contact_request` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_contact_config_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `share_card_contact_request` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_scene_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_contact_request' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `share_card_contact_request` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_owner_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_view_history' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `share_card_view_history` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = @retired_scene_column;
SET @has_column = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'share_card_view_history' AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@has_column = 1, CONCAT('ALTER TABLE `share_card_view_history` DROP COLUMN `', @column_name, '`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
