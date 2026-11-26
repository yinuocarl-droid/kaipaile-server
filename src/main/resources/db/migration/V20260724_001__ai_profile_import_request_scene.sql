-- Add request-scene binding without rewriting the already-published governance migration.
ALTER TABLE `ai_profile_import_request_audit`
  ADD COLUMN `scene` VARCHAR(32) NULL AFTER `model_name`;

UPDATE `ai_profile_import_request_audit`
SET `scene` = 'legacy_unknown'
WHERE `scene` IS NULL;

ALTER TABLE `ai_profile_import_request_audit`
  MODIFY COLUMN `scene` VARCHAR(32) NOT NULL;
