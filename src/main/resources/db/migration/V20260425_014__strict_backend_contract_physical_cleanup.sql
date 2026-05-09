SET @has_preferred_tone = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'actor_share_preference'
    AND COLUMN_NAME = 'preferred_tone'
);

SET @ddl = IF(
  @has_preferred_tone > 0,
  'CREATE TABLE IF NOT EXISTS `zz_bak_20260425_014_share_pref_tone` AS SELECT `preference_id`, `share_card_id`, `preferred_tone`, NOW() AS `backed_up_at` FROM `actor_share_preference` WHERE `preferred_tone` IS NOT NULL AND TRIM(`preferred_tone`) <> ''''',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  @has_preferred_tone > 0,
  'ALTER TABLE `actor_share_preference` DROP COLUMN `preferred_tone`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_invite_record = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'invite_record'
);

SET @ddl = IF(
  @has_invite_record > 0,
  'CREATE TABLE IF NOT EXISTS `zz_bak_20260425_014_invite_record` LIKE `invite_record`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  @has_invite_record > 0,
  'INSERT IGNORE INTO `zz_bak_20260425_014_invite_record` SELECT * FROM `invite_record`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  @has_invite_record > 0,
  'DROP TABLE `invite_record`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @removed_recruit_menu_permission = CONCAT('menu.', 'recruit');

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_014_admin_role_removed_menu` AS
SELECT *
FROM `admin_role`
WHERE JSON_SEARCH(COALESCE(`menu_permissions_json`, JSON_ARRAY()), 'one', @removed_recruit_menu_permission) IS NOT NULL;

UPDATE `admin_role`
SET `menu_permissions_json` = JSON_REMOVE(
  COALESCE(`menu_permissions_json`, JSON_ARRAY()),
  JSON_UNQUOTE(JSON_SEARCH(COALESCE(`menu_permissions_json`, JSON_ARRAY()), 'one', @removed_recruit_menu_permission))
)
WHERE JSON_SEARCH(COALESCE(`menu_permissions_json`, JSON_ARRAY()), 'one', @removed_recruit_menu_permission) IS NOT NULL;

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_014_template_artifact_contract` AS
SELECT *
FROM `card_scene_template`
WHERE JSON_CONTAINS_PATH(`artifact_preset_json`, 'one', '$.shareCard')
   OR JSON_SEARCH(`artifact_preset_json`, 'one', 'shareCard') IS NOT NULL;

UPDATE `card_scene_template`
SET `artifact_preset_json` = JSON_REMOVE(
  JSON_SET(`artifact_preset_json`, '$.miniProgramCard', JSON_EXTRACT(`artifact_preset_json`, '$.shareCard')),
  '$.shareCard'
)
WHERE JSON_CONTAINS_PATH(`artifact_preset_json`, 'one', '$.shareCard');

CREATE TABLE IF NOT EXISTS `zz_bak_20260425_014_capability_benefit_contract` AS
SELECT *
FROM `capability_product`
WHERE JSON_SEARCH(`benefit_config_json`, 'one', 'shareCard') IS NOT NULL
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"benefits"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"items"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"capabilityMatrix"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"capabilityCode"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"abilityCode"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"benefitStatus"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"enabledStatus"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"activeStatus"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"isEnabled"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"pageScopes"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"artifactScopes"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"artifacts"%'
   OR CAST(`benefit_config_json` AS CHAR) LIKE '%"outputs"%';

UPDATE `capability_product`
SET `benefit_config_json` = REPLACE(CAST(`benefit_config_json` AS CHAR), '"shareCard"', '"miniProgramCard"')
WHERE JSON_SEARCH(`benefit_config_json`, 'one', 'shareCard') IS NOT NULL;
