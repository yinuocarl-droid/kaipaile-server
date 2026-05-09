CREATE TABLE IF NOT EXISTS `zz_bak_20260425_013_card_missing_pref` LIKE `user_share_card`;
CREATE TABLE IF NOT EXISTS `zz_bak_20260425_013_cfg_missing_pref_card` LIKE `actor_card_config`;
CREATE TABLE IF NOT EXISTS `zz_bak_20260425_013_req_missing_pref_card` LIKE `share_card_contact_request`;
CREATE TABLE IF NOT EXISTS `zz_bak_20260425_013_hist_missing_pref_card` LIKE `share_card_view_history`;

INSERT IGNORE INTO `zz_bak_20260425_013_card_missing_pref`
SELECT card.*
FROM `user_share_card` card
LEFT JOIN `actor_share_preference` pref
  ON pref.`share_card_id` = card.`share_card_id`
 AND pref.`deleted` = 0
WHERE card.`deleted` = 0
  AND card.`share_status` = 'active'
  AND pref.`preference_id` IS NULL;

INSERT IGNORE INTO `zz_bak_20260425_013_cfg_missing_pref_card`
SELECT config.*
FROM `actor_card_config` config
JOIN `zz_bak_20260425_013_card_missing_pref` bad_card
  ON bad_card.`share_card_id` = config.`share_card_id`;

INSERT IGNORE INTO `zz_bak_20260425_013_req_missing_pref_card`
SELECT request.*
FROM `share_card_contact_request` request
JOIN `zz_bak_20260425_013_card_missing_pref` bad_card
  ON bad_card.`share_card_id` = request.`share_card_id`;

INSERT IGNORE INTO `zz_bak_20260425_013_hist_missing_pref_card`
SELECT history.*
FROM `share_card_view_history` history
JOIN `zz_bak_20260425_013_card_missing_pref` bad_card
  ON bad_card.`share_card_id` = history.`share_card_id`;

DELETE request
FROM `share_card_contact_request` request
JOIN `zz_bak_20260425_013_card_missing_pref` bad_card
  ON bad_card.`share_card_id` = request.`share_card_id`;

DELETE history
FROM `share_card_view_history` history
JOIN `zz_bak_20260425_013_card_missing_pref` bad_card
  ON bad_card.`share_card_id` = history.`share_card_id`;

DELETE config
FROM `actor_card_config` config
JOIN `zz_bak_20260425_013_card_missing_pref` bad_card
  ON bad_card.`share_card_id` = config.`share_card_id`;

DELETE card
FROM `user_share_card` card
JOIN `zz_bak_20260425_013_card_missing_pref` bad_card
  ON bad_card.`share_card_id` = card.`share_card_id`;
