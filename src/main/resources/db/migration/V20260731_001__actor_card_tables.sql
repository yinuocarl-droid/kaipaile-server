-- T1: 演员卡创建向导 — 三张核心表 (00-206)
-- actor_card: 演员卡主表（草稿 + 已发布）
-- actor_card_work: 演员卡参演作品快照
-- actor_card_background: 背景图库（系统管理，不进用户素材库）

CREATE TABLE `actor_card` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '演员卡 id',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 id',
  `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态: draft=草稿, published=已发布',
  `title` VARCHAR(100) DEFAULT NULL COMMENT '演员卡名称，如「都市演员卡」',
  `style` VARCHAR(30) DEFAULT NULL COMMENT '风格: classic|urban|ancient|fresh',
  `current_step` TINYINT NOT NULL DEFAULT 1 COMMENT '最近编辑步骤 1-7',
  `step_status_json` TEXT DEFAULT NULL COMMENT '各步骤完成状态 JSON',
  `background_image_url` VARCHAR(1024) DEFAULT NULL COMMENT '已选背景图 URL',
  `source_image_url` VARCHAR(1024) DEFAULT NULL COMMENT '原始首图 URL（用户上传）',
  `expanded_image_url` VARCHAR(1024) DEFAULT NULL COMMENT 'AI 扩图后首图 URL',
  `profile_snapshot_json` LONGTEXT DEFAULT NULL COMMENT '个人资料快照 JSON（步骤 2）',
  `photos_json` LONGTEXT DEFAULT NULL COMMENT '生活照片 URL 数组 JSON（步骤 4）',
  `video_url` VARCHAR(1024) DEFAULT NULL COMMENT '视频简历 URL（步骤 5）',
  `attachment_url` VARCHAR(1024) DEFAULT NULL COMMENT '附件简历 URL（步骤 6）',
  `settings_json` TEXT DEFAULT NULL COMMENT '生成设置 JSON（步骤 7）',
  `generated_preview_url` VARCHAR(1024) DEFAULT NULL COMMENT 'AI 生成长页预览 URL',
  `published_version` INT NOT NULL DEFAULT 0 COMMENT '已发布版本号，每次重新发布 +1',
  `published_at` DATETIME DEFAULT NULL COMMENT '最近发布时间',
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `rid` VARCHAR(64) DEFAULT NULL,
  `create_user_id` BIGINT DEFAULT NULL,
  `create_user_name` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` BIGINT DEFAULT NULL,
  `update_user_name` VARCHAR(64) DEFAULT '',
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_actor_card_user_status` (`user_id`, `status`, `create_time`),
  KEY `idx_actor_card_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演员卡主表（草稿与已发布）';

CREATE TABLE `actor_card_work` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录 id',
  `card_id` BIGINT NOT NULL COMMENT '所属演员卡 id',
  `source_work_id` BIGINT DEFAULT NULL COMMENT '来源演艺经历 id，新增作品时为 NULL',
  `work_title` VARCHAR(200) NOT NULL COMMENT '作品名称',
  `work_type` VARCHAR(30) DEFAULT NULL COMMENT '作品类型: short_drama|micro_film|tv|movie|other',
  `role_name` VARCHAR(100) DEFAULT NULL COMMENT '饰演角色名',
  `stills_json` TEXT DEFAULT NULL COMMENT '剧照 URL 数组 JSON，最多 3 张，第一张为封面',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '展示排序，越小越靠前',
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `rid` VARCHAR(64) DEFAULT NULL,
  `create_user_id` BIGINT DEFAULT NULL,
  `create_user_name` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_id` BIGINT DEFAULT NULL,
  `update_user_name` VARCHAR(64) DEFAULT '',
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_actor_card_work_card` (`card_id`, `sort_order`),
  KEY `idx_actor_card_work_source` (`source_work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演员卡参演作品快照（与演员卡绑定，不随演艺经历原始数据变化）';

CREATE TABLE `actor_card_background` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '背景图 id',
  `style` VARCHAR(30) NOT NULL COMMENT '所属风格: classic|urban|ancient|fresh',
  `image_url` VARCHAR(1024) NOT NULL COMMENT '原图 URL',
  `thumbnail_url` VARCHAR(1024) DEFAULT NULL COMMENT '缩略图 URL（列表展示用）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '风格内排序',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用: 1=启用, 0=禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_update` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_actor_card_background_style` (`style`, `enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演员卡背景图库（系统维护，不进用户素材库）';
