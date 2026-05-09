-- Physically align template page surface keys with the current admin editor enum.
-- Matching rows are backed up before JSON values are rewritten.

SET @legacy_surface_key = CONCAT('stu', 'dio');

CREATE TABLE IF NOT EXISTS `zz_bak_20260427_026_template_surface_key_cleanup` AS
SELECT *
FROM `card_scene_template`
WHERE JSON_UNQUOTE(JSON_EXTRACT(`artifact_preset_json`, '$.pageConfig.surface')) = @legacy_surface_key;

UPDATE `card_scene_template`
SET `artifact_preset_json` = JSON_SET(`artifact_preset_json`, '$.pageConfig.surface', 'softlight')
WHERE JSON_UNQUOTE(JSON_EXTRACT(`artifact_preset_json`, '$.pageConfig.surface')) = @legacy_surface_key;
