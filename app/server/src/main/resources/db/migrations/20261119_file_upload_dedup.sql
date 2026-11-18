-- 同一上传者、用途和内容摘要只保留一条未绑定记录；绑定后生成列变为 NULL，不再参与唯一约束。
-- 先清理历史重复，再建生成列与唯一索引，保证旧库升级可重复执行。
DELETE older
FROM t_file older
JOIN t_file newer
  ON older.order_no IS NULL
 AND newer.order_no IS NULL
 AND older.uploaded_by = newer.uploaded_by
 AND older.purpose = newer.purpose
 AND older.sha256 = newer.sha256
 AND (older.created_at > newer.created_at OR (older.created_at = newer.created_at AND older.id > newer.id));

SET @digest_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_file' AND COLUMN_NAME='unbound_digest'
);
SET @ddl := IF(@digest_exists=0,
  'ALTER TABLE t_file ADD COLUMN unbound_digest VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN order_no IS NULL THEN CONCAT(uploaded_by, '':'', purpose, '':'', sha256) ELSE NULL END) STORED',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @digest_idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_file' AND INDEX_NAME='uk_file_unbound_digest'
);
SET @ddl := IF(@digest_idx_exists=0,
  'ALTER TABLE t_file ADD UNIQUE KEY uk_file_unbound_digest (unbound_digest)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
