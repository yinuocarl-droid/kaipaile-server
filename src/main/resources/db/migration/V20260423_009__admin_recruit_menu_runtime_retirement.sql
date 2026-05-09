SET @recruit_menu_permission = CONCAT('menu.', 'recruit');

START TRANSACTION;

UPDATE `admin_role`
SET `menu_permissions_json` = JSON_REMOVE(
  COALESCE(`menu_permissions_json`, JSON_ARRAY()),
  JSON_UNQUOTE(JSON_SEARCH(COALESCE(`menu_permissions_json`, JSON_ARRAY()), 'one', @recruit_menu_permission))
)
WHERE JSON_SEARCH(COALESCE(`menu_permissions_json`, JSON_ARRAY()), 'one', @recruit_menu_permission) IS NOT NULL;

COMMIT;
