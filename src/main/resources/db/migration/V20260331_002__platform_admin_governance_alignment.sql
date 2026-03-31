-- Governance alignment for platform admin baseline.
-- Apply after V20260331_001__platform_admin_baseline.sql.

ALTER TABLE `admin_role`
  ADD COLUMN `menu_permissions_json` JSON DEFAULT NULL COMMENT 'menu permission codes' AFTER `remark`,
  ADD COLUMN `page_permissions_json` JSON DEFAULT NULL COMMENT 'page permission codes' AFTER `menu_permissions_json`,
  ADD COLUMN `action_permissions_json` JSON DEFAULT NULL COMMENT 'action permission codes' AFTER `page_permissions_json`;

ALTER TABLE `template_publish_log`
  ADD COLUMN `target_type` VARCHAR(32) NOT NULL DEFAULT 'template' COMMENT 'template, theme_token, share_artifact' AFTER `template_id`,
  ADD COLUMN `target_code` VARCHAR(64) DEFAULT NULL COMMENT 'template code, theme code or artifact type' AFTER `target_type`,
  ADD COLUMN `draft_version` VARCHAR(32) DEFAULT NULL COMMENT 'draft version when publishing' AFTER `publish_version`,
  ADD COLUMN `source_version` VARCHAR(32) DEFAULT NULL COMMENT 'source version before rollback' AFTER `draft_version`,
  ADD COLUMN `target_version` VARCHAR(32) DEFAULT NULL COMMENT 'target version after rollback' AFTER `source_version`,
  ADD COLUMN `diff_summary_json` JSON DEFAULT NULL COMMENT 'diff summary json' AFTER `publish_note`,
  ADD INDEX `idx_template_publish_log_target_type_published_at` (`target_type`, `published_at`);
