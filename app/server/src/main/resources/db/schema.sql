-- ============================================================
-- 宿舍维修系统 · 数据库结构（MySQL 8.0 / utf8mb4 / InnoDB）
-- 规矩：不用外键（应用层保证）、不用 JSON 列装业务数据、时间统一 DATETIME
-- 表前缀 t_，字段 snake_case；接口出参 camelCase 由 MyBatis-Plus 映射
-- ============================================================

-- The database must be created and selected by the deployment operator.
-- Example: CREATE DATABASE dorm_repair CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 1 人员（学生 / 师傅 / 管理员分表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_student (
  id            VARCHAR(20)  NOT NULL COMMENT '学号',
  name          VARCHAR(30)  NOT NULL,
  phone         VARCHAR(20)  NULL,
  building_id   VARCHAR(10)  NOT NULL,
  room          VARCHAR(10)  NOT NULL,
  password_hash VARCHAR(200) NULL,
  active        TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_student_location (building_id, room),
  KEY idx_student_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生';

CREATE TABLE IF NOT EXISTS t_worker (
  id            VARCHAR(20)  NOT NULL COMMENT '工号',
  name          VARCHAR(30)  NOT NULL,
  phone         VARCHAR(20)  NULL,
  team          VARCHAR(20)  NULL COMMENT '水电班 / 综合班',
  password_hash VARCHAR(200) NULL,
  active        TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_worker_active (active),
  KEY idx_worker_team (team)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修师傅';

CREATE TABLE IF NOT EXISTS t_admin (
  id            VARCHAR(20)  NOT NULL COMMENT '管理员账号',
  name          VARCHAR(30)  NOT NULL,
  phone         VARCHAR(20)  NULL,
  department    VARCHAR(40)  NULL,
  password_hash VARCHAR(200) NULL,
  active        TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_admin_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员';

CREATE TABLE IF NOT EXISTS t_worker_building (
  worker_id   VARCHAR(20) NOT NULL,
  building_id VARCHAR(10) NOT NULL,
  is_primary  TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (worker_id, building_id),
  KEY idx_worker_building (building_id, worker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='师傅可服务楼栋';

CREATE OR REPLACE VIEW v_user_account AS
SELECT id, 'STUDENT' AS role, name, NULL AS team, building_id, room, phone,
       password_hash, active, created_at
FROM t_student
UNION ALL
SELECT id, 'WORKER' AS role, name, team, NULL AS building_id, NULL AS room, phone,
       password_hash, active, created_at
FROM t_worker
UNION ALL
SELECT id, 'ADMIN' AS role, name, NULL AS team, NULL AS building_id, NULL AS room, phone,
       password_hash, active, created_at
FROM t_admin;

-- ------------------------------------------------------------
-- 1.1 认证会话（只保存令牌摘要，不保存 Bearer 原文）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_auth_session (
  token_hash    CHAR(64)     NOT NULL COMMENT 'SHA-256(token)',
  account       VARCHAR(20)  NOT NULL,
  role          VARCHAR(12)  NOT NULL COMMENT 'STUDENT/WORKER/ADMIN/SYSTEM',
  name          VARCHAR(30)  NOT NULL,
  account_id    VARCHAR(20)  NOT NULL,
  issued_at_ms  BIGINT       NOT NULL,
  expires_at_ms BIGINT       NOT NULL,
  revoked_at_ms BIGINT       NULL,
  PRIMARY KEY (token_hash),
  KEY idx_auth_account (account),
  KEY idx_auth_expiry (expires_at_ms),
  KEY idx_auth_active (revoked_at_ms, expires_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证会话';

-- ------------------------------------------------------------
-- 2 楼栋与楼层
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_building (
  id        VARCHAR(10) NOT NULL COMMENT 'B7',
  name      VARCHAR(30) NOT NULL COMMENT '东区7号楼',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼栋';

CREATE TABLE IF NOT EXISTS t_building_floor (
  building_id VARCHAR(10) NOT NULL,
  floor       INT         NOT NULL,
  PRIMARY KEY (building_id, floor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼栋楼层';

-- ------------------------------------------------------------
-- 3 工单（核心表，状态只能由 OrderStateService.transit 改）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_work_order (
  no             VARCHAR(20)  NOT NULL COMMENT 'BX+YYYYMMDD+3位，返修单后缀 -R1',
  status         VARCHAR(12)  NOT NULL COMMENT 'PENDING/ASSIGNED/REPAIRING/PARTS/VERIFYING/REWORKING/DONE/CLOSED/CANCELLED',
  version        INT          NOT NULL DEFAULT 1 COMMENT '乐观锁：每次变更 +1',
  building_id    VARCHAR(10)  NOT NULL,
  floor          INT          NOT NULL,
  room           VARCHAR(10)  NOT NULL,
  student_id     VARCHAR(20)  NOT NULL,
  student_name   VARCHAR(30)  NOT NULL,
  description    VARCHAR(500) NOT NULL COMMENT '学生写的描述，≥4 字',
  visit_window   VARCHAR(40)  NOT NULL COMMENT '可上门时段：全天/上午/下午/晚上',
  category       VARCHAR(20)  NOT NULL COMMENT '给排水/电气/电器/门窗家具/网络',
  level          VARCHAR(10)  NOT NULL COMMENT '紧急/较急/普通',
  level_source   VARCHAR(10)  NOT NULL COMMENT 'AI / DEFAULT / HUMAN',
  ai_basis       VARCHAR(300) NULL COMMENT 'AI 判断依据，给学生看，不许改',
  worker_id      VARCHAR(20)  NULL,
  worker_name    VARCHAR(30)  NULL,
  parts_name     VARCHAR(50)  NULL COMMENT '缺件名（工单上的快照，明细在 t_part_request）',
  parts_amount   DECIMAL(10,2) NULL COMMENT '缺件金额',
  parts_eta      DATE         NULL COMMENT '预计到货日',
  rework_of      VARCHAR(20)  NULL COMMENT '返修单指向原单',
  rework_index   INT          NULL COMMENT '第几次返修',
  rework_count   INT          NOT NULL DEFAULT 0 COMMENT '原单累计返修次数，≥2 进例外清单',
  no_show_count  INT          NOT NULL DEFAULT 0,
  cancel_reason  VARCHAR(200) NULL COMMENT '学生申请撤销的原因（进第四类例外）',
  cancel_at      DATETIME     NULL,
  created_at     DATETIME     NOT NULL,
  assigned_at    DATETIME     NULL,
  arrive_at      DATETIME     NULL COMMENT '到门打卡；撤销边界就看它有没有值',
  finish_at      DATETIME     NULL COMMENT 'T1 从这算 168 小时',
  sign_at        DATETIME     NULL COMMENT 'T2 从这算 7 天',
  closed_at      DATETIME     NULL,
  cancelled_at   DATETIME     NULL,
  status_at      DATETIME     NULL,
  verify_due_at  DATETIME     NULL COMMENT 'T1 到期时间',
  archive_due_at DATETIME     NULL COMMENT 'T2 到期时间',
  PRIMARY KEY (no),
  KEY idx_wo_status (status),
  KEY idx_wo_building (building_id, status),
  KEY idx_wo_worker (worker_id, status),
  KEY idx_wo_student (student_id, created_at),
  KEY idx_wo_rework (rework_of)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单';

-- ------------------------------------------------------------
-- 4 工单事件（时间线，只增不改）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_work_order_event (
  seq        BIGINT       NOT NULL AUTO_INCREMENT,
  order_no   VARCHAR(20)  NOT NULL,
  at         DATETIME     NOT NULL,
  event      VARCHAR(30)  NOT NULL COMMENT '状态事件 + CANCEL_REQUEST/REWORK_REQUEST/REWORK_REJECTED/ORIGIN_CLOSED',
  actor_type VARCHAR(10)  NOT NULL COMMENT 'STUDENT/WORKER/ADMIN/SYSTEM',
  actor_id   VARCHAR(20)  NOT NULL,
  actor_name VARCHAR(30)  NOT NULL,
  manual     TINYINT(1)   NOT NULL COMMENT '0=系统自动 1=人推的。零人工完成率全靠它',
  note       VARCHAR(300) NULL,
  PRIMARY KEY (seq),
  KEY idx_evt_order (order_no, seq),
  KEY idx_evt_manual (manual)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单事件（只增不改）';

-- ------------------------------------------------------------
-- 5 工单照片（现场照 / 完工照）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_work_order_photo (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  order_no  VARCHAR(20) NOT NULL,
  kind      VARCHAR(10) NOT NULL COMMENT 'BEFORE 现场 / AFTER 完工',
  url       VARCHAR(300) NOT NULL,
  file_id   VARCHAR(36) NULL COMMENT '上传文件 ID；旧 demo:// 数据为空',
  uploaded_by VARCHAR(20) NOT NULL,
  created_at DATETIME   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_photo_order (order_no, kind),
  KEY idx_photo_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单照片';

-- ------------------------------------------------------------
-- 5.1 文件元数据（对象存储只存字节，权限与工单归属在这里）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_file (
  id            VARCHAR(36)  NOT NULL,
  object_key    VARCHAR(300) NOT NULL,
  original_name VARCHAR(200) NULL,
  mime          VARCHAR(100) NOT NULL,
  byte_size     BIGINT       NOT NULL,
  sha256        CHAR(64)     NOT NULL,
  uploaded_by   VARCHAR(20)  NOT NULL,
  purpose       VARCHAR(20)  NOT NULL COMMENT 'BEFORE/AFTER',
  visibility    VARCHAR(12)  NOT NULL DEFAULT 'PRIVATE',
  order_no      VARCHAR(20)  NULL,
  kind          VARCHAR(10)  NULL COMMENT 'BEFORE/AFTER',
  deleted_at    DATETIME     NULL,
  cleanup_claimed_at DATETIME NULL,
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_object_key (object_key),
  KEY idx_file_order (order_no, kind),
  KEY idx_file_uploader (uploaded_by, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据';

-- ------------------------------------------------------------
-- 5.2 AI 复核任务（只记录任务事实；结果应用由工单 CAS 决定）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 6 配件台账（老师要求第 5 张业务表：配件与领用）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_part (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  name      VARCHAR(50)  NOT NULL COMMENT '软管 / 角阀 / 面板模块',
  spec      VARCHAR(60)  NULL,
  unit      VARCHAR(10)  NOT NULL DEFAULT '个',
  price     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  stock     INT          NOT NULL DEFAULT 0,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_part_name (name),
  KEY idx_part_stock (stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配件台账';

-- ------------------------------------------------------------
-- 7 配件申请（缺件登记）—— 预算线在这里判
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_part_request (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  order_no    VARCHAR(20)   NOT NULL,
  part_id     BIGINT        NULL COMMENT '能对上台账就填',
  part_name   VARCHAR(50)   NOT NULL,
  amount      DECIMAL(10,2) NOT NULL COMMENT '金额，管理端按预算线 200 判超没超',
  eta_date    DATE          NOT NULL,
  over_budget TINYINT(1)    NOT NULL DEFAULT 0,
  status      VARCHAR(12)   NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED/APPROVED/ARRIVED/REJECTED',
  created_by  VARCHAR(20)   NOT NULL,
  created_at  DATETIME      NOT NULL,
  arrived_at  DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_pr_order (order_no),
  KEY idx_pr_status (status),
  KEY idx_pr_over (over_budget)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配件申请';

-- ------------------------------------------------------------
-- 8 返修审查（每次申请独立一行）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_rework_review (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  order_no        VARCHAR(20)  NOT NULL,
  attempt_no      INT          NOT NULL COMMENT '同一原单第几次申请返修',
  rework_index    INT          NULL COMMENT '批准后写入第几次返修，申请时为 NULL',
  status          VARCHAR(12)  NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
  requested_by    VARCHAR(20)  NOT NULL COMMENT '学生 ID',
  request_reason  VARCHAR(300) NOT NULL,
  requested_at    DATETIME     NOT NULL,
  decided_by      VARCHAR(20)  NULL COMMENT '管理员 ID',
  decision_reason VARCHAR(300) NULL,
  decided_at      DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_attempt (order_no, attempt_no),
  UNIQUE KEY uk_review_rework (order_no, rework_index),
  KEY idx_rework_queue (status, requested_at),
  KEY idx_rework_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返修审查';

-- ------------------------------------------------------------
-- 9 例外清单（三类不占状态位 + 第四类申请撤销）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_exception_record (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  kind       VARCHAR(20)  NOT NULL COMMENT 'MISMATCH/OVER_BUDGET/REPEAT_REWORK/CANCEL_REQUEST',
  order_no   VARCHAR(20)  NOT NULL,
  detail     VARCHAR(500) NULL,
  status     VARCHAR(12)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  handled_by VARCHAR(20)  NULL,
  created_at DATETIME     NOT NULL,
  closed_at  DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_ex_kind (kind, status),
  KEY idx_ex_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='例外清单';

-- ------------------------------------------------------------
-- 10 配置（阈值不许写死在代码里）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_config (
  k          VARCHAR(40) NOT NULL,
  v          VARCHAR(100) NOT NULL,
  note       VARCHAR(200) NULL,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置：预算线、验收小时、归档天数';

-- ------------------------------------------------------------
-- 11 关键写请求幂等（actor + method + path + key 唯一）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 12 单号序号（按天递增，避免并发撞号）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_seq (
  day      VARCHAR(8) NOT NULL COMMENT 'yyyyMMdd',
  name     VARCHAR(10) NOT NULL DEFAULT 'BX',
  next_val INT        NOT NULL DEFAULT 1,
  PRIMARY KEY (day, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单号序号';
