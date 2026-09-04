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
--    role 决定数据可见范围，是整个权限模型的基础：
--      0 普通成员   只能看自己名下的流水与统计，看不到资产负债
--      1 户主       可以看全家所有成员的数据，可管理成员与分类
--      2 系统管理员 跨家庭，只用于网站维护与日志查看，不参与记账
--    member_id 把登录账号绑定到具体家庭成员，普通成员的数据隔离就靠它。
--    系统管理员不属于任何家庭，family_id = 0、member_id 为空。
-- ------------------------------------------------------------
CREATE TABLE t_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username     VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    password     VARCHAR(100) NOT NULL                COMMENT '密码（MD5加密存储）',
    real_name    VARCHAR(50)           DEFAULT NULL   COMMENT '真实姓名',
    family_id    BIGINT       NOT NULL                COMMENT '所属家庭ID，系统管理员为 0',
    member_id    BIGINT                DEFAULT NULL   COMMENT '绑定的家庭成员ID，普通成员据此做数据隔离',
    role         TINYINT      NOT NULL DEFAULT 0      COMMENT '角色：0=普通成员 1=户主 2=系统管理员',
    status       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1=正常 0=已禁用（禁用后不能登录）',
    session_version INT       NOT NULL DEFAULT 0      COMMENT '登录会话版本；禁用或改密码时加一，已登录请求对不上即踢下线',
    last_login   DATETIME              DEFAULT NULL   COMMENT '最后登录时间',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_family (family_id)
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
-- 4. 收支分类表（支持三级自定义分类）    【乙 负责的模块】
--    parent_id = 0 表示一级分类；level 冗余存层级，避免统计时反复递归查父级
--    例：文化娱乐(1级) → 影音娱乐(2级) → 游戏充值 / KTV(3级)
--
--    root_id 是本分类所属的一级分类ID（一级分类的 root_id 就是自己）。
--    加这个字段是为了让统计按一级分类汇总时只用一次 JOIN，
--    否则三级分类要连续 JOIN 两次父表才能找到顶级，SQL 会明显变复杂。
--    维护逻辑集中在 CategoryService 里，插入时一次算好。
-- ------------------------------------------------------------
CREATE TABLE t_category (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    family_id      BIGINT      NOT NULL               COMMENT '所属家庭ID',
    parent_id      BIGINT      NOT NULL DEFAULT 0     COMMENT '父分类ID，0表示一级分类',
    root_id        BIGINT      NOT NULL DEFAULT 0     COMMENT '所属一级分类ID，一级分类为自身ID',
    level          TINYINT     NOT NULL DEFAULT 1     COMMENT '层级：1=一级 2=二级 3=三级',
    category_name  VARCHAR(50) NOT NULL               COMMENT '分类名称',
    type           TINYINT     NOT NULL               COMMENT '类型：1=收入 2=支出',
    is_default     TINYINT     NOT NULL DEFAULT 0     COMMENT '是否系统预置：1=预置(不可删) 0=用户自定义',
    sort_no        INT         NOT NULL DEFAULT 0     COMMENT '排序号',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_family_type (family_id, type),
    KEY idx_parent (parent_id),
    KEY idx_root (root_id)
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
    order_no     VARCHAR(64)            DEFAULT NULL  COMMENT '订单号/商单号/交易单号，查重用，可空',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_family_date (family_id, record_date),
    KEY idx_category (category_id),
    KEY idx_member (member_id),
    KEY idx_family_order (family_id, order_no)
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
    symbol       VARCHAR(32)            DEFAULT NULL  COMMENT '股票/基金代码',
    shares       DECIMAL(16,4)          DEFAULT NULL  COMMENT '持仓数量',
    annual_rate  DECIMAL(8,4)           DEFAULT NULL  COMMENT '年利率%存款',
    term_months  INT                    DEFAULT NULL  COMMENT '存期月',
    interest_method VARCHAR(20)         DEFAULT NULL  COMMENT 'simple/compound_year/compound_month',
    car_model    VARCHAR(100)           DEFAULT NULL  COMMENT '车型',
    city         VARCHAR(50)            DEFAULT NULL  COMMENT '城市',
    community    VARCHAR(100)           DEFAULT NULL  COMMENT '小区',
    area_sqm     DECIMAL(10,2)          DEFAULT NULL  COMMENT '面积㎡',
    mileage_km   INT                    DEFAULT NULL  COMMENT '里程km',
    model_year   INT                    DEFAULT NULL  COMMENT '车份',
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

-- ------------------------------------------------------------
-- 8. 操作日志表    【第一批新增，对应需求第 7、8 条】
--    记录谁在什么时候动了哪条数据，以及文件导入的情况。
--    设计取舍：
--      · 只记录"写"操作（新增/修改/删除/导入/登录），不记录查询。
--        查询量是写操作的几十倍，全记会让日志表迅速变成整个库最大的表，
--        而对排查问题几乎没有帮助。
--      · detail 存 JSON 文本而不是拆成一堆列，因为不同模块要记的字段差别很大
--        （流水记金额和分类，导入记文件名和成功条数），拆列会出现大量空字段。
--      · family_id = 0 表示与家庭无关的系统级操作（如管理员登录）。
--    普通成员只能看到自己的日志，户主能看全家，系统管理员看全部。
-- ------------------------------------------------------------
CREATE TABLE t_operation_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    family_id    BIGINT       NOT NULL DEFAULT 0      COMMENT '所属家庭ID，0=系统级操作',
    user_id      BIGINT                DEFAULT NULL   COMMENT '操作人用户ID',
    username     VARCHAR(50)           DEFAULT NULL   COMMENT '操作人账号（冗余，用户删除后日志仍可读）',
    real_name    VARCHAR(50)           DEFAULT NULL   COMMENT '操作人姓名（冗余）',
    module       VARCHAR(20)  NOT NULL                COMMENT '模块：流水/成员/分类/资产/贷款/导入/登录/管理员',
    action       VARCHAR(20)  NOT NULL                COMMENT '动作：新增/修改/删除/导入/登录/退出/重置密码/启用/禁用',
    target_id    BIGINT                DEFAULT NULL   COMMENT '被操作对象的ID',
    summary      VARCHAR(255)          DEFAULT NULL   COMMENT '一句话摘要，日志列表直接显示这一列',
    detail       TEXT                                 COMMENT '详细内容，JSON 文本',
    ip           VARCHAR(50)           DEFAULT NULL   COMMENT '操作来源IP',
    success      TINYINT      NOT NULL DEFAULT 1      COMMENT '是否成功：1=成功 0=失败',
    error_msg    VARCHAR(500)          DEFAULT NULL   COMMENT '失败原因',
    cost_ms      INT                   DEFAULT NULL   COMMENT '耗时（毫秒）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_family_time (family_id, create_time),
    KEY idx_user (user_id),
    KEY idx_module (module, action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

-- ------------------------------------------------------------
-- 9. 文件导入任务    【第二批需求 1：图片 / Excel / PDF → Dify → 待确认 → 入库】
--    智能体只输出结构化 JSON，不直接写 t_record。
-- ------------------------------------------------------------
CREATE TABLE t_import_job (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    family_id    BIGINT       NOT NULL                COMMENT '所属家庭ID',
    user_id      BIGINT       NOT NULL                COMMENT '发起人用户ID',
    member_id    BIGINT       NOT NULL                COMMENT '流水记到哪位成员名下',
    status       VARCHAR(20)  NOT NULL DEFAULT 'queued' COMMENT 'queued/running/preview/importing/done/failed',
    total_files  INT          NOT NULL DEFAULT 0      COMMENT '文件总数',
    done_files   INT          NOT NULL DEFAULT 0      COMMENT '已处理文件数',
    extracted    INT          NOT NULL DEFAULT 0      COMMENT '抽出的候选流水数',
    imported     INT          NOT NULL DEFAULT 0      COMMENT '已确认入库数',
    rejected     INT          NOT NULL DEFAULT 0      COMMENT '判定为无关的文件数',
    message      VARCHAR(500)          DEFAULT NULL   COMMENT '给前端看的摘要',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finish_time  DATETIME              DEFAULT NULL   COMMENT '结束时间',
    PRIMARY KEY (id),
    KEY idx_family_time (family_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件导入任务';

CREATE TABLE t_import_file (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    job_id         BIGINT        NOT NULL                COMMENT '所属任务',
    family_id      BIGINT        NOT NULL                COMMENT '所属家庭ID',
    original_name  VARCHAR(200)  NOT NULL                COMMENT '原始文件名',
    stored_path    VARCHAR(500)           DEFAULT NULL   COMMENT '本机落盘路径',
    content_type   VARCHAR(120)           DEFAULT NULL   COMMENT '上传时的 MIME',
    file_size      BIGINT                 DEFAULT 0      COMMENT '字节数',
    kind           VARCHAR(20)   NOT NULL                COMMENT 'excel/pdf/image/other',
    status         VARCHAR(20)   NOT NULL DEFAULT 'queued' COMMENT 'queued/parsing/ready/rejected/failed',
    progress       INT           NOT NULL DEFAULT 0      COMMENT '0-100',
    reject_reason  VARCHAR(500)           DEFAULT NULL   COMMENT '无关或失败原因',
    extracted      INT           NOT NULL DEFAULT 0      COMMENT '该文件抽出条数',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_job (job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件导入的单个文件';

CREATE TABLE t_import_item (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '候选流水ID',
    job_id         BIGINT        NOT NULL                COMMENT '所属任务',
    file_id        BIGINT        NOT NULL                COMMENT '来源文件',
    family_id      BIGINT        NOT NULL                COMMENT '所属家庭ID',
    status         VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/rejected/skipped',
    reject_reason  VARCHAR(500)           DEFAULT NULL   COMMENT '跳过原因',
    type           TINYINT                DEFAULT NULL   COMMENT '1=收入 2=支出',
    category_name  VARCHAR(50)            DEFAULT NULL   COMMENT '智能体给出的分类名',
    category_id    BIGINT                 DEFAULT NULL   COMMENT '匹配到的末级分类',
    amount         DECIMAL(12,2)          DEFAULT NULL   COMMENT '金额',
    record_date    DATE                   DEFAULT NULL   COMMENT '发生日期',
    merchant       VARCHAR(100)           DEFAULT NULL   COMMENT '商家',
    area           VARCHAR(50)            DEFAULT NULL   COMMENT '片区',
    pay_method     VARCHAR(20)            DEFAULT NULL   COMMENT '支付方式',
    is_gift        TINYINT       NOT NULL DEFAULT 0      COMMENT '是否人情往来',
    remark         VARCHAR(255)           DEFAULT NULL   COMMENT '备注',
    order_no       VARCHAR(64)            DEFAULT NULL   COMMENT '订单号/商单号/交易单号',
    PRIMARY KEY (id),
    KEY idx_job (job_id),
    KEY idx_file (file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件导入抽出的待确认流水';
