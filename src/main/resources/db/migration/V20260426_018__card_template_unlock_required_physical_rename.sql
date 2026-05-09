-- Physically align the template unlock flag with the current schema.
-- Backup is created only when the retired column is still present.

SET @legacy_unlock_column = CONCAT('mem', 'ber', 'ship', '_required');
SET @legacy_unlock_column_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'card_scene_template'
    AND COLUMN_NAME = @legacy_unlock_column
);

SET @backup_unlock_sql = IF(
  @legacy_unlock_column_exists > 0,
  CONCAT(
    'CREATE TABLE IF NOT EXISTS `zz_bak_20260426_018_card_template_unlock_required_rename` AS ',
    'SELECT `template_id`, `template_code`, `template_scene_code`, `',
    @legacy_unlock_column,
    '`, NOW() AS `backup_time` FROM `card_scene_template`'
  ),
  'SELECT 1'
);
PREPARE backup_unlock_stmt FROM @backup_unlock_sql;
EXECUTE backup_unlock_stmt;
DEALLOCATE PREPARE backup_unlock_stmt;

SET @rename_unlock_sql = IF(
  @legacy_unlock_column_exists > 0,
  CONCAT(
    'ALTER TABLE `card_scene_template` CHANGE COLUMN `',
    @legacy_unlock_column,
    '` `unlock_required` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 public, 1 unlock required'''
  ),
  'SELECT 1'
);
PREPARE rename_unlock_stmt FROM @rename_unlock_sql;
EXECUTE rename_unlock_stmt;
DEALLOCATE PREPARE rename_unlock_stmt;
