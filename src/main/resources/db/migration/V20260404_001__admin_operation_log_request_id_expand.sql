ALTER TABLE `admin_operation_log`
  MODIFY COLUMN `request_id` VARCHAR(128) DEFAULT NULL COMMENT 'request id';
