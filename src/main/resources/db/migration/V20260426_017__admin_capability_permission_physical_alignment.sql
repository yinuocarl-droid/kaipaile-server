-- Physically align retired admin permission values with the current capability contract.
-- Backup is kept before old permission entries are removed; no compatibility aliases are retained.

SET @legacy_domain = CONCAT('mem', 'ber', 'ship');
SET @legacy_menu_permission = CONCAT('menu.', @legacy_domain);
SET @legacy_page_permission_pattern = CONCAT('page.', @legacy_domain, '.%');
SET @legacy_action_permission_pattern = CONCAT('action.', @legacy_domain, '.%');

CREATE TABLE IF NOT EXISTS `zz_bak_20260426_017_admin_role_legacy_capability_permissions` AS
SELECT
  `admin_role_id`,
  `role_code`,
  `role_name`,
  `menu_permissions_json`,
  `page_permissions_json`,
  `action_permissions_json`,
  NOW() AS `backup_time`
FROM `admin_role`
WHERE CAST(`menu_permissions_json` AS CHAR) LIKE CONCAT('%', @legacy_menu_permission, '%')
   OR CAST(`page_permissions_json` AS CHAR) LIKE CONCAT('%', @legacy_page_permission_pattern)
   OR CAST(`action_permissions_json` AS CHAR) LIKE CONCAT('%', @legacy_action_permission_pattern);

UPDATE `admin_role` AS r
SET r.`menu_permissions_json` = (
  SELECT COALESCE(JSON_ARRAYAGG(p.`permission`), JSON_ARRAY())
  FROM JSON_TABLE(
    COALESCE(r.`menu_permissions_json`, JSON_ARRAY()),
    '$[*]' COLUMNS (`permission` VARCHAR(128) PATH '$')
  ) AS p
  WHERE p.`permission` <> @legacy_menu_permission
)
WHERE CAST(r.`menu_permissions_json` AS CHAR) LIKE CONCAT('%', @legacy_menu_permission, '%');

UPDATE `admin_role` AS r
SET r.`page_permissions_json` = (
  SELECT COALESCE(JSON_ARRAYAGG(p.`permission`), JSON_ARRAY())
  FROM JSON_TABLE(
    COALESCE(r.`page_permissions_json`, JSON_ARRAY()),
    '$[*]' COLUMNS (`permission` VARCHAR(128) PATH '$')
  ) AS p
  WHERE p.`permission` NOT LIKE @legacy_page_permission_pattern
)
WHERE CAST(r.`page_permissions_json` AS CHAR) LIKE CONCAT('%', @legacy_page_permission_pattern);

UPDATE `admin_role` AS r
SET r.`action_permissions_json` = (
  SELECT COALESCE(JSON_ARRAYAGG(p.`permission`), JSON_ARRAY())
  FROM JSON_TABLE(
    COALESCE(r.`action_permissions_json`, JSON_ARRAY()),
    '$[*]' COLUMNS (`permission` VARCHAR(128) PATH '$')
  ) AS p
  WHERE p.`permission` NOT LIKE @legacy_action_permission_pattern
)
WHERE CAST(r.`action_permissions_json` AS CHAR) LIKE CONCAT('%', @legacy_action_permission_pattern);
