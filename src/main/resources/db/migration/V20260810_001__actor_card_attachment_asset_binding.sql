-- 00-214 步骤 6 附件简历端到端接通：actor_card 改为绑定 actor_media_asset.asset_id。
-- 背景：附件存在私有桶，读取只能靠 10 分钟有效期的预签名 URL，
-- 因此 attachment_url 这一列在架构上不可用（存进去也会过期失效），本次改为存素材 id。
-- attachment_url 本次起停写、只读兼容，物理退场见 00-110。
ALTER TABLE `actor_card`
  ADD COLUMN `attachment_asset_id` BIGINT DEFAULT NULL COMMENT '附件简历素材 id（步骤 6，指向 actor_media_asset.asset_id）' AFTER `attachment_url`,
  ADD KEY `idx_actor_card_attachment_asset` (`attachment_asset_id`);
