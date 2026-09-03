-- Additive only. MySQL 8.0 has no ADD COLUMN IF NOT EXISTS (that is MariaDB).
-- Existing rows get session_version = 0 via DEFAULT.
SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND column_name = 'session_version'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_user ADD COLUMN session_version INT NOT NULL DEFAULT 0 COMMENT ''登录会话版本；禁用账号时加一，已登录请求对不上即踢下线''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
