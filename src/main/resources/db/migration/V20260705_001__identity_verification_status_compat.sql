SET @has_id_card_no_masked_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'identity_verification'
    AND COLUMN_NAME = 'id_card_no_masked'
);

SET @ddl = IF(
  @has_id_card_no_masked_column = 0,
  'ALTER TABLE `identity_verification` ADD COLUMN `id_card_no_masked` VARCHAR(32) DEFAULT NULL COMMENT ''masked id card number'' AFTER `id_card_no_cipher`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_provider_code_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'identity_verification'
    AND COLUMN_NAME = 'provider_code'
);

SET @ddl = IF(
  @has_provider_code_column = 0,
  'ALTER TABLE `identity_verification` ADD COLUMN `provider_code` VARCHAR(32) DEFAULT NULL COMMENT ''manual/tencent'' AFTER `snapshot_profile_completion`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_provider_request_id_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'identity_verification'
    AND COLUMN_NAME = 'provider_request_id'
);

SET @ddl = IF(
  @has_provider_request_id_column = 0,
  'ALTER TABLE `identity_verification` ADD COLUMN `provider_request_id` VARCHAR(128) DEFAULT NULL COMMENT ''provider request id'' AFTER `provider_code`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_provider_result_code_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'identity_verification'
    AND COLUMN_NAME = 'provider_result_code'
);

SET @ddl = IF(
  @has_provider_result_code_column = 0,
  'ALTER TABLE `identity_verification` ADD COLUMN `provider_result_code` VARCHAR(64) DEFAULT NULL COMMENT ''provider result code'' AFTER `provider_request_id`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_provider_result_message_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'identity_verification'
    AND COLUMN_NAME = 'provider_result_message'
);

SET @ddl = IF(
  @has_provider_result_message_column = 0,
  'ALTER TABLE `identity_verification` ADD COLUMN `provider_result_message` VARCHAR(255) DEFAULT NULL COMMENT ''provider result message'' AFTER `provider_result_code`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_provider_verified_at_column = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'identity_verification'
    AND COLUMN_NAME = 'provider_verified_at'
);

SET @ddl = IF(
  @has_provider_verified_at_column = 0,
  'ALTER TABLE `identity_verification` ADD COLUMN `provider_verified_at` DATETIME DEFAULT NULL COMMENT ''provider verification time'' AFTER `provider_result_message`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `identity_verification`
SET `id_card_no_masked` = `id_card_no_cipher`
WHERE `id_card_no_masked` IS NULL
  AND `id_card_no_cipher` IS NOT NULL;

SET @has_identity_verification_provider_code_index = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'identity_verification'
    AND INDEX_NAME = 'idx_identity_verification_provider_code'
);

SET @ddl = IF(
  @has_identity_verification_provider_code_index = 0,
  'ALTER TABLE `identity_verification` ADD INDEX `idx_identity_verification_provider_code` (`provider_code`)',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_identity_verification_provider_verified_at_index = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'identity_verification'
    AND INDEX_NAME = 'idx_identity_verification_provider_verified_at'
);

SET @ddl = IF(
  @has_identity_verification_provider_verified_at_index = 0,
  'ALTER TABLE `identity_verification` ADD INDEX `idx_identity_verification_provider_verified_at` (`provider_verified_at`)',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
