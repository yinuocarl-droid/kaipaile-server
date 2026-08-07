-- Add the five prompt-template actions to live system administration roles.
-- Page, menu, audit, route, and navigation grants remain unchanged.

SET @profile_prompt_read = 'action.system.ai-profile-import.template-read';
SET @profile_prompt_update = 'action.system.ai-profile-import.template-update';
SET @profile_prompt_test = 'action.system.ai-profile-import.template-test';
SET @profile_prompt_publish = 'action.system.ai-profile-import.template-publish';
SET @profile_prompt_restore = 'action.system.ai-profile-import.template-restore';

UPDATE admin_role
SET action_permissions_json = JSON_ARRAY_APPEND(
        COALESCE(action_permissions_json, JSON_ARRAY()),
        '$',
        @profile_prompt_read)
WHERE status = 1
  AND deleted = 0
  AND (
    LOWER(role_code) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
        COALESCE(menu_permissions_json, JSON_ARRAY()),
        JSON_QUOTE('menu.system'))
  )
  AND NOT JSON_CONTAINS(
      COALESCE(action_permissions_json, JSON_ARRAY()),
      JSON_QUOTE(@profile_prompt_read));

UPDATE admin_role
SET action_permissions_json = JSON_ARRAY_APPEND(
        COALESCE(action_permissions_json, JSON_ARRAY()),
        '$',
        @profile_prompt_update)
WHERE status = 1
  AND deleted = 0
  AND (
    LOWER(role_code) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
        COALESCE(menu_permissions_json, JSON_ARRAY()),
        JSON_QUOTE('menu.system'))
  )
  AND NOT JSON_CONTAINS(
      COALESCE(action_permissions_json, JSON_ARRAY()),
      JSON_QUOTE(@profile_prompt_update));

UPDATE admin_role
SET action_permissions_json = JSON_ARRAY_APPEND(
        COALESCE(action_permissions_json, JSON_ARRAY()),
        '$',
        @profile_prompt_test)
WHERE status = 1
  AND deleted = 0
  AND (
    LOWER(role_code) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
        COALESCE(menu_permissions_json, JSON_ARRAY()),
        JSON_QUOTE('menu.system'))
  )
  AND NOT JSON_CONTAINS(
      COALESCE(action_permissions_json, JSON_ARRAY()),
      JSON_QUOTE(@profile_prompt_test));

UPDATE admin_role
SET action_permissions_json = JSON_ARRAY_APPEND(
        COALESCE(action_permissions_json, JSON_ARRAY()),
        '$',
        @profile_prompt_publish)
WHERE status = 1
  AND deleted = 0
  AND (
    LOWER(role_code) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
        COALESCE(menu_permissions_json, JSON_ARRAY()),
        JSON_QUOTE('menu.system'))
  )
  AND NOT JSON_CONTAINS(
      COALESCE(action_permissions_json, JSON_ARRAY()),
      JSON_QUOTE(@profile_prompt_publish));

UPDATE admin_role
SET action_permissions_json = JSON_ARRAY_APPEND(
        COALESCE(action_permissions_json, JSON_ARRAY()),
        '$',
        @profile_prompt_restore)
WHERE status = 1
  AND deleted = 0
  AND (
    LOWER(role_code) IN ('admin', 'super_admin')
    OR JSON_CONTAINS(
        COALESCE(menu_permissions_json, JSON_ARRAY()),
        JSON_QUOTE('menu.system'))
  )
  AND NOT JSON_CONTAINS(
      COALESCE(action_permissions_json, JSON_ARRAY()),
      JSON_QUOTE(@profile_prompt_restore));
