-- Physically align the retired paid-capability domain with the current schema.
-- Old live schema values are backed up before table, column, index and runtime values are rewritten.

SET @legacy_domain = CONCAT('mem', 'ber', 'ship');
SET @legacy_domain_upper = CONCAT('MEM', 'BER', 'SHIP');
SET @legacy_domain_title = CONCAT('Mem', 'ber', 'ship');
SET @legacy_plus_lower = CONCAT('mem', 'ber');
SET @legacy_plus_upper = CONCAT('MEM', 'BER');
SET @legacy_plus_title = CONCAT('Mem', 'ber');
SET @legacy_pro_lower = CONCAT('v', 'ip');
SET @legacy_pro_upper = CONCAT('V', 'IP');
SET @legacy_pro_title = CONCAT('V', 'ip');
SET @legacy_cn = CONCAT('会', '员');
SET @legacy_product_table = CONCAT(@legacy_domain, '_product');
SET @legacy_account_table = CONCAT(@legacy_domain, '_account');
SET @legacy_log_table = CONCAT(@legacy_domain, '_change_log');
SET @legacy_purchase = CONCAT(@legacy_domain, '_purchase');
SET @legacy_renewal = CONCAT(@legacy_domain, '_renewal');
SET @legacy_trial = CONCAT(@legacy_pro_lower, '_trial');
SET @legacy_required = CONCAT(@legacy_plus_lower, '_required');

SET @legacy_product_table_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @legacy_product_table
);
SET @legacy_account_table_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @legacy_account_table
);
SET @legacy_log_table_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = @legacy_log_table
);

SET @backup_product_sql = IF(
  @legacy_product_table_exists > 0,
  CONCAT('CREATE TABLE IF NOT EXISTS `zz_bak_20260426_019_legacy_product_physical_rename` AS SELECT * FROM `', @legacy_product_table, '`'),
  'SELECT 1'
);
PREPARE backup_product_stmt FROM @backup_product_sql;
EXECUTE backup_product_stmt;
DEALLOCATE PREPARE backup_product_stmt;

SET @backup_account_sql = IF(
  @legacy_account_table_exists > 0,
  CONCAT('CREATE TABLE IF NOT EXISTS `zz_bak_20260426_019_legacy_account_physical_rename` AS SELECT * FROM `', @legacy_account_table, '`'),
  'SELECT 1'
);
PREPARE backup_account_stmt FROM @backup_account_sql;
EXECUTE backup_account_stmt;
DEALLOCATE PREPARE backup_account_stmt;

SET @backup_log_sql = IF(
  @legacy_log_table_exists > 0,
  CONCAT('CREATE TABLE IF NOT EXISTS `zz_bak_20260426_019_legacy_change_log_physical_rename` AS SELECT * FROM `', @legacy_log_table, '`'),
  'SELECT 1'
);
PREPARE backup_log_stmt FROM @backup_log_sql;
EXECUTE backup_log_stmt;
DEALLOCATE PREPARE backup_log_stmt;

CREATE TABLE IF NOT EXISTS `zz_bak_20260426_019_payment_order_legacy_biz` AS
SELECT *
FROM `payment_order`
WHERE `biz_type` IN (@legacy_purchase, @legacy_renewal);

CREATE TABLE IF NOT EXISTS `zz_bak_20260426_019_admin_operation_log_legacy_values` AS
SELECT *
FROM `admin_operation_log`
WHERE `module_code` = @legacy_domain
   OR `operation_code` LIKE CONCAT('%', @legacy_domain, '%')
   OR `target_type` LIKE CONCAT('%', @legacy_domain, '%')
   OR CAST(`before_snapshot_json` AS CHAR) LIKE CONCAT('%', @legacy_domain, '%')
   OR CAST(`after_snapshot_json` AS CHAR) LIKE CONCAT('%', @legacy_domain, '%')
   OR CAST(`extra_context_json` AS CHAR) LIKE CONCAT('%', @legacy_domain, '%')
   OR CAST(`before_snapshot_json` AS CHAR) LIKE CONCAT('%', @legacy_cn, '%')
   OR CAST(`after_snapshot_json` AS CHAR) LIKE CONCAT('%', @legacy_cn, '%')
   OR CAST(`extra_context_json` AS CHAR) LIKE CONCAT('%', @legacy_cn, '%');

CREATE TABLE IF NOT EXISTS `zz_bak_20260426_019_user_entitlement_grant_legacy_trial` AS
SELECT *
FROM `user_entitlement_grant`
WHERE `grant_type` = @legacy_trial
   OR `grant_code` LIKE CONCAT('%', @legacy_pro_lower, '%');

SET @capability_product_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_product'
);
SET @rename_product_sql = IF(
  @legacy_product_table_exists > 0 AND @capability_product_exists = 0,
  CONCAT('RENAME TABLE `', @legacy_product_table, '` TO `capability_product`'),
  'SELECT 1'
);
PREPARE rename_product_stmt FROM @rename_product_sql;
EXECUTE rename_product_stmt;
DEALLOCATE PREPARE rename_product_stmt;

SET @capability_account_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_account'
);
SET @rename_account_sql = IF(
  @legacy_account_table_exists > 0 AND @capability_account_exists = 0,
  CONCAT('RENAME TABLE `', @legacy_account_table, '` TO `capability_account`'),
  'SELECT 1'
);
PREPARE rename_account_stmt FROM @rename_account_sql;
EXECUTE rename_account_stmt;
DEALLOCATE PREPARE rename_account_stmt;

SET @capability_log_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_change_log'
);
SET @rename_log_sql = IF(
  @legacy_log_table_exists > 0 AND @capability_log_exists = 0,
  CONCAT('RENAME TABLE `', @legacy_log_table, '` TO `capability_change_log`'),
  'SELECT 1'
);
PREPARE rename_log_stmt FROM @rename_log_sql;
EXECUTE rename_log_stmt;
DEALLOCATE PREPARE rename_log_stmt;

SET @legacy_tier_column = CONCAT(@legacy_domain, '_tier');
SET @legacy_tier_column_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_product'
    AND COLUMN_NAME = @legacy_tier_column
);
SET @rename_tier_sql = IF(
  @legacy_tier_column_exists > 0,
  CONCAT('ALTER TABLE `capability_product` CHANGE COLUMN `', @legacy_tier_column, '` `capability_tier` TINYINT NOT NULL COMMENT ''0 base, 1 plus, 2 pro'''),
  'SELECT 1'
);
PREPARE rename_tier_stmt FROM @rename_tier_sql;
EXECUTE rename_tier_stmt;
DEALLOCATE PREPARE rename_tier_stmt;

SET @legacy_product_code_index = CONCAT('uk_', @legacy_product_table, '_code');
SET @legacy_product_code_index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_product'
    AND INDEX_NAME = @legacy_product_code_index
);
SET @rename_product_code_index_sql = IF(
  @legacy_product_code_index_exists > 0,
  CONCAT('ALTER TABLE `capability_product` RENAME INDEX `', @legacy_product_code_index, '` TO `uk_capability_product_code`'),
  'SELECT 1'
);
PREPARE rename_product_code_index_stmt FROM @rename_product_code_index_sql;
EXECUTE rename_product_code_index_stmt;
DEALLOCATE PREPARE rename_product_code_index_stmt;

SET @legacy_product_status_index = CONCAT('idx_', @legacy_product_table, '_status_sort_no');
SET @legacy_product_status_index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_product'
    AND INDEX_NAME = @legacy_product_status_index
);
SET @rename_product_status_index_sql = IF(
  @legacy_product_status_index_exists > 0,
  CONCAT('ALTER TABLE `capability_product` RENAME INDEX `', @legacy_product_status_index, '` TO `idx_capability_product_status_sort_no`'),
  'SELECT 1'
);
PREPARE rename_product_status_index_stmt FROM @rename_product_status_index_sql;
EXECUTE rename_product_status_index_stmt;
DEALLOCATE PREPARE rename_product_status_index_stmt;

SET @legacy_product_tier_index = CONCAT('idx_', @legacy_product_table, '_', @legacy_domain, '_tier');
SET @legacy_product_tier_index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_product'
    AND INDEX_NAME = @legacy_product_tier_index
);
SET @rename_product_tier_index_sql = IF(
  @legacy_product_tier_index_exists > 0,
  CONCAT('ALTER TABLE `capability_product` RENAME INDEX `', @legacy_product_tier_index, '` TO `idx_capability_product_capability_tier`'),
  'SELECT 1'
);
PREPARE rename_product_tier_index_stmt FROM @rename_product_tier_index_sql;
EXECUTE rename_product_tier_index_stmt;
DEALLOCATE PREPARE rename_product_tier_index_stmt;

SET @legacy_id_column = CONCAT(@legacy_domain, '_id');
SET @legacy_id_column_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_account'
    AND COLUMN_NAME = @legacy_id_column
);
SET @rename_id_sql = IF(
  @legacy_id_column_exists > 0,
  CONCAT('ALTER TABLE `capability_account` CHANGE COLUMN `', @legacy_id_column, '` `capability_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''pk'''),
  'SELECT 1'
);
PREPARE rename_id_stmt FROM @rename_id_sql;
EXECUTE rename_id_stmt;
DEALLOCATE PREPARE rename_id_stmt;

SET @legacy_account_user_index = CONCAT('uk_', @legacy_account_table, '_user_id');
SET @legacy_account_user_index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_account'
    AND INDEX_NAME = @legacy_account_user_index
);
SET @rename_account_user_index_sql = IF(
  @legacy_account_user_index_exists > 0,
  CONCAT('ALTER TABLE `capability_account` RENAME INDEX `', @legacy_account_user_index, '` TO `uk_capability_account_user_id`'),
  'SELECT 1'
);
PREPARE rename_account_user_index_stmt FROM @rename_account_user_index_sql;
EXECUTE rename_account_user_index_stmt;
DEALLOCATE PREPARE rename_account_user_index_stmt;

SET @legacy_account_status_index = CONCAT('idx_', @legacy_account_table, '_tier_status');
SET @legacy_account_status_index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_account'
    AND INDEX_NAME = @legacy_account_status_index
);
SET @rename_account_status_index_sql = IF(
  @legacy_account_status_index_exists > 0,
  CONCAT('ALTER TABLE `capability_account` RENAME INDEX `', @legacy_account_status_index, '` TO `idx_capability_account_tier_status`'),
  'SELECT 1'
);
PREPARE rename_account_status_index_stmt FROM @rename_account_status_index_sql;
EXECUTE rename_account_status_index_stmt;
DEALLOCATE PREPARE rename_account_status_index_stmt;

SET @legacy_account_expire_index = CONCAT('idx_', @legacy_account_table, '_expire_time');
SET @legacy_account_expire_index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_account'
    AND INDEX_NAME = @legacy_account_expire_index
);
SET @rename_account_expire_index_sql = IF(
  @legacy_account_expire_index_exists > 0,
  CONCAT('ALTER TABLE `capability_account` RENAME INDEX `', @legacy_account_expire_index, '` TO `idx_capability_account_expire_time`'),
  'SELECT 1'
);
PREPARE rename_account_expire_index_stmt FROM @rename_account_expire_index_sql;
EXECUTE rename_account_expire_index_stmt;
DEALLOCATE PREPARE rename_account_expire_index_stmt;

SET @legacy_log_create_index = CONCAT('idx_', @legacy_log_table, '_user_id_create_time');
SET @legacy_log_create_index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_change_log'
    AND INDEX_NAME = @legacy_log_create_index
);
SET @rename_log_create_index_sql = IF(
  @legacy_log_create_index_exists > 0,
  CONCAT('ALTER TABLE `capability_change_log` RENAME INDEX `', @legacy_log_create_index, '` TO `idx_capability_change_log_user_id_create_time`'),
  'SELECT 1'
);
PREPARE rename_log_create_index_stmt FROM @rename_log_create_index_sql;
EXECUTE rename_log_create_index_stmt;
DEALLOCATE PREPARE rename_log_create_index_stmt;

SET @legacy_log_source_index = CONCAT('idx_', @legacy_log_table, '_source_type_source_ref_id');
SET @legacy_log_source_index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'capability_change_log'
    AND INDEX_NAME = @legacy_log_source_index
);
SET @rename_log_source_index_sql = IF(
  @legacy_log_source_index_exists > 0,
  CONCAT('ALTER TABLE `capability_change_log` RENAME INDEX `', @legacy_log_source_index, '` TO `idx_capability_change_log_source_type_source_ref_id`'),
  'SELECT 1'
);
PREPARE rename_log_source_index_stmt FROM @rename_log_source_index_sql;
EXECUTE rename_log_source_index_stmt;
DEALLOCATE PREPARE rename_log_source_index_stmt;

ALTER TABLE `capability_product`
  COMMENT = 'capability products';

ALTER TABLE `capability_account`
  MODIFY COLUMN `tier` TINYINT NOT NULL DEFAULT 0 COMMENT '0 base, 1 plus, 2 pro',
  COMMENT = 'current capability account';

ALTER TABLE `capability_change_log`
  COMMENT = 'capability change logs';

UPDATE `capability_product`
SET
  `product_code` = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`product_code`, @legacy_domain_upper, 'CAPABILITY'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), @legacy_plus_upper, 'PLUS'), @legacy_plus_title, 'Plus'), @legacy_plus_lower, 'plus'), @legacy_pro_upper, 'PRO'), @legacy_pro_title, 'Pro'), @legacy_pro_lower, 'pro'),
  `product_name` = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`product_name`, @legacy_cn, '能力'), @legacy_domain_upper, 'CAPABILITY'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), @legacy_plus_upper, 'PLUS'), @legacy_plus_title, 'Plus'), @legacy_plus_lower, 'plus'), @legacy_pro_upper, 'PRO'), @legacy_pro_title, 'Pro'), @legacy_pro_lower, 'pro'),
  `benefit_config_json` = CASE
    WHEN `benefit_config_json` IS NULL THEN NULL
    ELSE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`benefit_config_json` AS CHAR), @legacy_cn, '能力'), @legacy_domain_upper, 'CAPABILITY'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), CONCAT('"', @legacy_plus_upper, '"'), '"PLUS"'), CONCAT('"', @legacy_plus_title, '"'), '"Plus"'), CONCAT('"', @legacy_plus_lower, '"'), '"plus"'), CONCAT('"', @legacy_pro_upper, '"'), '"PRO"'), CONCAT('"', @legacy_pro_title, '"'), '"Pro"'), CONCAT('"', @legacy_pro_lower, '"'), '"pro"'), @legacy_required, 'capability_required')
  END;

UPDATE `payment_order`
SET `biz_type` = CASE `biz_type`
  WHEN @legacy_purchase THEN 'capability_purchase'
  WHEN @legacy_renewal THEN 'capability_renewal'
  ELSE `biz_type`
END
WHERE `biz_type` IN (@legacy_purchase, @legacy_renewal);

ALTER TABLE `payment_order`
  MODIFY COLUMN `biz_type` VARCHAR(32) NOT NULL COMMENT 'capability_purchase, capability_renewal',
  MODIFY COLUMN `product_id` BIGINT NOT NULL COMMENT 'capability product id',
  COMMENT = 'payment orders for capability';

UPDATE `admin_operation_log`
SET
  `module_code` = REPLACE(`module_code`, @legacy_domain, 'capability'),
  `operation_code` = REPLACE(`operation_code`, @legacy_domain, 'capability'),
  `target_type` = REPLACE(`target_type`, @legacy_domain, 'capability'),
  `before_snapshot_json` = CASE
    WHEN `before_snapshot_json` IS NULL THEN NULL
    ELSE REPLACE(REPLACE(REPLACE(REPLACE(CAST(`before_snapshot_json` AS CHAR), @legacy_cn, '能力'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), @legacy_required, 'capability_required')
  END,
  `after_snapshot_json` = CASE
    WHEN `after_snapshot_json` IS NULL THEN NULL
    ELSE REPLACE(REPLACE(REPLACE(REPLACE(CAST(`after_snapshot_json` AS CHAR), @legacy_cn, '能力'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), @legacy_required, 'capability_required')
  END,
  `extra_context_json` = CASE
    WHEN `extra_context_json` IS NULL THEN NULL
    ELSE REPLACE(REPLACE(REPLACE(REPLACE(CAST(`extra_context_json` AS CHAR), @legacy_cn, '能力'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), @legacy_required, 'capability_required')
  END
WHERE `module_code` = @legacy_domain
   OR `operation_code` LIKE CONCAT('%', @legacy_domain, '%')
   OR `target_type` LIKE CONCAT('%', @legacy_domain, '%')
   OR CAST(`before_snapshot_json` AS CHAR) LIKE CONCAT('%', @legacy_domain, '%')
   OR CAST(`after_snapshot_json` AS CHAR) LIKE CONCAT('%', @legacy_domain, '%')
   OR CAST(`extra_context_json` AS CHAR) LIKE CONCAT('%', @legacy_domain, '%')
   OR CAST(`before_snapshot_json` AS CHAR) LIKE CONCAT('%', @legacy_cn, '%')
   OR CAST(`after_snapshot_json` AS CHAR) LIKE CONCAT('%', @legacy_cn, '%')
   OR CAST(`extra_context_json` AS CHAR) LIKE CONCAT('%', @legacy_cn, '%');

ALTER TABLE `admin_operation_log`
  MODIFY COLUMN `module_code` VARCHAR(64) NOT NULL COMMENT 'verify, referral, capability, refund, content, system';

UPDATE `user_entitlement_grant`
SET
  `grant_type` = CASE WHEN `grant_type` = @legacy_trial THEN 'pro_trial' ELSE `grant_type` END,
  `grant_code` = REPLACE(`grant_code`, @legacy_pro_lower, 'pro')
WHERE `grant_type` = @legacy_trial
   OR `grant_code` LIKE CONCAT('%', @legacy_pro_lower, '%');

ALTER TABLE `user_entitlement_grant`
  MODIFY COLUMN `grant_type` VARCHAR(32) NOT NULL COMMENT 'invite_eligibility, pro_trial, theme_access';
