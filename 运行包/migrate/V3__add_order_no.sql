-- Additive only. order_no is nullable; not unique (same order pasted twice is the 查重 case).
-- MySQL 8.0: no ADD COLUMN IF NOT EXISTS, gate on information_schema.

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_record'
    AND column_name = 'order_no'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_record ADD COLUMN order_no VARCHAR(64) DEFAULT NULL COMMENT ''订单号/商单号/交易单号，查重用，可空''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_import_item'
    AND column_name = 'order_no'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_import_item ADD COLUMN order_no VARCHAR(64) DEFAULT NULL COMMENT ''订单号/商单号/交易单号''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 't_record'
    AND index_name = 'idx_family_order'
);
SET @sqlstmt := IF(@idx_exists = 0,
  'CREATE INDEX idx_family_order ON t_record (family_id, order_no)',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
