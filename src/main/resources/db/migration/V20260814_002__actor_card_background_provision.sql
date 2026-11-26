-- =====================================================================
-- 生产预置 actor_card_background 表与清理后 seed 数据（2026-08-14）
-- 背景：生产远端（101.43.57.62:3306/kaipai_dev）不存在此表，
--       而首页模板区 /api/actor-card/background-library 依赖它，缺表会 500。
-- 本迁移幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，
--       生产执行 → 建表 + 写入 7 行 seed（classic 3 / urban 3 / ancient 1）；
--       本地执行 → 表已存在、行已存在，无副作用。
-- 数据为 2026-08-14 清理错误归属（V20260814_001）后的最终集合；fresh 暂无真实图。
-- =====================================================================
CREATE TABLE IF NOT EXISTS `actor_card_background` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '背景图 id',
  `style` varchar(30) NOT NULL COMMENT '所属风格: classic|urban|ancient|fresh',
  `image_url` varchar(1024) NOT NULL COMMENT '原图 URL',
  `thumbnail_url` varchar(1024) DEFAULT NULL COMMENT '缩略图 URL（列表展示用）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '风格内排序',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用: 1=启用, 0=禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_update` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_actor_card_background_style` (`style`,`enabled`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演员卡背景图库（系统维护，不进用户素材库）';

INSERT IGNORE INTO `actor_card_background` (id, style, image_url, thumbnail_url, sort_order, enabled) VALUES
(1, 'classic', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/c1e789d8937847c8a71f08a94ae10f63.png', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/c1e789d8937847c8a71f08a94ae10f63.png', 1, 1),
(2, 'classic', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/94f0fb3e924f4ecab8982a805923a92f.png', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/94f0fb3e924f4ecab8982a805923a92f.png', 2, 1),
(3, 'classic', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/056025a663144e64bd311f6ea4458530.png', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/056025a663144e64bd311f6ea4458530.png', 3, 1),
(4, 'urban', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/2b6da833a950402892b305bea345fc41.png', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/2b6da833a950402892b305bea345fc41.png', 1, 1),
(5, 'urban', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/3afdcccf2d704cad9d1bf54758196068.png', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/21/3afdcccf2d704cad9d1bf54758196068.png', 2, 1),
(6, 'urban', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/19d282a8cdc14c07beed82e3ff497e14.png', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/19d282a8cdc14c07beed82e3ff497e14.png', 3, 1),
(7, 'ancient', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/fa1a8bd121964458a4d315ae3afe3f58.png', 'https://kaipai-prod-1412601014.cos.ap-shanghai.myqcloud.com/ai-profile-card/2026/07/10/fa1a8bd121964458a4d315ae3afe3f58.png', 1, 1);
