-- Retire the obsolete report domain physically. Backup first, then drop runtime objects.

SET @retired_report_table = CONCAT('for', 'tune_report');
SET @retired_theme_column = CONCAT('enable_for', 'tune_theme');

SET @has_retired_report_table = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @retired_report_table
);
SET @ddl = IF(
  @has_retired_report_table > 0,
  CONCAT('CREATE TABLE IF NOT EXISTS `zz_bak_20260426_022_retired_report` AS SELECT * FROM `', @retired_report_table, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_retired_theme_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'actor_share_preference'
    AND COLUMN_NAME = @retired_theme_column
);
SET @ddl = IF(
  @has_retired_theme_column > 0,
  CONCAT('CREATE TABLE IF NOT EXISTS `zz_bak_20260426_022_actor_share_preference_retired_theme` AS SELECT `preference_id`, `share_card_id`, `preferred_artifact`, `', @retired_theme_column, '`, `create_time`, `last_update`, `deleted` FROM `actor_share_preference`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  @has_retired_theme_column > 0,
  CONCAT('ALTER TABLE `actor_share_preference` DROP COLUMN `', @retired_theme_column, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  @has_retired_report_table > 0,
  CONCAT('DROP TABLE `', @retired_report_table, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
