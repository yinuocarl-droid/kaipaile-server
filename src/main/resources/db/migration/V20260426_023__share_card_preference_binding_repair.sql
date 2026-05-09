CREATE TABLE IF NOT EXISTS `zz_bak_20260426_023_share_card_missing_preference` LIKE `user_share_card`;

INSERT IGNORE INTO `zz_bak_20260426_023_share_card_missing_preference`
SELECT card.*
FROM `user_share_card` card
LEFT JOIN `actor_share_preference` pref
  ON pref.`share_card_id` = card.`share_card_id`
 AND pref.`deleted` = 0
WHERE card.`deleted` = 0
  AND card.`share_status` = 'active'
  AND pref.`preference_id` IS NULL;

INSERT INTO `actor_share_preference` (
  `share_card_id`,
  `preferred_artifact`,
  `create_user_id`,
  `create_user_name`,
  `create_time`,
  `update_user_id`,
  `update_user_name`,
  `last_update`
)
SELECT
  card.`share_card_id`,
  'miniProgramCard',
  NULL,
  '',
  NOW(),
  NULL,
  '',
  NOW()
FROM `user_share_card` card
LEFT JOIN `actor_share_preference` pref
  ON pref.`share_card_id` = card.`share_card_id`
 AND pref.`deleted` = 0
WHERE card.`deleted` = 0
  AND card.`share_status` = 'active'
  AND pref.`preference_id` IS NULL;
