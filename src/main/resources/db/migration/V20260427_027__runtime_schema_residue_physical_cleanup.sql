-- Archive runtime cleanup shadow tables to MySQL secure-file storage, then remove them from the active schema.
-- Also align current column comments with the active crew-domain contract.

DROP PROCEDURE IF EXISTS kp_tmp_runtime_residue_archive_drop;

DELIMITER $$
CREATE PROCEDURE kp_tmp_runtime_residue_archive_drop()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE table_name_value VARCHAR(255);
  DECLARE table_index INT DEFAULT 0;
  DECLARE row_count_value BIGINT DEFAULT 0;
  DECLARE archive_prefix VARCHAR(255);

  DECLARE cursor_tables CURSOR FOR
    SELECT TABLE_NAME
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND (
        TABLE_NAME LIKE 'zz\_bak\_%' ESCAPE '\\'
        OR TABLE_NAME LIKE 'zz\_backup\_%' ESCAPE '\\'
      )
    ORDER BY TABLE_NAME;

  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  SET archive_prefix = CONCAT(
    '/var/lib/mysql-files/kplyyk_runtime_residue_',
    DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'),
    '_'
  );

  CREATE TEMPORARY TABLE IF NOT EXISTS kp_tmp_runtime_residue_manifest (
    archive_index INT NOT NULL,
    source_table VARCHAR(255) NOT NULL,
    source_rows BIGINT NOT NULL,
    archive_file VARCHAR(512) NOT NULL
  );
  TRUNCATE TABLE kp_tmp_runtime_residue_manifest;

  OPEN cursor_tables;

  read_loop: LOOP
    FETCH cursor_tables INTO table_name_value;
    IF done = 1 THEN
      LEAVE read_loop;
    END IF;

    SET table_index = table_index + 1;
    SET @quoted_table = CONCAT('`', REPLACE(table_name_value, '`', '``'), '`');
    SET @archive_file = CONCAT(archive_prefix, LPAD(table_index, 3, '0'), '.tsv');

    SET @count_sql = CONCAT('SELECT COUNT(*) INTO @kp_tmp_row_count FROM ', @quoted_table);
    PREPARE stmt FROM @count_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    SET row_count_value = @kp_tmp_row_count;

    INSERT INTO kp_tmp_runtime_residue_manifest (
      archive_index,
      source_table,
      source_rows,
      archive_file
    )
    VALUES (
      table_index,
      table_name_value,
      row_count_value,
      @archive_file
    );

    SET @export_sql = CONCAT(
      'SELECT * FROM ', @quoted_table,
      ' INTO OUTFILE ', QUOTE(@archive_file),
      ' CHARACTER SET utf8mb4 FIELDS TERMINATED BY ''\t'' OPTIONALLY ENCLOSED BY ''"'' ESCAPED BY ''\\\\'' LINES TERMINATED BY ''\n'''
    );
    PREPARE stmt FROM @export_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @drop_sql = CONCAT('DROP TABLE ', @quoted_table);
    PREPARE stmt FROM @drop_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;

  CLOSE cursor_tables;

  SET @manifest_file = CONCAT(archive_prefix, 'manifest.tsv');
  SET @manifest_sql = CONCAT(
    'SELECT archive_index, source_table, source_rows, archive_file FROM kp_tmp_runtime_residue_manifest ',
    'INTO OUTFILE ', QUOTE(@manifest_file),
    ' CHARACTER SET utf8mb4 FIELDS TERMINATED BY ''\t'' OPTIONALLY ENCLOSED BY ''"'' ESCAPED BY ''\\\\'' LINES TERMINATED BY ''\n'''
  );
  PREPARE stmt FROM @manifest_sql;
  EXECUTE stmt;
  DEALLOCATE PREPARE stmt;
END$$
DELIMITER ;

CALL kp_tmp_runtime_residue_archive_drop();

DROP PROCEDURE IF EXISTS kp_tmp_runtime_residue_archive_drop;

ALTER TABLE `crew_profile`
  MODIFY COLUMN `crew_no` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'crew profile number',
  MODIFY COLUMN `crew_name` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'crew profile name',
  MODIFY COLUMN `crew_short_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'crew profile short name',
  MODIFY COLUMN `crew_type` TINYINT DEFAULT NULL COMMENT '1 media crew, 2 production crew, 3 casting team, 4 agency team',
  MODIFY COLUMN `license_no` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'business license code',
  MODIFY COLUMN `intro` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT 'crew profile intro',
  MODIFY COLUMN `is_certified` BIT(1) NOT NULL DEFAULT b'0' COMMENT 'profile certification flag';

ALTER TABLE `cooperation_order`
  MODIFY COLUMN `crew_user_id` BIGINT NOT NULL COMMENT 'crew user id',
  MODIFY COLUMN `crew_profile_id` BIGINT NOT NULL COMMENT 'crew profile id';

ALTER TABLE `recruit_post`
  MODIFY COLUMN `crew_profile_id` BIGINT NOT NULL COMMENT 'crew profile id';

ALTER TABLE `intermediary_actor`
  MODIFY COLUMN `intermediary_profile_id` BIGINT NOT NULL COMMENT 'intermediary profile id';

ALTER TABLE `user`
  MODIFY COLUMN `user_type` TINYINT NOT NULL COMMENT '1 actor, 2 crew, 3 platform admin';
