ALTER TABLE `identity_verification`
  ADD COLUMN `verify_provider` VARCHAR(32) DEFAULT NULL COMMENT 'manual, tencent_faceid' AFTER `snapshot_profile_completion`,
  ADD COLUMN `provider_result_code` VARCHAR(16) DEFAULT NULL COMMENT 'external provider business result code' AFTER `verify_provider`,
  ADD COLUMN `provider_description` VARCHAR(255) DEFAULT NULL COMMENT 'external provider business result description' AFTER `provider_result_code`,
  ADD COLUMN `provider_request_id` VARCHAR(64) DEFAULT NULL COMMENT 'external provider request id' AFTER `provider_description`,
  ADD KEY `idx_identity_verification_provider_request_id` (`provider_request_id`);
