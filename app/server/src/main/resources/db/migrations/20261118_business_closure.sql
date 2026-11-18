USE dorm_repair;

CREATE TABLE IF NOT EXISTS t_idempotency_record (
  id              VARCHAR(36)  NOT NULL,
  actor_type      VARCHAR(12)  NOT NULL,
  actor_id        VARCHAR(20)  NOT NULL,
  method          VARCHAR(8)   NOT NULL,
  path            VARCHAR(300) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  request_hash    CHAR(64)     NOT NULL,
  status          VARCHAR(12)  NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/COMPLETED',
  response_status INT          NULL,
  response_body   MEDIUMTEXT   NULL,
  created_at      DATETIME     NOT NULL,
  completed_at    DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_idempotency_actor_request (actor_type, actor_id, method, path, idempotency_key),
  KEY idx_idempotency_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键写请求幂等记录';

-- 兼容早期测试表：补充处理中状态与可空响应列。
SET @has_status := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_idempotency_record' AND COLUMN_NAME='status');
SET @sql := IF(@has_status=0,
  'ALTER TABLE t_idempotency_record ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT ''COMPLETED'' AFTER request_hash',
  'SELECT 1');
PREPARE s1 FROM @sql; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @has_completed := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_idempotency_record' AND COLUMN_NAME='completed_at');
SET @sql := IF(@has_completed=0,
  'ALTER TABLE t_idempotency_record ADD COLUMN completed_at DATETIME NULL AFTER created_at',
  'SELECT 1');
PREPARE s2 FROM @sql; EXECUTE s2; DEALLOCATE PREPARE s2;

ALTER TABLE t_idempotency_record MODIFY response_status INT NULL;
ALTER TABLE t_idempotency_record MODIFY response_body MEDIUMTEXT NULL;
