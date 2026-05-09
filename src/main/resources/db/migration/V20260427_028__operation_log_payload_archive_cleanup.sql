-- Archive historical operation-log payloads, then clear runtime payload columns.
-- The list view and detail view keep working; historical payload bodies are kept in secure-file storage.

SET @archive_file = CONCAT(
  '/var/lib/mysql-files/kplyyk_operation_log_payload_',
  DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f'),
  '.tsv'
);

SET @archive_sql = CONCAT(
  'SELECT `operation_log_id`, `before_snapshot_json`, `after_snapshot_json`, `extra_context_json` ',
  'FROM `admin_operation_log` ',
  'WHERE `before_snapshot_json` IS NOT NULL ',
  'OR `after_snapshot_json` IS NOT NULL ',
  'OR `extra_context_json` IS NOT NULL ',
  'INTO OUTFILE ', QUOTE(@archive_file),
  ' CHARACTER SET utf8mb4 FIELDS TERMINATED BY ''\t'' OPTIONALLY ENCLOSED BY ''"'' ESCAPED BY ''\\\\'' LINES TERMINATED BY ''\n'''
);

PREPARE stmt FROM @archive_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `admin_operation_log`
SET
  `before_snapshot_json` = NULL,
  `after_snapshot_json` = NULL,
  `extra_context_json` = NULL
WHERE `before_snapshot_json` IS NOT NULL
   OR `after_snapshot_json` IS NOT NULL
   OR `extra_context_json` IS NOT NULL;
