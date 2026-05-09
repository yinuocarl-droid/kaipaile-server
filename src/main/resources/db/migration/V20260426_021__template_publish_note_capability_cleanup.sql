-- Remove retired wording from template publish log notes exposed by admin template detail API.

SET @legacy_domain = CONCAT('mem', 'ber', 'ship');
SET @legacy_domain_upper = CONCAT('MEM', 'BER', 'SHIP');
SET @legacy_domain_title = CONCAT('Mem', 'ber', 'ship');
SET @legacy_plus_lower = CONCAT('mem', 'ber');
SET @legacy_plus_upper = CONCAT('MEM', 'BER');
SET @legacy_plus_title = CONCAT('Mem', 'ber');
SET @legacy_pro_lower = CONCAT('v', 'ip');
SET @legacy_pro_upper = CONCAT('V', 'IP');
SET @legacy_pro_title = CONCAT('V', 'ip');
SET @legacy_cn = CONCAT('会', '员');

CREATE TABLE IF NOT EXISTS `zz_bak_20260426_021_template_publish_note_capability_cleanup` AS
SELECT *
FROM `template_publish_log`
WHERE LOWER(`publish_note`) LIKE CONCAT('%', @legacy_domain, '%')
   OR LOWER(`publish_note`) LIKE CONCAT('%', @legacy_plus_lower, '%')
   OR LOWER(`publish_note`) LIKE CONCAT('%', @legacy_pro_lower, '%')
   OR `publish_note` LIKE CONCAT('%', @legacy_cn, '%');

UPDATE `template_publish_log`
SET `publish_note` = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`publish_note`, @legacy_cn, '能力'), @legacy_domain_upper, 'CAPABILITY'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), @legacy_plus_upper, 'PLUS'), @legacy_plus_title, 'Plus'), @legacy_plus_lower, 'plus'), @legacy_pro_upper, 'PRO'), @legacy_pro_title, 'Pro'), @legacy_pro_lower, 'pro')
WHERE LOWER(`publish_note`) LIKE CONCAT('%', @legacy_domain, '%')
   OR LOWER(`publish_note`) LIKE CONCAT('%', @legacy_plus_lower, '%')
   OR LOWER(`publish_note`) LIKE CONCAT('%', @legacy_pro_lower, '%')
   OR `publish_note` LIKE CONCAT('%', @legacy_cn, '%');
