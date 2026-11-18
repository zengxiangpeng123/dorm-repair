-- Shared login throttling for multi-instance deployments.
CREATE TABLE IF NOT EXISTS t_login_attempt (
  attempt_key     VARCHAR(200) NOT NULL,
  failures        INT          NOT NULL DEFAULT 0,
  blocked_until_ms BIGINT      NOT NULL DEFAULT 0,
  updated_at_ms   BIGINT       NOT NULL,
  PRIMARY KEY (attempt_key),
  KEY idx_login_attempt_expiry (blocked_until_ms, updated_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录失败限流';
