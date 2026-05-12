-- AI profile-card image provider dynamic admin configuration.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS `ai_image_provider_config` (
  `config_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
  `provider_code` VARCHAR(64) NOT NULL COMMENT 'provider code',
  `display_name` VARCHAR(128) NOT NULL COMMENT 'provider display name',
  `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '1 enabled, 0 disabled',
  `active` TINYINT NOT NULL DEFAULT 0 COMMENT '1 current runtime provider',
  `priority` INT NOT NULL DEFAULT 100 COMMENT 'display and fallback priority',
  `public_config_json` JSON NOT NULL COMMENT 'non-secret provider config',
  `secret_config_ciphertext` TEXT DEFAULT NULL COMMENT 'encrypted secret json envelope',
  `secret_mask_json` JSON DEFAULT NULL COMMENT 'masked secret display json',
  `secret_updated_by` BIGINT DEFAULT NULL COMMENT 'last secret updater admin id',
  `secret_updated_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'last secret updater admin name',
  `secret_updated_at` DATETIME DEFAULT NULL COMMENT 'last secret update time',
  `last_test_status` VARCHAR(32) DEFAULT NULL COMMENT 'success or failed',
  `last_test_message` VARCHAR(512) DEFAULT NULL COMMENT 'last provider test message',
  `last_test_at` DATETIME DEFAULT NULL COMMENT 'last provider test time',
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `rid` VARCHAR(64) DEFAULT NULL,
  `create_user_id` BIGINT DEFAULT NULL,
  `create_user_name` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` BIGINT DEFAULT NULL,
  `update_user_name` VARCHAR(64) DEFAULT '',
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_ai_image_provider_config_code_deleted` (`provider_code`, `deleted`),
  KEY `idx_ai_image_provider_config_active_enabled` (`active`, `enabled`, `priority`),
  KEY `idx_ai_image_provider_config_enabled_priority` (`enabled`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI image provider admin config';

CREATE TABLE IF NOT EXISTS `ai_image_provider_config_audit` (
  `audit_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
  `config_id` BIGINT NOT NULL COMMENT 'provider config id',
  `provider_code` VARCHAR(64) NOT NULL COMMENT 'provider code',
  `action_code` VARCHAR(64) NOT NULL COMMENT 'public_config_update,secret_update,secret_reveal,activate,test',
  `before_public_config_json` JSON DEFAULT NULL COMMENT 'masked before public config',
  `after_public_config_json` JSON DEFAULT NULL COMMENT 'masked after public config',
  `before_secret_mask_json` JSON DEFAULT NULL COMMENT 'before masked secret fields',
  `after_secret_mask_json` JSON DEFAULT NULL COMMENT 'after masked secret fields',
  `operator_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'admin operator id',
  `operator_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'admin operator name',
  `result_status` VARCHAR(32) NOT NULL DEFAULT 'success' COMMENT 'success or failed',
  `message` VARCHAR(512) DEFAULT NULL COMMENT 'sanitized audit message',
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `rid` VARCHAR(64) DEFAULT NULL,
  `create_user_id` BIGINT DEFAULT NULL,
  `create_user_name` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` BIGINT DEFAULT NULL,
  `update_user_name` VARCHAR(64) DEFAULT '',
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  KEY `idx_ai_image_provider_config_audit_config_time` (`config_id`, `create_time`),
  KEY `idx_ai_image_provider_config_audit_provider_action` (`provider_code`, `action_code`, `create_time`),
  KEY `idx_ai_image_provider_config_audit_operator` (`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI image provider admin config audit';

INSERT INTO `ai_image_provider_config` (`provider_code`, `display_name`, `enabled`, `active`, `priority`, `public_config_json`)
VALUES
  ('kplyyk', 'KPLYYK 管理 API', 1, 1, 10, JSON_OBJECT(
    'endpoint', 'http://kplyyk.com/v0/management/image-generation/test',
    'model', 'gpt-image-2',
    'size', '2160x3840',
    'quality', 'high',
    'count', 1,
    'authHeader', 'Authorization',
    'connectTimeoutMs', 10000,
    'readTimeoutMs', 120000,
    'pollIntervalMs', 1500,
    'maxPollAttempts', 400
  )),
  ('volc-seedream', '火山/豆包 Seedream', 0, 0, 20, JSON_OBJECT(
    'endpoint', '',
    'region', 'cn-beijing',
    'model', 'doubao-seedream-4.0',
    'modelVersion', '',
    'size', '2160x3840',
    'responseFormat', 'url',
    'count', 1,
    'watermark', true,
    'connectTimeoutMs', 10000,
    'readTimeoutMs', 120000,
    'pollIntervalMs', 1500,
    'maxPollAttempts', 240
  )),
  ('aliyun-qwen-image', '阿里云百炼 Qwen Image', 0, 0, 30, JSON_OBJECT(
    'endpoint', 'https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation',
    'region', 'cn-beijing',
    'model', 'qwen-image-edit',
    'size', '1024*1536',
    'responseFormat', 'url',
    'count', 1,
    'watermark', true,
    'connectTimeoutMs', 10000,
    'readTimeoutMs', 120000,
    'pollIntervalMs', 1500,
    'maxPollAttempts', 240
  )),
  ('aliyun-wanxiang', '阿里云通义万相', 0, 0, 40, JSON_OBJECT(
    'endpoint', 'https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation',
    'region', 'cn-beijing',
    'model', 'wan2.7-image-pro',
    'size', '1024*1536',
    'responseFormat', 'url',
    'count', 1,
    'watermark', true,
    'connectTimeoutMs', 10000,
    'readTimeoutMs', 120000,
    'pollIntervalMs', 1500,
    'maxPollAttempts', 240
  )),
  ('tencent-hunyuan', '腾讯混元生图', 0, 0, 50, JSON_OBJECT(
    'endpoint', 'https://hunyuan.tencentcloudapi.com',
    'region', 'ap-guangzhou',
    'model', 'hunyuan-image',
    'modelVersion', '2023-09-01',
    'resolution', '720:1280',
    'size', '720:1280',
    'count', 1,
    'watermark', true,
    'connectTimeoutMs', 10000,
    'readTimeoutMs', 120000,
    'pollIntervalMs', 1500,
    'maxPollAttempts', 240
  )),
  ('baidu-qianfan', '百度千帆图像生成', 0, 0, 60, JSON_OBJECT(
    'endpoint', 'https://qianfan.baidubce.com/v2/images/generations',
    'region', 'cn',
    'model', 'irag-1.0',
    'size', '1024x1536',
    'count', 1,
    'watermark', true,
    'connectTimeoutMs', 10000,
    'readTimeoutMs', 120000,
    'pollIntervalMs', 1500,
    'maxPollAttempts', 240
  )),
  ('http', '通用 HTTP Provider', 0, 0, 90, JSON_OBJECT(
    'endpoint', '',
    'model', 'profile-card-image',
    'authHeader', 'Authorization',
    'connectTimeoutMs', 10000,
    'readTimeoutMs', 120000
  )),
  ('openai', 'OpenAI Images', 0, 0, 100, JSON_OBJECT(
    'endpoint', 'https://api.openai.com/v1/images/edits',
    'model', 'gpt-image-1.5',
    'size', '1024x1536',
    'quality', 'high',
    'responseFormat', 'png',
    'connectTimeoutMs', 10000,
    'readTimeoutMs', 120000
  ))
ON DUPLICATE KEY UPDATE
  `display_name` = VALUES(`display_name`),
  `priority` = VALUES(`priority`),
  `public_config_json` = IF(JSON_TYPE(`public_config_json`) = 'OBJECT', `public_config_json`, VALUES(`public_config_json`));

UPDATE `admin_role`
SET `page_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`page_permissions_json`, JSON_ARRAY()),
  '$',
  'page.system.ai-image-providers'
)
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(
    COALESCE(`page_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE('page.system.ai-image-providers')
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  'action.system.ai-image-provider.update'
)
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE('action.system.ai-image-provider.update')
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  'action.system.ai-image-provider.secret.update'
)
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE('action.system.ai-image-provider.secret.update')
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  'action.system.ai-image-provider.secret.view'
)
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE('action.system.ai-image-provider.secret.view')
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  'action.system.ai-image-provider.activate'
)
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE('action.system.ai-image-provider.activate')
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  'action.system.ai-image-provider.test'
)
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE('action.system.ai-image-provider.test')
  );

COMMIT;
