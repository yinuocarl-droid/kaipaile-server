-- Grant governed DeepSeek import permissions to existing system administrators.
SET @profile_import_page = 'page.system.ai-profile-import';
SET @profile_import_update = 'action.system.ai-profile-import.update';
SET @profile_import_secret = 'action.system.ai-profile-import.secret';
SET @profile_import_test = 'action.system.ai-profile-import.test';
SET @profile_import_audit = 'action.system.ai-profile-import.audit';

UPDATE admin_role SET page_permissions_json = JSON_ARRAY_APPEND(COALESCE(page_permissions_json, JSON_ARRAY()), '$', @profile_import_page) WHERE (LOWER(role_code) IN ('admin', 'super_admin') OR JSON_CONTAINS(COALESCE(menu_permissions_json, JSON_ARRAY()), JSON_QUOTE('menu.system'))) AND NOT JSON_CONTAINS(COALESCE(page_permissions_json, JSON_ARRAY()), JSON_QUOTE(@profile_import_page));
UPDATE admin_role SET action_permissions_json = JSON_ARRAY_APPEND(COALESCE(action_permissions_json, JSON_ARRAY()), '$', @profile_import_update) WHERE (LOWER(role_code) IN ('admin', 'super_admin') OR JSON_CONTAINS(COALESCE(menu_permissions_json, JSON_ARRAY()), JSON_QUOTE('menu.system'))) AND NOT JSON_CONTAINS(COALESCE(action_permissions_json, JSON_ARRAY()), JSON_QUOTE(@profile_import_update));
UPDATE admin_role SET action_permissions_json = JSON_ARRAY_APPEND(COALESCE(action_permissions_json, JSON_ARRAY()), '$', @profile_import_secret) WHERE (LOWER(role_code) IN ('admin', 'super_admin') OR JSON_CONTAINS(COALESCE(menu_permissions_json, JSON_ARRAY()), JSON_QUOTE('menu.system'))) AND NOT JSON_CONTAINS(COALESCE(action_permissions_json, JSON_ARRAY()), JSON_QUOTE(@profile_import_secret));
UPDATE admin_role SET action_permissions_json = JSON_ARRAY_APPEND(COALESCE(action_permissions_json, JSON_ARRAY()), '$', @profile_import_test) WHERE (LOWER(role_code) IN ('admin', 'super_admin') OR JSON_CONTAINS(COALESCE(menu_permissions_json, JSON_ARRAY()), JSON_QUOTE('menu.system'))) AND NOT JSON_CONTAINS(COALESCE(action_permissions_json, JSON_ARRAY()), JSON_QUOTE(@profile_import_test));
UPDATE admin_role SET action_permissions_json = JSON_ARRAY_APPEND(COALESCE(action_permissions_json, JSON_ARRAY()), '$', @profile_import_audit) WHERE (LOWER(role_code) IN ('admin', 'super_admin') OR JSON_CONTAINS(COALESCE(menu_permissions_json, JSON_ARRAY()), JSON_QUOTE('menu.system'))) AND NOT JSON_CONTAINS(COALESCE(action_permissions_json, JSON_ARRAY()), JSON_QUOTE(@profile_import_audit));
