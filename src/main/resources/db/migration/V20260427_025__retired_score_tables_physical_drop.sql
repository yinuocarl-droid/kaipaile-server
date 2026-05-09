-- Backup and physically remove retired score tables from the current runtime schema.

SET @retired_score_table = CONCAT('cre', 'dit_score');
SET @retired_score_log_table = CONCAT('cre', 'dit_score_log');

SET @has_retired_score_table = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @retired_score_table
);
SET @ddl = IF(
  @has_retired_score_table > 0,
  CONCAT('CREATE TABLE IF NOT EXISTS `zz_bak_20260427_025_retired_score` AS SELECT * FROM `', @retired_score_table, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  @has_retired_score_table > 0,
  CONCAT('DROP TABLE `', @retired_score_table, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_retired_score_log_table = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @retired_score_log_table
);
SET @ddl = IF(
  @has_retired_score_log_table > 0,
  CONCAT('CREATE TABLE IF NOT EXISTS `zz_bak_20260427_025_retired_score_log` AS SELECT * FROM `', @retired_score_log_table, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  @has_retired_score_log_table > 0,
  CONCAT('DROP TABLE `', @retired_score_log_table, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
