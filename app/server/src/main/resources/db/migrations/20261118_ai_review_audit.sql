-- The migration runner selects the target database. Never hard-code a database name here.
CREATE TABLE IF NOT EXISTS t_ai_review_task (
  id             VARCHAR(36)  NOT NULL,
  order_no       VARCHAR(20)  NOT NULL,
  order_version  INT          NOT NULL,
  input_hash     CHAR(64)     NOT NULL,
  model          VARCHAR(100) NOT NULL,
  provider       VARCHAR(40)  NOT NULL,
  status         VARCHAR(12)  NOT NULL COMMENT 'PENDING/RUNNING/SUCCEEDED/FALLBACK/FAILED/SUPERSEDED',
  attempts       INT          NOT NULL DEFAULT 0,
  requested_at   DATETIME     NOT NULL,
  completed_at   DATETIME     NULL,
  error_code     VARCHAR(40)  NULL,
  error_message  VARCHAR(300) NULL,
  category       VARCHAR(20)  NULL,
  level          VARCHAR(10)  NULL,
  confidence     DOUBLE       NULL,
  basis          VARCHAR(300) NULL,
  source         VARCHAR(20)  NULL,
  latency_ms     BIGINT       NOT NULL DEFAULT 0,
  applied        TINYINT(1)   NOT NULL DEFAULT 0,
  applied_at     DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_ai_review_order (order_no, requested_at),
  KEY idx_ai_review_status (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 复核任务审计';

-- 兼容已执行过早期版本的数据库；MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS。
SET @has_latency_ms := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_ai_review_task' AND COLUMN_NAME = 'latency_ms'
);
SET @add_latency_ms := IF(@has_latency_ms = 0,
  'ALTER TABLE t_ai_review_task ADD COLUMN latency_ms BIGINT NOT NULL DEFAULT 0 AFTER source',
  'SELECT 1');
PREPARE stmt FROM @add_latency_ms;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
