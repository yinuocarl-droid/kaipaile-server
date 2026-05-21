-- AI profile-card covers are embedded in native detail pages; generated bitmap watermarks must stay disabled.

UPDATE `ai_image_provider_config`
SET `public_config_json` = JSON_SET(
    `public_config_json`,
    '$.watermark', JSON_EXTRACT('false', '$')
  )
WHERE `deleted` = 0
  AND JSON_CONTAINS_PATH(`public_config_json`, 'one', '$.watermark');

UPDATE `ai_image_provider_config`
SET `public_config_json` = JSON_SET(
    `public_config_json`,
    '$.promptRewrite', JSON_EXTRACT('false', '$')
  )
WHERE `provider_code` = 'tencent-hunyuan'
  AND `deleted` = 0;
