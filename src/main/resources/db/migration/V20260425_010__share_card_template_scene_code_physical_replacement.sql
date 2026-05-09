SET @retired_template_scene_column = CONCAT('scene', '_key');
SET @retired_template_scene_index = CONCAT('idx_card_scene_template_', @retired_template_scene_column, '_status');
SET @retired_template_scene_code_to_replace = CONCAT('gen', 'eral');

SET @has_retired_template_scene_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'card_scene_template'
    AND COLUMN_NAME = @retired_template_scene_column
);

SET @has_template_scene_code_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'card_scene_template'
    AND COLUMN_NAME = 'template_scene_code'
);

SET @ddl = IF(
  @has_template_scene_code_column = 0,
  'ALTER TABLE `card_scene_template` ADD COLUMN `template_scene_code` VARCHAR(32) NULL COMMENT ''classic, costume, urban, commercial, artistic'' AFTER `template_code`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dml = IF(
  @has_retired_template_scene_column = 1,
  CONCAT(
    'UPDATE `card_scene_template` SET `template_scene_code` = CASE ',
    'WHEN `', @retired_template_scene_column, '` = ''', @retired_template_scene_code_to_replace, ''' THEN ''classic'' ',
    'ELSE TRIM(`', @retired_template_scene_column, '`) END ',
    'WHERE `template_scene_code` IS NULL OR TRIM(`template_scene_code`) = '''' OR `template_scene_code` = ''', @retired_template_scene_code_to_replace, ''''
  ),
  'DO 0'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dml = CONCAT(
  'UPDATE `card_scene_template` SET `template_scene_code` = ''classic'' ',
  'WHERE `template_scene_code` = ''', @retired_template_scene_code_to_replace, ''''
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zz_backup_20260425_010_card_scene_template_invalid_scene_code` AS
SELECT *
  FROM `card_scene_template`
 WHERE 1 = 0;
INSERT IGNORE INTO `zz_backup_20260425_010_card_scene_template_invalid_scene_code`
SELECT *
  FROM `card_scene_template`
 WHERE `template_scene_code` IS NULL
    OR TRIM(`template_scene_code`) = ''
    OR `template_scene_code` NOT IN ('classic', 'urban', 'costume', 'commercial', 'artistic');

DELETE FROM `card_scene_template`
 WHERE `template_scene_code` IS NULL
    OR TRIM(`template_scene_code`) = ''
    OR `template_scene_code` NOT IN ('classic', 'urban', 'costume', 'commercial', 'artistic');

SET @ddl = 'ALTER TABLE `card_scene_template` MODIFY COLUMN `template_scene_code` VARCHAR(32) NOT NULL COMMENT ''classic, costume, urban, commercial, artistic''';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_template_scene_code_check = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'card_scene_template'
    AND CONSTRAINT_NAME = 'chk_card_scene_template_template_scene_code'
);

SET @ddl = IF(
  @has_template_scene_code_check = 0,
  'ALTER TABLE `card_scene_template` ADD CONSTRAINT `chk_card_scene_template_template_scene_code` CHECK (`template_scene_code` IN (''classic'', ''urban'', ''costume'', ''commercial'', ''artistic''))',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_old_scene_index = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'card_scene_template'
    AND INDEX_NAME = @retired_template_scene_index
);

SET @ddl = IF(
  @has_old_scene_index > 0,
  CONCAT('ALTER TABLE `card_scene_template` DROP INDEX `', @retired_template_scene_index, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_retired_template_scene_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'card_scene_template'
    AND COLUMN_NAME = @retired_template_scene_column
);

SET @ddl = IF(
  @has_retired_template_scene_column = 1,
  CONCAT('ALTER TABLE `card_scene_template` DROP COLUMN `', @retired_template_scene_column, '`'),
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_template_scene_code_index = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'card_scene_template'
    AND INDEX_NAME = 'idx_card_scene_template_template_scene_code_status'
);

SET @ddl = IF(
  @has_template_scene_code_index = 0,
  'ALTER TABLE `card_scene_template` ADD INDEX `idx_card_scene_template_template_scene_code_status` (`template_scene_code`, `status`)',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
