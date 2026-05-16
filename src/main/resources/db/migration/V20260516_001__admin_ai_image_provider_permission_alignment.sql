-- Align administrator roles with the AI image provider configuration entry.
-- Entry visibility remains driven by backend role permission JSON returned by /admin/auth/me.

SET @ai_image_provider_page_permission = 'page.system.ai-image-providers';
SET @system_menu_permission = 'menu.system';
SET @ai_image_provider_update_action = 'action.system.ai-image-provider.update';
SET @ai_image_provider_secret_update_action = 'action.system.ai-image-provider.secret.update';
SET @ai_image_provider_secret_view_action = 'action.system.ai-image-provider.secret.view';
SET @ai_image_provider_activate_action = 'action.system.ai-image-provider.activate';
SET @ai_image_provider_test_action = 'action.system.ai-image-provider.test';

UPDATE `admin_role`
SET `menu_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`menu_permissions_json`, JSON_ARRAY()),
  '$',
  @system_menu_permission
)
WHERE (
    LOWER(`role_code`) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
    COALESCE(`menu_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@system_menu_permission)
  )
  )
  AND NOT JSON_CONTAINS(
    COALESCE(`menu_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@system_menu_permission)
  );

UPDATE `admin_role`
SET `page_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`page_permissions_json`, JSON_ARRAY()),
  '$',
  @ai_image_provider_page_permission
)
WHERE (
    LOWER(`role_code`) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
    COALESCE(`menu_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@system_menu_permission)
  )
  )
  AND NOT JSON_CONTAINS(
    COALESCE(`page_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@ai_image_provider_page_permission)
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  @ai_image_provider_update_action
)
WHERE (
    LOWER(`role_code`) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
    COALESCE(`menu_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@system_menu_permission)
  )
  )
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@ai_image_provider_update_action)
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  @ai_image_provider_secret_update_action
)
WHERE (
    LOWER(`role_code`) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
    COALESCE(`menu_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@system_menu_permission)
  )
  )
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@ai_image_provider_secret_update_action)
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  @ai_image_provider_secret_view_action
)
WHERE (
    LOWER(`role_code`) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
    COALESCE(`menu_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@system_menu_permission)
  )
  )
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@ai_image_provider_secret_view_action)
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  @ai_image_provider_activate_action
)
WHERE (
    LOWER(`role_code`) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
    COALESCE(`menu_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@system_menu_permission)
  )
  )
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@ai_image_provider_activate_action)
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  @ai_image_provider_test_action
)
WHERE (
    LOWER(`role_code`) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
    COALESCE(`menu_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@system_menu_permission)
  )
  )
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE(@ai_image_provider_test_action)
  );
