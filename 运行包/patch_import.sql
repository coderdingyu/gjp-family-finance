-- 已有库升级：文件导入表。不要整库重建，以免丢掉演示数据。
SET NAMES utf8mb4;
USE gjp;

CREATE TABLE IF NOT EXISTS t_import_job (
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

CREATE TABLE IF NOT EXISTS t_import_file (
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

CREATE TABLE IF NOT EXISTS t_import_item (
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
    PRIMARY KEY (id),
    KEY idx_job (job_id),
    KEY idx_file (file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件导入抽出的待确认流水';
