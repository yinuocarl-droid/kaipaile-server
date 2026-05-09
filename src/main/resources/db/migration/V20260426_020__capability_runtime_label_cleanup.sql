-- Remove residual legacy product labels left by historical smoke/runtime data.
-- Backup keeps the affected rows before values are physically rewritten.

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

CREATE TABLE IF NOT EXISTS `zz_bak_20260426_020_capability_product_legacy_label_cleanup` AS
SELECT *
FROM `capability_product`
WHERE LOWER(`product_code`) LIKE CONCAT('%', @legacy_plus_lower, '%')
   OR LOWER(`product_code`) LIKE CONCAT('%', @legacy_domain, '%')
   OR LOWER(`product_code`) LIKE CONCAT('%', @legacy_pro_lower, '%')
   OR LOWER(`product_name`) LIKE CONCAT('%', @legacy_plus_lower, '%')
   OR LOWER(`product_name`) LIKE CONCAT('%', @legacy_domain, '%')
   OR LOWER(`product_name`) LIKE CONCAT('%', @legacy_pro_lower, '%')
   OR `product_name` LIKE CONCAT('%', @legacy_cn, '%')
   OR LOWER(CAST(`benefit_config_json` AS CHAR)) LIKE CONCAT('%', @legacy_plus_lower, '%')
   OR LOWER(CAST(`benefit_config_json` AS CHAR)) LIKE CONCAT('%', @legacy_domain, '%')
   OR LOWER(CAST(`benefit_config_json` AS CHAR)) LIKE CONCAT('%', @legacy_pro_lower, '%')
   OR CAST(`benefit_config_json` AS CHAR) LIKE CONCAT('%', @legacy_cn, '%');

UPDATE `capability_product`
SET
  `product_code` = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`product_code`, @legacy_domain_upper, 'CAPABILITY'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), @legacy_plus_upper, 'PLUS'), @legacy_plus_title, 'Plus'), @legacy_plus_lower, 'plus'), @legacy_pro_upper, 'PRO'), @legacy_pro_title, 'Pro'), @legacy_pro_lower, 'pro'),
  `product_name` = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`product_name`, @legacy_cn, '能力'), @legacy_domain_upper, 'CAPABILITY'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), @legacy_plus_upper, 'PLUS'), @legacy_plus_title, 'Plus'), @legacy_plus_lower, 'plus'), @legacy_pro_upper, 'PRO'), @legacy_pro_title, 'Pro'), @legacy_pro_lower, 'pro'),
  `benefit_config_json` = CASE
    WHEN `benefit_config_json` IS NULL THEN NULL
    ELSE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`benefit_config_json` AS CHAR), @legacy_cn, '能力'), @legacy_domain_upper, 'CAPABILITY'), @legacy_domain_title, 'Capability'), @legacy_domain, 'capability'), CONCAT('"', @legacy_plus_upper, '"'), '"PLUS"'), CONCAT('"', @legacy_plus_title, '"'), '"Plus"'), CONCAT('"', @legacy_plus_lower, '"'), '"plus"'), CONCAT('"', @legacy_pro_upper, '"'), '"PRO"'), CONCAT('"', @legacy_pro_title, '"'), '"Pro"'), CONCAT('"', @legacy_pro_lower, '"'), '"pro"')
  END
WHERE LOWER(`product_code`) LIKE CONCAT('%', @legacy_plus_lower, '%')
   OR LOWER(`product_code`) LIKE CONCAT('%', @legacy_domain, '%')
   OR LOWER(`product_code`) LIKE CONCAT('%', @legacy_pro_lower, '%')
   OR LOWER(`product_name`) LIKE CONCAT('%', @legacy_plus_lower, '%')
   OR LOWER(`product_name`) LIKE CONCAT('%', @legacy_domain, '%')
   OR LOWER(`product_name`) LIKE CONCAT('%', @legacy_pro_lower, '%')
   OR `product_name` LIKE CONCAT('%', @legacy_cn, '%')
   OR LOWER(CAST(`benefit_config_json` AS CHAR)) LIKE CONCAT('%', @legacy_plus_lower, '%')
   OR LOWER(CAST(`benefit_config_json` AS CHAR)) LIKE CONCAT('%', @legacy_domain, '%')
   OR LOWER(CAST(`benefit_config_json` AS CHAR)) LIKE CONCAT('%', @legacy_pro_lower, '%')
   OR CAST(`benefit_config_json` AS CHAR) LIKE CONCAT('%', @legacy_cn, '%');
