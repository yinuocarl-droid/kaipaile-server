-- =====================================================================
-- 清理 actor_card_background 风格归类错误（2026-08-14）
-- 背景图库按风格查询时出现的错乱行：
--   ① ancient id=8  与 classic id=1 为同一 URL（重复挂风格）
--   ② fresh  id=10  与 classic id=2 为同一 URL（重复挂风格）
--   ③ fresh  id=9  为 avatar 目录图（非背景图）
-- 处置：删除 3 行错误归属；classic / urban 不受影响。
--
-- 发版兼容（幂等）：
--   - 生产远端（101.43.57.62:3306/kaipai_dev）不存在 actor_card_background 表，
--     直接 DELETE 会因表不存在失败；这里先查 information_schema，
--     表存在才执行 DELETE，不存在则 DO 0 无操作 —— 同一文件任何环境可安全执行。
--   - 已有卡片兼容：actor_card.backgroundImageUrl 存的是 URL 字符串（COS 对象），
--     非本表 id 外键；删除本表行不影响任何已发布/草稿卡的展示。
--   - 幂等：重复执行时目标 id 已不存在，DELETE 影响 0 行，无副作用。
-- =====================================================================
SET @tbl_exists = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'actor_card_background');
SET @cleanup_sql = IF(@tbl_exists > 0, 'DELETE FROM actor_card_background WHERE id IN (8, 9, 10)', 'DO 0');
PREPARE cleanup_stmt FROM @cleanup_sql;
EXECUTE cleanup_stmt;
DEALLOCATE PREPARE cleanup_stmt;
