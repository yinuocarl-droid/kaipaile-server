CREATE TABLE IF NOT EXISTS `zz_bak_20260425_012_pref_bad_artifact` LIKE `actor_share_preference`;

INSERT IGNORE INTO `zz_bak_20260425_012_pref_bad_artifact`
SELECT *
FROM `actor_share_preference`
WHERE `preferred_artifact` IS NULL
   OR TRIM(`preferred_artifact`) = ''
   OR `preferred_artifact` NOT IN ('miniProgramCard', 'poster');

DELETE FROM `actor_share_preference`
WHERE `preferred_artifact` IS NULL
   OR TRIM(`preferred_artifact`) = ''
   OR `preferred_artifact` NOT IN ('miniProgramCard', 'poster');

ALTER TABLE `actor_share_preference`
  MODIFY COLUMN `preferred_artifact` VARCHAR(32) NOT NULL COMMENT 'miniProgramCard, poster';

SET @has_preferred_artifact_check = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'actor_share_preference'
    AND CONSTRAINT_NAME = 'chk_actor_share_preference_preferred_artifact'
);
SET @ddl = IF(
  @has_preferred_artifact_check = 0,
  'ALTER TABLE `actor_share_preference` ADD CONSTRAINT `chk_actor_share_preference_preferred_artifact` CHECK (`preferred_artifact` IN (''miniProgramCard'', ''poster''))',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
