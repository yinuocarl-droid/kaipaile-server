ALTER TABLE `identity_verification`
  ADD COLUMN `id_card_no_masked` VARCHAR(32) DEFAULT NULL COMMENT 'masked id card number' AFTER `id_card_no_cipher`,
  ADD COLUMN `provider_code` VARCHAR(32) DEFAULT NULL COMMENT 'manual/tencent' AFTER `snapshot_profile_completion`,
  ADD COLUMN `provider_request_id` VARCHAR(128) DEFAULT NULL COMMENT 'provider request id' AFTER `provider_code`,
  ADD COLUMN `provider_result_code` VARCHAR(64) DEFAULT NULL COMMENT 'provider result code' AFTER `provider_request_id`,
  ADD COLUMN `provider_result_message` VARCHAR(255) DEFAULT NULL COMMENT 'provider result message' AFTER `provider_result_code`,
  ADD COLUMN `provider_verified_at` DATETIME DEFAULT NULL COMMENT 'provider verification time' AFTER `provider_result_message`,
  ADD KEY `idx_identity_verification_provider_code` (`provider_code`),
  ADD KEY `idx_identity_verification_provider_verified_at` (`provider_verified_at`);

UPDATE `identity_verification`
SET `id_card_no_masked` = `id_card_no_cipher`
WHERE `id_card_no_masked` IS NULL
  AND `id_card_no_cipher` IS NOT NULL;
