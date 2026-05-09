-- Align admin API runtime data and direct permissions with the current strict contract.

START TRANSACTION;

UPDATE `card_scene_template`
SET `artifact_preset_json` = JSON_OBJECT(
  'requiredInviteCount', 0,
  'contentFocus', JSON_ARRAY('镜头表现', '角色适配'),
  'poster', JSON_OBJECT('enabled', TRUE, 'ratio', '3:4'),
  'miniProgramCard', JSON_OBJECT('enabled', TRUE, 'ratio', '1:1'),
  'pageConfig', JSON_OBJECT(
    'layoutPreset', 'magazine',
    'surface', 'paper',
    'density', 'balanced',
    'heroStyle', 'editorial',
    'sections', JSON_OBJECT(
      'profile', TRUE,
      'stats', TRUE,
      'timeline', TRUE,
      'contactCta', TRUE
    ),
    'actions', JSON_OBJECT(
      'primary', 'contact',
      'secondary', 'share'
    )
  )
)
WHERE `deleted` = 0
  AND (
    `artifact_preset_json` IS NULL
    OR `artifact_preset_json` = ''
    OR JSON_VALID(`artifact_preset_json`) = 0
  );

UPDATE `card_scene_template`
SET `artifact_preset_json` = JSON_SET(
  `artifact_preset_json`,
  '$.pageConfig',
  JSON_OBJECT(
    'layoutPreset', 'magazine',
    'surface', 'paper',
    'density', 'balanced',
    'heroStyle', 'editorial',
    'sections', JSON_OBJECT(
      'profile', TRUE,
      'stats', TRUE,
      'timeline', TRUE,
      'contactCta', TRUE
    ),
    'actions', JSON_OBJECT(
      'primary', 'contact',
      'secondary', 'share'
    )
  )
)
WHERE `deleted` = 0
  AND JSON_VALID(`artifact_preset_json`) = 1
  AND JSON_CONTAINS_PATH(`artifact_preset_json`, 'one', '$.pageConfig') = 0;

UPDATE `admin_role`
SET `page_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`page_permissions_json`, JSON_ARRAY()), '$', 'page.payment.orders')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`page_permissions_json`, JSON_ARRAY()), JSON_QUOTE('page.payment.orders'));

UPDATE `admin_role`
SET `page_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`page_permissions_json`, JSON_ARRAY()), '$', 'page.refund.orders')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`page_permissions_json`, JSON_ARRAY()), JSON_QUOTE('page.refund.orders'));

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`action_permissions_json`, JSON_ARRAY()), '$', 'action.refund.approve')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`action_permissions_json`, JSON_ARRAY()), JSON_QUOTE('action.refund.approve'));

UPDATE `admin_role`
SET `action_permissions_json` = JSON_ARRAY_APPEND(COALESCE(`action_permissions_json`, JSON_ARRAY()), '$', 'action.refund.reject')
WHERE `role_code` = 'ADMIN'
  AND NOT JSON_CONTAINS(COALESCE(`action_permissions_json`, JSON_ARRAY()), JSON_QUOTE('action.refund.reject'));

COMMIT;
