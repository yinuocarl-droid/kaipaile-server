-- Ensure the actor share-card creator has the current public template set.

SET NAMES utf8mb4;

START TRANSACTION;

UPDATE `card_scene_template`
SET `template_name` = '经典',
    `description` = '永恒影调 · 光影经典',
    `layout_variant` = 'compact',
    `tier` = 'free',
    `required_level` = 1,
    `unlock_required` = 0,
    `base_theme_json` = JSON_OBJECT(
      'themeColors', JSON_OBJECT(
        'primary', '#8c6f4f',
        'accent', '#d4b896',
        'background', '#f5f3ee',
        'text', '#231b15',
        'heroText', '#fffdf8'
      )
    ),
    `artifact_preset_json` = JSON_OBJECT(
      'coverImage', '',
      'heroEyebrow', 'CLASSIC',
      'requiredInviteCount', 0,
      'contentFocus', JSON_ARRAY('镜头表现', '角色适配'),
      'miniProgramCard', JSON_OBJECT('enabled', TRUE, 'ratio', '1:1'),
      'poster', JSON_OBJECT('enabled', TRUE, 'ratio', '3:4'),
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
    ),
    `status` = 1,
    `sort_no` = 30,
    `deleted` = 0,
    `last_update` = NOW()
WHERE `template_scene_code` = 'classic'
  AND `deleted` = 0;

INSERT INTO `card_scene_template` (
  `template_code`,
  `template_scene_code`,
  `template_name`,
  `description`,
  `layout_variant`,
  `tier`,
  `required_level`,
  `unlock_required`,
  `base_theme_json`,
  `artifact_preset_json`,
  `status`,
  `sort_no`,
  `deleted`,
  `create_time`,
  `last_update`
)
SELECT
  'CLASSIC_TEMPLATE',
  'classic',
  '经典',
  '永恒影调 · 光影经典',
  'compact',
  'free',
  1,
  0,
  JSON_OBJECT(
    'themeColors', JSON_OBJECT(
      'primary', '#8c6f4f',
      'accent', '#d4b896',
      'background', '#f5f3ee',
      'text', '#231b15',
      'heroText', '#fffdf8'
    )
  ),
  JSON_OBJECT(
    'coverImage', '',
    'heroEyebrow', 'CLASSIC',
    'requiredInviteCount', 0,
    'contentFocus', JSON_ARRAY('镜头表现', '角色适配'),
    'miniProgramCard', JSON_OBJECT('enabled', TRUE, 'ratio', '1:1'),
    'poster', JSON_OBJECT('enabled', TRUE, 'ratio', '3:4'),
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
  ),
  1,
  30,
  0,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1
  FROM `card_scene_template`
  WHERE `template_scene_code` = 'classic'
    AND `deleted` = 0
)
ON DUPLICATE KEY UPDATE
  `template_scene_code` = VALUES(`template_scene_code`),
  `template_name` = VALUES(`template_name`),
  `description` = VALUES(`description`),
  `layout_variant` = VALUES(`layout_variant`),
  `tier` = VALUES(`tier`),
  `required_level` = VALUES(`required_level`),
  `unlock_required` = VALUES(`unlock_required`),
  `base_theme_json` = VALUES(`base_theme_json`),
  `artifact_preset_json` = VALUES(`artifact_preset_json`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `deleted` = VALUES(`deleted`),
  `last_update` = NOW();

INSERT INTO `card_scene_template` (
  `template_code`,
  `template_scene_code`,
  `template_name`,
  `description`,
  `layout_variant`,
  `tier`,
  `required_level`,
  `unlock_required`,
  `base_theme_json`,
  `artifact_preset_json`,
  `status`,
  `sort_no`,
  `deleted`,
  `create_time`,
  `last_update`
) VALUES (
  'URBAN_TEMPLATE',
  'urban',
  '都市',
  '都市霓虹 · 现代高光',
  'spacious',
  'free',
  1,
  0,
  JSON_OBJECT(
    'themeColors', JSON_OBJECT(
      'primary', '#3f526b',
      'accent', '#9fb7c8',
      'background', '#eef2f4',
      'text', '#18212b',
      'heroText', '#ffffff'
    )
  ),
  JSON_OBJECT(
    'coverImage', '',
    'heroEyebrow', 'URBAN',
    'requiredInviteCount', 0,
    'contentFocus', JSON_ARRAY('现代形象', '生活镜头', '职场气质'),
    'miniProgramCard', JSON_OBJECT('enabled', TRUE, 'ratio', '1:1'),
    'poster', JSON_OBJECT('enabled', TRUE, 'ratio', '3:4'),
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
  ),
  1,
  10,
  0,
  NOW(),
  NOW()
) ON DUPLICATE KEY UPDATE
  `template_scene_code` = VALUES(`template_scene_code`),
  `template_name` = VALUES(`template_name`),
  `description` = VALUES(`description`),
  `layout_variant` = VALUES(`layout_variant`),
  `tier` = VALUES(`tier`),
  `required_level` = VALUES(`required_level`),
  `unlock_required` = VALUES(`unlock_required`),
  `base_theme_json` = VALUES(`base_theme_json`),
  `artifact_preset_json` = VALUES(`artifact_preset_json`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `deleted` = VALUES(`deleted`),
  `last_update` = NOW();

INSERT INTO `card_scene_template` (
  `template_code`,
  `template_scene_code`,
  `template_name`,
  `description`,
  `layout_variant`,
  `tier`,
  `required_level`,
  `unlock_required`,
  `base_theme_json`,
  `artifact_preset_json`,
  `status`,
  `sort_no`,
  `deleted`,
  `create_time`,
  `last_update`
) VALUES (
  'COSTUME_TEMPLATE',
  'costume',
  '古风',
  '汉唐衣冠 · 东方韵律',
  'magazine',
  'free',
  1,
  0,
  JSON_OBJECT(
    'themeColors', JSON_OBJECT(
      'primary', '#7d5142',
      'accent', '#c8a36d',
      'background', '#f4eee4',
      'text', '#261b15',
      'heroText', '#fff8ec'
    )
  ),
  JSON_OBJECT(
    'coverImage', '',
    'heroEyebrow', 'GUO FENG',
    'requiredInviteCount', 0,
    'contentFocus', JSON_ARRAY('古装适配', '剧照履历', '角色完成度'),
    'miniProgramCard', JSON_OBJECT('enabled', TRUE, 'ratio', '1:1'),
    'poster', JSON_OBJECT('enabled', TRUE, 'ratio', '3:4'),
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
  ),
  1,
  20,
  0,
  NOW(),
  NOW()
) ON DUPLICATE KEY UPDATE
  `template_scene_code` = VALUES(`template_scene_code`),
  `template_name` = VALUES(`template_name`),
  `description` = VALUES(`description`),
  `layout_variant` = VALUES(`layout_variant`),
  `tier` = VALUES(`tier`),
  `required_level` = VALUES(`required_level`),
  `unlock_required` = VALUES(`unlock_required`),
  `base_theme_json` = VALUES(`base_theme_json`),
  `artifact_preset_json` = VALUES(`artifact_preset_json`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `deleted` = VALUES(`deleted`),
  `last_update` = NOW();

COMMIT;
