-- Align Tencent Hunyuan image provider with the official AI Art endpoint/actions.

UPDATE `ai_image_provider_config`
SET `public_config_json` = JSON_SET(
    `public_config_json`,
    '$.endpoint', 'https://aiart.tencentcloudapi.com',
    '$.model', 'hunyuan-image-3.0',
    '$.modelVersion', '2022-12-29',
    '$.region', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`public_config_json`, '$.region')), 'ap-guangzhou'),
    '$.resolution', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`public_config_json`, '$.resolution')), '720:1280'),
    '$.size', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`public_config_json`, '$.size')), '720:1280')
  )
WHERE `provider_code` = 'tencent-hunyuan'
  AND `deleted` = 0;
