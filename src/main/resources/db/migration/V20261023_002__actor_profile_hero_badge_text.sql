-- B12 additive production pre-provision. Run before deploying the B12 backend.
-- No table creation, seed, historical-card rewrite or existing-row mutation.
-- Safe before base-table provisioning and on repeated execution.
SET @hero_badge_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = DATABASE() AND table_name = 'actor_profile')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'actor_profile'
                     AND column_name = 'hero_badge_text'),
    'ALTER TABLE actor_profile ADD COLUMN hero_badge_text VARCHAR(80) NULL COMMENT ''Actor-authored optional hero pill''',
    'SELECT 1');
PREPARE hero_badge_stmt FROM @hero_badge_sql;
EXECUTE hero_badge_stmt;
DEALLOCATE PREPARE hero_badge_stmt;
