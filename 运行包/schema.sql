-- ============================================================
-- 《管家婆 — 家庭收支管理系统》数据库建表脚本
-- 负责人：甲（组长）。其他成员不要修改本文件，需要加字段请找组长。
-- 执行方式：在 Navicat / MySQL 命令行中整个文件执行一次即可
-- ============================================================

-- 客户端字符集：不加这一行，用 `mysql -u root < schema.sql` 导入时
-- 中文注释和中文数据会被按 latin1 解析，出现乱码。
SET NAMES utf8mb4;

DROP DATABASE IF EXISTS gjp;
CREATE DATABASE gjp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE gjp;

-- ------------------------------------------------------------
-- 1. 家庭表
-- ------------------------------------------------------------
CREATE TABLE t_family (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '家庭ID',
    family_name  VARCHAR(50)  NOT NULL                COMMENT '家庭名称，如“张家”',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '家庭表';

-- ------------------------------------------------------------
-- 2. 用户表（登录账号）
-- ------------------------------------------------------------
CREATE TABLE t_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username     VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    password     VARCHAR(100) NOT NULL                COMMENT '密码（MD5加密存储）',
    real_name    VARCHAR(50)           DEFAULT NULL   COMMENT '真实姓名',
    family_id    BIGINT       NOT NULL                COMMENT '所属家庭ID',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ------------------------------------------------------------
-- 3. 家庭成员表    【乙 负责的模块】
-- ------------------------------------------------------------
CREATE TABLE t_member (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '成员ID',
    family_id       BIGINT        NOT NULL               COMMENT '所属家庭ID',
    member_name     VARCHAR(50)   NOT NULL               COMMENT '成员姓名',
    relation        VARCHAR(20)            DEFAULT NULL  COMMENT '家庭关系：本人/配偶/子女/父母/其他',
    monthly_budget  DECIMAL(12,2)          DEFAULT 0.00  COMMENT '月度预算金额',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_family (family_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '家庭成员表';

-- ------------------------------------------------------------
-- 4. 收支分类表（支持二级自定义分类）    【乙 负责的模块】
--    parent_id = 0 表示一级分类
-- ------------------------------------------------------------
CREATE TABLE t_category (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    family_id      BIGINT      NOT NULL               COMMENT '所属家庭ID',
    parent_id      BIGINT      NOT NULL DEFAULT 0     COMMENT '父分类ID，0表示一级分类',
    category_name  VARCHAR(50) NOT NULL               COMMENT '分类名称',
    type           TINYINT     NOT NULL               COMMENT '类型：1=收入 2=支出',
    is_default     TINYINT     NOT NULL DEFAULT 0     COMMENT '是否系统预置：1=预置(不可删) 0=用户自定义',
    sort_no        INT         NOT NULL DEFAULT 0     COMMENT '排序号',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_family_type (family_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收支分类表';

-- ------------------------------------------------------------
-- 5. 收支流水表（核心表）    【丙 负责的模块】
--    注意：merchant / area / is_gift 这三个字段是统计分析出彩的关键，
--    对应课程要求第 ⑤ 条（录入时应考虑多种因素），不能省略。
-- ------------------------------------------------------------
CREATE TABLE t_record (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    family_id    BIGINT        NOT NULL               COMMENT '所属家庭ID',
    member_id    BIGINT        NOT NULL               COMMENT '所属成员ID',
    category_id  BIGINT        NOT NULL               COMMENT '分类ID',
    type         TINYINT       NOT NULL               COMMENT '类型：1=收入 2=支出',
    amount       DECIMAL(12,2) NOT NULL               COMMENT '金额',
    record_date  DATE          NOT NULL               COMMENT '发生日期',
    merchant     VARCHAR(100)           DEFAULT NULL  COMMENT '商家名称，如“海底捞”',
    area         VARCHAR(50)            DEFAULT NULL  COMMENT '消费片区，如“城东”',
    pay_method   VARCHAR(20)            DEFAULT NULL  COMMENT '支付方式：现金/微信/支付宝/银行卡',
    is_gift      TINYINT       NOT NULL DEFAULT 0     COMMENT '是否人情往来：1=是 0=否',
    remark       VARCHAR(255)           DEFAULT NULL  COMMENT '备注',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_family_date (family_id, record_date),
    KEY idx_category (category_id),
    KEY idx_member (member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收支流水表';

-- ------------------------------------------------------------
-- 6. 资产表（拓展功能）    【戊 负责，时间不够可砍】
-- ------------------------------------------------------------
CREATE TABLE t_asset (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '资产ID',
    family_id    BIGINT        NOT NULL               COMMENT '所属家庭ID',
    asset_name   VARCHAR(100)  NOT NULL               COMMENT '资产名称',
    asset_type   VARCHAR(20)   NOT NULL               COMMENT '资产类型：房产/车辆/存款/股票/基金/其他',
    amount       DECIMAL(14,2) NOT NULL               COMMENT '当前价值（元）',
    cost         DECIMAL(14,2)          DEFAULT NULL  COMMENT '取得成本（元）',
    buy_date     DATE                   DEFAULT NULL  COMMENT '取得日期',
    remark       VARCHAR(255)           DEFAULT NULL  COMMENT '备注',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_family (family_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '资产表';

-- ------------------------------------------------------------
-- 7. 贷款表（拓展功能）    【戊 负责，时间不够可砍】
-- ------------------------------------------------------------
CREATE TABLE t_loan (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '贷款ID',
    family_id        BIGINT        NOT NULL               COMMENT '所属家庭ID',
    loan_name        VARCHAR(100)  NOT NULL               COMMENT '贷款名称',
    loan_type        VARCHAR(20)   NOT NULL               COMMENT '贷款类型：房贷/车贷/消费贷',
    total_amount     DECIMAL(14,2) NOT NULL               COMMENT '贷款总额',
    monthly_payment  DECIMAL(12,2) NOT NULL               COMMENT '每月还款额',
    total_months     INT           NOT NULL               COMMENT '总期数（月）',
    paid_months      INT           NOT NULL DEFAULT 0     COMMENT '已还期数',
    start_date       DATE                   DEFAULT NULL  COMMENT '起始还款日',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_family (family_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '贷款表';
