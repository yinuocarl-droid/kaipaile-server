-- Physically align the crew profile schema with the current runtime contract.
-- Backup first, then rename legacy physical objects and JSON keys.

SET @legacy_profile_table = CONCAT('com', 'pany_profile');
SET @current_profile_table = 'crew_profile';

SET @has_legacy_profile_table = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @legacy_profile_table
);
SET @has_current_profile_table = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @current_profile_table
);

SET @ddl = IF(
  @has_legacy_profile_table > 0 AND @has_current_profile_table = 0,
  CONCAT('CREATE TABLE IF NOT EXISTS `zz_bak_20260427_024_crew_profile_pre_rename` AS SELECT * FROM `', @legacy_profile_table, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  @has_legacy_profile_table > 0 AND @has_current_profile_table = 0,
  CONCAT('RENAME TABLE `', @legacy_profile_table, '` TO `', @current_profile_table, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_current_profile_table = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @current_profile_table
);

SET @legacy_profile_id_col = CONCAT('com', 'pany_profile_id');
SET @legacy_no_col = CONCAT('com', 'pany_no');
SET @legacy_name_col = CONCAT('com', 'pany_name');
SET @legacy_short_name_col = CONCAT('com', 'pany_short_name');
SET @legacy_type_col = CONCAT('com', 'pany_type');
SET @legacy_status_col = CONCAT('com', 'pany_status');

SET @ddl = IF(@has_current_profile_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = @legacy_profile_id_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = 'crew_profile_id'
), CONCAT('ALTER TABLE `', @current_profile_table, '` RENAME COLUMN `', @legacy_profile_id_col, '` TO `crew_profile_id`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_current_profile_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = @legacy_no_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = 'crew_no'
), CONCAT('ALTER TABLE `', @current_profile_table, '` RENAME COLUMN `', @legacy_no_col, '` TO `crew_no`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_current_profile_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = @legacy_name_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = 'crew_name'
), CONCAT('ALTER TABLE `', @current_profile_table, '` RENAME COLUMN `', @legacy_name_col, '` TO `crew_name`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_current_profile_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = @legacy_short_name_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = 'crew_short_name'
), CONCAT('ALTER TABLE `', @current_profile_table, '` RENAME COLUMN `', @legacy_short_name_col, '` TO `crew_short_name`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_current_profile_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = @legacy_type_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = 'crew_type'
), CONCAT('ALTER TABLE `', @current_profile_table, '` RENAME COLUMN `', @legacy_type_col, '` TO `crew_type`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_current_profile_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = @legacy_status_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = 'crew_status'
), CONCAT('ALTER TABLE `', @current_profile_table, '` RENAME COLUMN `', @legacy_status_col, '` TO `crew_status`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_current_profile_table > 0,
  'ALTER TABLE `crew_profile` COMMENT = ''crew profiles''',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @legacy_crew_profile_col = CONCAT('com', 'pany_profile_id');
SET @legacy_crew_user_col = CONCAT('com', 'pany_user_id');
SET @legacy_confirmed_col = CONCAT('com', 'pany_confirmed');
SET @legacy_confirm_time_col = CONCAT('com', 'pany_confirm_time');

SET @has_recruit_post_table = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruit_post'
);
SET @ddl = IF(@has_recruit_post_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruit_post' AND COLUMN_NAME = @legacy_crew_profile_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruit_post' AND COLUMN_NAME = 'crew_profile_id'
), CONCAT('ALTER TABLE `recruit_post` RENAME COLUMN `', @legacy_crew_profile_col, '` TO `crew_profile_id`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_order_table = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order'
);
SET @ddl = IF(@has_order_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order' AND COLUMN_NAME = @legacy_crew_user_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order' AND COLUMN_NAME = 'crew_user_id'
), CONCAT('ALTER TABLE `cooperation_order` RENAME COLUMN `', @legacy_crew_user_col, '` TO `crew_user_id`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_order_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order' AND COLUMN_NAME = @legacy_crew_profile_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order' AND COLUMN_NAME = 'crew_profile_id'
), CONCAT('ALTER TABLE `cooperation_order` RENAME COLUMN `', @legacy_crew_profile_col, '` TO `crew_profile_id`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_order_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order' AND COLUMN_NAME = @legacy_confirmed_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order' AND COLUMN_NAME = 'crew_confirmed'
), CONCAT('ALTER TABLE `cooperation_order` RENAME COLUMN `', @legacy_confirmed_col, '` TO `crew_confirmed`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_order_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order' AND COLUMN_NAME = @legacy_confirm_time_col
) AND NOT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooperation_order' AND COLUMN_NAME = 'crew_confirm_time'
), CONCAT('ALTER TABLE `cooperation_order` RENAME COLUMN `', @legacy_confirm_time_col, '` TO `crew_confirm_time`'), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @legacy_json_crew_id = CONCAT('"', 'com', 'panyId', '"');
SET @legacy_json_profile_id = CONCAT('"', 'com', 'panyProfileId', '"');
SET @legacy_json_name = CONCAT('"', 'com', 'panyName', '"');
SET @current_json_crew_id = '"crewId"';
SET @current_json_profile_id = '"crewProfileId"';
SET @current_json_name = '"crewName"';

SET @ddl = IF(@has_current_profile_table > 0 AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @current_profile_table AND COLUMN_NAME = 'extended_field'
), CONCAT(
  'UPDATE `crew_profile` SET `extended_field` = REPLACE(REPLACE(REPLACE(CAST(`extended_field` AS CHAR), ',
  QUOTE(@legacy_json_crew_id), ', ', QUOTE(@current_json_crew_id), '), ',
  QUOTE(@legacy_json_profile_id), ', ', QUOTE(@current_json_profile_id), '), ',
  QUOTE(@legacy_json_name), ', ', QUOTE(@current_json_name), ') WHERE `extended_field` IS NOT NULL'
), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @legacy_snake_profile_id = CONCAT('com', 'pany_profile_id');
SET @legacy_camel_profile_id = CONCAT('com', 'panyProfileId');
SET @legacy_camel_name = CONCAT('com', 'panyName');
SET @legacy_camel_id = CONCAT('com', 'panyId');

SET @has_admin_log_table = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_operation_log'
);
SET @ddl = IF(@has_admin_log_table > 0, CONCAT(
  'UPDATE `admin_operation_log` SET ',
  '`before_snapshot_json` = CASE WHEN `before_snapshot_json` IS NULL THEN NULL ELSE REPLACE(REPLACE(REPLACE(REPLACE(CAST(`before_snapshot_json` AS CHAR), ', QUOTE(@legacy_snake_profile_id), ', ''crew_profile_id''), ', QUOTE(@legacy_camel_profile_id), ', ''crewProfileId''), ', QUOTE(@legacy_camel_name), ', ''crewName''), ', QUOTE(@legacy_camel_id), ', ''crewId'') END, ',
  '`after_snapshot_json` = CASE WHEN `after_snapshot_json` IS NULL THEN NULL ELSE REPLACE(REPLACE(REPLACE(REPLACE(CAST(`after_snapshot_json` AS CHAR), ', QUOTE(@legacy_snake_profile_id), ', ''crew_profile_id''), ', QUOTE(@legacy_camel_profile_id), ', ''crewProfileId''), ', QUOTE(@legacy_camel_name), ', ''crewName''), ', QUOTE(@legacy_camel_id), ', ''crewId'') END, ',
  '`extra_context_json` = CASE WHEN `extra_context_json` IS NULL THEN NULL ELSE REPLACE(REPLACE(REPLACE(REPLACE(CAST(`extra_context_json` AS CHAR), ', QUOTE(@legacy_snake_profile_id), ', ''crew_profile_id''), ', QUOTE(@legacy_camel_profile_id), ', ''crewProfileId''), ', QUOTE(@legacy_camel_name), ', ''crewName''), ', QUOTE(@legacy_camel_id), ', ''crewId'') END'
), 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
