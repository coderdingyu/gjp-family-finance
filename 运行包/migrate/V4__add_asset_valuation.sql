-- Additive only. t_asset valuation columns for 股票/基金/存款/车辆/房产.
-- Existing rows stay valid: all new columns are nullable.
-- MySQL 8.0 has no ADD COLUMN IF NOT EXISTS (that is MariaDB).
-- Gate on information_schema like V2/V3.

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'symbol'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN symbol VARCHAR(32) NULL COMMENT ''股票/基金代码''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'shares'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN shares DECIMAL(16,4) NULL COMMENT ''持仓数量''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'annual_rate'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN annual_rate DECIMAL(8,4) NULL COMMENT ''年利率%存款''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'term_months'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN term_months INT NULL COMMENT ''存期月''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'interest_method'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN interest_method VARCHAR(20) NULL COMMENT ''simple/compound_year/compound_month''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'car_model'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN car_model VARCHAR(100) NULL COMMENT ''车型''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'city'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN city VARCHAR(50) NULL COMMENT ''城市''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'community'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN community VARCHAR(100) NULL COMMENT ''小区''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'area_sqm'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN area_sqm DECIMAL(10,2) NULL COMMENT ''面积㎡''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'mileage_km'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN mileage_km INT NULL COMMENT ''里程km''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_asset'
    AND column_name = 'model_year'
);
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE t_asset ADD COLUMN model_year INT NULL COMMENT ''车份''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
