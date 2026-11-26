-- 00-222 演员卡素材绑定基础：集合 + 条目。
-- 只保存 assetId 或历史 URL 回退，不保存私有桶签名 URL。
-- 旧 actor_card URL 字段和 00-214 attachment_asset_id 保持不变。

CREATE TABLE IF NOT EXISTS `actor_card_media_set` (
  `set_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '素材绑定集合 id',
  `card_id` BIGINT NOT NULL COMMENT '所属演员卡 id',
  `card_work_id` BIGINT DEFAULT NULL COMMENT '作品剧照集合对应 actor_card_work.id，其他用途为空',
  `usage_code` VARCHAR(32) NOT NULL COMMENT 'source_image,work_still,life_photo,video_resume',
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `rid` VARCHAR(64) DEFAULT NULL,
  `create_user_id` BIGINT DEFAULT NULL,
  `create_user_name` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` BIGINT DEFAULT NULL,
  `update_user_name` VARCHAR(64) DEFAULT '',
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`set_id`),
  KEY `idx_actor_card_media_set_card_usage` (`card_id`, `card_work_id`, `usage_code`, `deleted`, `last_update`),
  KEY `idx_actor_card_media_set_work` (`card_work_id`, `deleted`),
  CONSTRAINT `chk_actor_card_media_set_usage`
    CHECK (`usage_code` IN ('source_image', 'work_still', 'life_photo', 'video_resume')),
  CONSTRAINT `chk_actor_card_media_set_scope`
    CHECK ((`usage_code` = 'work_still' AND `card_work_id` IS NOT NULL)
      OR (`usage_code` <> 'work_still' AND `card_work_id` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演员卡按用途绑定的素材集合';

CREATE TABLE IF NOT EXISTS `actor_card_media_item` (
  `item_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '素材绑定条目 id',
  `set_id` BIGINT NOT NULL COMMENT '所属素材绑定集合 id',
  `asset_id` BIGINT DEFAULT NULL COMMENT '新素材库 asset id',
  `legacy_url` VARCHAR(1024) DEFAULT NULL COMMENT '历史 URL 回退条目，仅由服务端导入',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '展示顺序，从 0 开始',
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `rid` VARCHAR(64) DEFAULT NULL,
  `create_user_id` BIGINT DEFAULT NULL,
  `create_user_name` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` BIGINT DEFAULT NULL,
  `update_user_name` VARCHAR(64) DEFAULT '',
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_asset_id` BIGINT GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `asset_id` ELSE NULL END) STORED,
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_actor_card_media_item_active_asset` (`set_id`, `active_asset_id`),
  KEY `idx_actor_card_media_item_set_order` (`set_id`, `deleted`, `sort_order`),
  KEY `idx_actor_card_media_item_asset` (`asset_id`, `deleted`),
  CONSTRAINT `chk_actor_card_media_item_source`
    CHECK ((`asset_id` IS NOT NULL AND NULLIF(TRIM(`legacy_url`), '') IS NULL)
      OR (`asset_id` IS NULL AND NULLIF(TRIM(`legacy_url`), '') IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演员卡素材绑定条目';
