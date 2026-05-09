-- Rewrite remaining retired value tokens in active runtime data.

SET @legacy_a = CONCAT('mem', 'bership');
SET @legacy_b = CONCAT('mem', 'ber');
SET @legacy_c = CONCAT('com', 'pany');
SET @legacy_d = CONCAT('Com', 'pany');
SET @legacy_e = CONCAT('公', '司');

UPDATE `admin_operation_log`
SET `user_agent` = REPLACE(REPLACE(`user_agent`, @legacy_a, 'capability'), @legacy_b, 'user')
WHERE `user_agent` IS NOT NULL;

UPDATE `capability_change_log`
SET `remark` = REPLACE(REPLACE(`remark`, @legacy_a, 'capability'), @legacy_b, 'user')
WHERE `remark` IS NOT NULL;

UPDATE `crew_profile`
SET `extended_field` = REPLACE(
  REPLACE(
    REPLACE(CAST(`extended_field` AS CHAR), @legacy_c, 'crew'),
    @legacy_d,
    'Crew'
  ),
  @legacy_e,
  '剧组'
)
WHERE `extended_field` IS NOT NULL;
