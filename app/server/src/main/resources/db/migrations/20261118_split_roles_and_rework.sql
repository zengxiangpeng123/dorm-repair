-- LEGACY DATABASE ONLY: requires the pre-split t_user schema.
-- Fresh installations must use schema.sql and must not run this file.

-- 2026-11-18: 统一人员表拆分为学生/师傅/管理员，并加入审查表与到期字段。
-- 本脚本用于已有 dorm_repair 库；新库请直接执行 schema.sql。
-- 迁移期保留 t_user 和 t_building.floors，观察一个发布周期后再执行 cleanup 段。

CREATE TABLE IF NOT EXISTS t_student (
  id VARCHAR(20) NOT NULL,
  name VARCHAR(30) NOT NULL,
  phone VARCHAR(20) NULL,
  building_id VARCHAR(10) NOT NULL,
  room VARCHAR(10) NOT NULL,
  password_hash VARCHAR(200) NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_student_location (building_id, room),
  KEY idx_student_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_worker (
  id VARCHAR(20) NOT NULL,
  name VARCHAR(30) NOT NULL,
  phone VARCHAR(20) NULL,
  team VARCHAR(20) NULL,
  password_hash VARCHAR(200) NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_worker_active (active),
  KEY idx_worker_team (team)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_admin (
  id VARCHAR(20) NOT NULL,
  name VARCHAR(30) NOT NULL,
  phone VARCHAR(20) NULL,
  department VARCHAR(40) NULL,
  password_hash VARCHAR(200) NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_admin_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_worker_building (
  worker_id VARCHAR(20) NOT NULL,
  building_id VARCHAR(10) NOT NULL,
  is_primary TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (worker_id, building_id),
  KEY idx_worker_building (building_id, worker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_building_floor (
  building_id VARCHAR(10) NOT NULL,
  floor INT NOT NULL,
  PRIMARY KEY (building_id, floor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- New installations already use the split schema and must not run this legacy copy block.

-- Legacy copy block: execute only when the pre-split t_user table exists.
SET @has_legacy_user := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_user'
);
SET @legacy_sql := IF(@has_legacy_user > 0,
  'ALTER TABLE t_user ADD COLUMN password_hash VARCHAR(200) NULL',
  'SELECT 1');
PREPARE legacy_stmt FROM @legacy_sql;
EXECUTE legacy_stmt;
DEALLOCATE PREPARE legacy_stmt;

-- The following data-copy statements are intentionally reached only for legacy databases.
INSERT INTO t_student (id,name,phone,building_id,room,password_hash,active,created_at)
SELECT id,name,phone,building_id,room,password_hash,active,created_at
FROM t_user WHERE role='STUDENT'
ON DUPLICATE KEY UPDATE name=VALUES(name),phone=VALUES(phone),building_id=VALUES(building_id),
  room=VALUES(room),password_hash=VALUES(password_hash),active=VALUES(active);

INSERT INTO t_worker (id,name,phone,team,password_hash,active,created_at)
SELECT id,name,phone,team,password_hash,active,created_at
FROM t_user WHERE role='WORKER'
ON DUPLICATE KEY UPDATE name=VALUES(name),phone=VALUES(phone),team=VALUES(team),
  password_hash=VALUES(password_hash),active=VALUES(active);

INSERT INTO t_admin (id,name,phone,password_hash,active,created_at)
SELECT id,name,phone,password_hash,active,created_at
FROM t_user WHERE role='ADMIN'
ON DUPLICATE KEY UPDATE name=VALUES(name),phone=VALUES(phone),
  password_hash=VALUES(password_hash),active=VALUES(active);

INSERT INTO t_worker_building (worker_id,building_id,is_primary)
SELECT id,building_id,1 FROM t_user
WHERE role='WORKER' AND building_id IS NOT NULL
ON DUPLICATE KEY UPDATE is_primary=VALUES(is_primary);

INSERT IGNORE INTO t_building_floor (building_id,floor) VALUES
  ('B7',2),('B7',3),('B7',4),('B7',5),('B8',1),('B8',2),('B8',3);

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='t_work_order' AND column_name='verify_due_at'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE t_work_order ADD COLUMN verify_due_at DATETIME NULL COMMENT ''T1 到期时间''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='t_work_order' AND column_name='archive_due_at'
);
SET @ddl := IF(@col_exists=0,
  'ALTER TABLE t_work_order ADD COLUMN archive_due_at DATETIME NULL COMMENT ''T2 到期时间''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE t_work_order
SET verify_due_at = DATE_ADD(finish_at, INTERVAL (SELECT CAST(v AS UNSIGNED) FROM t_config WHERE k='verifyHours' LIMIT 1) HOUR)
WHERE status='VERIFYING' AND finish_at IS NOT NULL AND verify_due_at IS NULL;

UPDATE t_work_order
SET archive_due_at = DATE_ADD(sign_at, INTERVAL (SELECT CAST(v AS UNSIGNED) FROM t_config WHERE k='archiveDays' LIMIT 1) DAY)
WHERE status='DONE' AND sign_at IS NOT NULL AND archive_due_at IS NULL;

CREATE TABLE IF NOT EXISTS t_rework_review (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(20) NOT NULL,
  attempt_no INT NOT NULL,
  rework_index INT NULL,
  status VARCHAR(12) NOT NULL,
  requested_by VARCHAR(20) NOT NULL,
  request_reason VARCHAR(300) NOT NULL,
  requested_at DATETIME NOT NULL,
  decided_by VARCHAR(20) NULL,
  decision_reason VARCHAR(300) NULL,
  decided_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_attempt (order_no, attempt_no),
  UNIQUE KEY uk_review_rework (order_no, rework_index),
  KEY idx_rework_queue (status, requested_at),
  KEY idx_rework_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_auth_session (
  token_hash    CHAR(64)     NOT NULL,
  account       VARCHAR(20)  NOT NULL,
  role          VARCHAR(12)  NOT NULL,
  name          VARCHAR(30)  NOT NULL,
  account_id    VARCHAR(20)  NOT NULL,
  issued_at_ms  BIGINT       NOT NULL,
  expires_at_ms BIGINT       NOT NULL,
  revoked_at_ms BIGINT       NULL,
  PRIMARY KEY (token_hash),
  KEY idx_auth_account (account),
  KEY idx_auth_expiry (expires_at_ms),
  KEY idx_auth_active (revoked_at_ms, expires_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE OR REPLACE VIEW v_user_account AS
SELECT id,'STUDENT' AS role,name,NULL AS team,building_id,room,phone,password_hash,active,created_at FROM t_student
UNION ALL
SELECT id,'WORKER' AS role,name,team,NULL AS building_id,NULL AS room,phone,password_hash,active,created_at FROM t_worker
UNION ALL
SELECT id,'ADMIN' AS role,name,NULL AS team,NULL AS building_id,NULL AS room,phone,password_hash,active,created_at FROM t_admin;

-- cleanup（观察一个发布周期后手工执行）：
-- DROP VIEW v_user_account;
-- ALTER TABLE t_building DROP COLUMN floors;
-- DROP TABLE t_user;