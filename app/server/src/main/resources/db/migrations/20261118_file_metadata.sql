-- The migration runner selects the target database. Never hard-code a database name here.
CREATE TABLE IF NOT EXISTS t_file (
  id            VARCHAR(36)  NOT NULL,
  object_key    VARCHAR(300) NOT NULL,
  original_name VARCHAR(200) NULL,
  mime          VARCHAR(100) NOT NULL,
  byte_size     BIGINT       NOT NULL,
  sha256        CHAR(64)     NOT NULL,
  uploaded_by   VARCHAR(20)  NOT NULL,
  purpose       VARCHAR(20)  NOT NULL,
  visibility    VARCHAR(12)  NOT NULL DEFAULT 'PRIVATE',
  order_no      VARCHAR(20)  NULL,
  kind          VARCHAR(10)  NULL,
  deleted_at    DATETIME     NULL,
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_object_key (object_key),
  KEY idx_file_order (order_no, kind),
  KEY idx_file_uploader (uploaded_by, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_work_order_photo' AND COLUMN_NAME='file_id'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE t_work_order_photo ADD COLUMN file_id VARCHAR(36) NULL COMMENT ''上传文件 ID；旧 demo 数据为空''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_work_order_photo' AND INDEX_NAME='idx_photo_file'
);
SET @ddl := IF(@idx_exists=0,
  'ALTER TABLE t_work_order_photo ADD KEY idx_photo_file (file_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
