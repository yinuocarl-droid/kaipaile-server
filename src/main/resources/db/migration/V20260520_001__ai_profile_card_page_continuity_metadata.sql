ALTER TABLE `actor_ai_profile_card_page`
  ADD COLUMN `prompt_locale` VARCHAR(16) DEFAULT NULL COMMENT 'page prompt primary locale, e.g. zh-CN' AFTER `negative_prompt`,
  ADD COLUMN `continuity_mode` VARCHAR(32) DEFAULT NULL COMMENT 'identity_reference,tail_reference,text_only' AFTER `prompt_locale`,
  ADD COLUMN `continuity_reference_url` VARCHAR(1024) DEFAULT NULL COMMENT 'identity source image or previous page tail reference band url' AFTER `continuity_mode`,
  ADD COLUMN `continuity_reference_source_page_type` VARCHAR(32) DEFAULT NULL COMMENT 'source page type for tail reference band' AFTER `continuity_reference_url`,
  ADD COLUMN `continuity_reference_source_page_no` INT DEFAULT NULL COMMENT 'source page no for tail reference band' AFTER `continuity_reference_source_page_type`,
  ADD COLUMN `continuity_band_ratio` DECIMAL(5,4) DEFAULT NULL COMMENT 'tail reference band crop height ratio' AFTER `continuity_reference_source_page_no`,
  ADD COLUMN `continuity_band_rect` VARCHAR(128) DEFAULT NULL COMMENT 'tail reference crop rectangle on source image' AFTER `continuity_band_ratio`,
  ADD COLUMN `continuity_failure_reason` VARCHAR(1024) DEFAULT NULL COMMENT 'continuity reference degraded or crop failure reason' AFTER `continuity_band_rect`;
