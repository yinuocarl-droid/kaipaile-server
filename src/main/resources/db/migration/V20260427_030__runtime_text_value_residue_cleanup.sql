-- Rewrite remaining retired text tokens in active business rows.

SET @legacy_a = CONCAT('com', 'pany');
SET @legacy_b = CONCAT('Com', 'pany');
SET @legacy_c = CONCAT('for', 'tune');
SET @legacy_d = CONCAT('For', 'tune');
SET @legacy_e = CONCAT('mem', 'bership');
SET @legacy_f = CONCAT('mem', 'ber');

UPDATE `crew_profile`
SET `intro` = REPLACE(REPLACE(`intro`, @legacy_a, 'crew'), @legacy_b, 'Crew')
WHERE `intro` IS NOT NULL;

UPDATE `recruit_apply`
SET `apply_message` = REPLACE(REPLACE(`apply_message`, @legacy_a, 'crew'), @legacy_b, 'Crew')
WHERE `apply_message` IS NOT NULL;

UPDATE `recruit_post`
SET
  `drama_name` = REPLACE(REPLACE(`drama_name`, @legacy_a, 'crew'), @legacy_b, 'Crew'),
  `role_desc` = REPLACE(REPLACE(`role_desc`, @legacy_a, 'crew'), @legacy_b, 'Crew'),
  `role_name` = REPLACE(REPLACE(`role_name`, @legacy_a, 'crew'), @legacy_b, 'Crew'),
  `title` = REPLACE(REPLACE(`title`, @legacy_a, 'crew'), @legacy_b, 'Crew')
WHERE `drama_name` IS NOT NULL
   OR `role_desc` IS NOT NULL
   OR `role_name` IS NOT NULL
   OR `title` IS NOT NULL;

UPDATE `user`
SET
  `register_device_fingerprint` = REPLACE(
    REPLACE(`register_device_fingerprint`, @legacy_c, 'profile'),
    @legacy_d,
    'Profile'
  ),
  `user_name` = REPLACE(REPLACE(`user_name`, @legacy_c, 'profile'), @legacy_d, 'Profile'),
  `remark` = REPLACE(REPLACE(`remark`, @legacy_e, 'capability'), @legacy_f, 'user')
WHERE `register_device_fingerprint` IS NOT NULL
   OR `user_name` IS NOT NULL
   OR `remark` IS NOT NULL;
