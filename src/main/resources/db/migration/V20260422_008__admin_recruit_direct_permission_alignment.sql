-- Align current ADMIN role with direct recruit governance permissions.

START TRANSACTION;

UPDATE `admin_role`
SET `page_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`page_permissions_json`, JSON_ARRAY()), '$', 'page.recruit.projects')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`page_permissions_json`, JSON_ARRAY()), JSON_QUOTE('page.recruit.projects'));

UPDATE `admin_role`
SET `page_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`page_permissions_json`, JSON_ARRAY()), '$', 'page.recruit.roles')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`page_permissions_json`, JSON_ARRAY()), JSON_QUOTE('page.recruit.roles'));

UPDATE `admin_role`
SET `page_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`page_permissions_json`, JSON_ARRAY()), '$', 'page.recruit.applies')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`page_permissions_json`, JSON_ARRAY()), JSON_QUOTE('page.recruit.applies'));

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`action_permissions_json`, JSON_ARRAY()), '$', 'action.recruit.project.status')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`action_permissions_json`, JSON_ARRAY()), JSON_QUOTE('action.recruit.project.status'));

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`action_permissions_json`, JSON_ARRAY()), '$', 'action.recruit.role.status')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`action_permissions_json`, JSON_ARRAY()), JSON_QUOTE('action.recruit.role.status'));

COMMIT;
