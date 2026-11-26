-- 00-267: 演员卡风格统一为后台「风格模板」场景码。
-- 旧版小程序写死的 ancient 对应模板 costume（名称「古风」）。只改取值、不改表结构、不删数据。
-- 幂等：重复执行时 WHERE 不再命中任何行；表尚未预置时跳过。
-- last_update 带 ON UPDATE CURRENT_TIMESTAMP，显式写回原值，避免名片夹排序与「更新于」被迁移改动。
-- fresh 等无对应模板的旧值保留，由运营在后台改归。后端读取侧已兼容 ancient，迁移可先可后。
SET @bg_style_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = DATABASE() AND table_name = 'actor_card_background'),
    'UPDATE actor_card_background SET style = ''costume'', last_update = last_update WHERE style = ''ancient''',
    'SELECT 1');
PREPARE bg_style_stmt FROM @bg_style_sql;
EXECUTE bg_style_stmt;
DEALLOCATE PREPARE bg_style_stmt;

SET @card_style_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = DATABASE() AND table_name = 'actor_card'),
    'UPDATE actor_card SET style = ''costume'', last_update = last_update WHERE style = ''ancient''',
    'SELECT 1');
PREPARE card_style_stmt FROM @card_style_sql;
EXECUTE card_style_stmt;
DEALLOCATE PREPARE card_style_stmt;
