-- Grant current ADMIN role the direct actions needed to process contact requests.

START TRANSACTION;

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  'action.content.contact-request.approve'
)
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE('action.content.contact-request.approve')
  );

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(
  COALESCE(`action_permissions_json`, JSON_ARRAY()),
  '$',
  'action.content.contact-request.reject'
)
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(
    COALESCE(`action_permissions_json`, JSON_ARRAY()),
    JSON_QUOTE('action.content.contact-request.reject')
  );

COMMIT;
