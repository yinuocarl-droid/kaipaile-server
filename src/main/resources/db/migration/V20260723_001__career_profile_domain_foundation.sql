-- 00-199 career profile and work-library foundation.
-- Additive and repeatable: every mutation is guarded through INFORMATION_SCHEMA.

SET @table_name = 'actor_profile';

SET @column_name = 'avatar_asset_id';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN avatar_asset_id BIGINT DEFAULT NULL COMMENT ''current avatar asset id''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'current_resume_asset_id';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN current_resume_asset_id BIGINT DEFAULT NULL COMMENT ''current resume PDF asset id''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'birth_year';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN birth_year SMALLINT DEFAULT NULL COMMENT ''birth year''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'birth_month';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN birth_month TINYINT DEFAULT NULL COMMENT ''birth month''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'birth_day';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN birth_day TINYINT DEFAULT NULL COMMENT ''birth day''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'birth_precision';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN birth_precision VARCHAR(16) DEFAULT NULL COMMENT ''year,month,day''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'origin_place';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN origin_place VARCHAR(128) DEFAULT NULL COMMENT ''place of origin''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'school_name';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN school_name VARCHAR(128) DEFAULT NULL COMMENT ''school name''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'major_name';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN major_name VARCHAR(128) DEFAULT NULL COMMENT ''major name''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'language_tags_json';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN language_tags_json JSON DEFAULT NULL COMMENT ''language and dialect tags''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'specialty_tags_json';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN specialty_tags_json JSON DEFAULT NULL COMMENT ''specialty tags''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'role_type_tags_json';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN role_type_tags_json JSON DEFAULT NULL COMMENT ''role type tags''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'professional_ability_tags_json';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN professional_ability_tags_json JSON DEFAULT NULL COMMENT ''professional ability tags''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'work_library_version';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_profile ADD COLUMN work_library_version BIGINT NOT NULL DEFAULT 0 COMMENT ''work library optimistic version''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @table_name = 'actor_experience';

SET @column_name = 'publish_status';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN publish_status VARCHAR(32) DEFAULT NULL COMMENT ''aired,pending,stage,horizontal,other''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'work_type_code';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN work_type_code VARCHAR(32) DEFAULT NULL COMMENT ''normalized work type''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'role_level_code';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN role_level_code VARCHAR(32) DEFAULT NULL COMMENT ''role level''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'sync_sound_status';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN sync_sound_status VARCHAR(16) DEFAULT NULL COMMENT ''sync sound status''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'collaborators_json';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN collaborators_json JSON DEFAULT NULL COMMENT ''collaborator names''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'achievement_text';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN achievement_text TEXT DEFAULT NULL COMMENT ''rankings and playback achievements''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'normalized_drama_name';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN normalized_drama_name VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''normalized project name''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'normalized_role_name';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN normalized_role_name VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''normalized role name''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'dedupe_key';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN dedupe_key VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''normalized project-role hash''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_name = 'source_type';
SET @ddl = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name) = 0, 'ALTER TABLE actor_experience ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT ''manual'' COMMENT ''manual,import,migration''', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS actor_profile_representative_work (
  relation_id BIGINT NOT NULL AUTO_INCREMENT,
  actor_profile_id BIGINT NOT NULL,
  experience_id BIGINT NOT NULL,
  sort_no INT NOT NULL,
  version INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  rid VARCHAR(64) DEFAULT NULL,
  create_user_id BIGINT DEFAULT NULL,
  create_user_name VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_user_id BIGINT DEFAULT NULL,
  update_user_name VARCHAR(64) DEFAULT '',
  last_update DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  active_experience_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN experience_id ELSE NULL END) STORED,
  active_sort_no INT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN sort_no ELSE NULL END) STORED,
  PRIMARY KEY (relation_id),
  UNIQUE KEY uk_profile_representative_active_work (actor_profile_id, active_experience_id),
  UNIQUE KEY uk_profile_representative_active_sort (actor_profile_id, active_sort_no),
  KEY idx_profile_representative_active (actor_profile_id, deleted, sort_no),
  KEY idx_profile_representative_experience (experience_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='profile representative works';
