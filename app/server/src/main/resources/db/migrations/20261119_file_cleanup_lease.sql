-- Adds the orphan-file cleanup lease. Safe for existing installations.
SET @cleanup_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_file' AND COLUMN_NAME='cleanup_claimed_at'
);
SET @ddl := IF(@cleanup_exists=0,
  'ALTER TABLE t_file ADD COLUMN cleanup_claimed_at DATETIME NULL AFTER deleted_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
