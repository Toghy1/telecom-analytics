-- ===========================================================================
-- demo01 · P1 电信用户行为分析系统
-- 建表脚本：业务 7 张（t_*）+ 行为数据 4 张，共 11 张
--
-- 口径来源：《开发文档》§5.1.2（公共字段）、§5.2（业务表）、§5.3（行为表）
--
-- ⚠️ 本脚本会先 DROP 再建，**会清空 demo01 库里的数据**。
--    它只用于本地开发初始化；重复执行 = 重来一遍。
--
-- 约定：
--   · 字符集统一 utf8mb4（省份名、中文套餐名都要能存）
--   · 金额 / 额度统一 DECIMAL，不用 FLOAT（0.1 + 0.2 ≠ 0.3）
--   · 不建外键约束：本地批量装载（LOAD DATA）时外键只会拖慢速度并制造
--     装载顺序依赖，引用完整性由 ETL 与业务层保证
--   · 时间列一律 DATETIME；event_time 用 DATETIME(3) 保留毫秒
-- ===========================================================================

CREATE DATABASE IF NOT EXISTS demo01
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE demo01;

SET NAMES utf8mb4;

DROP TABLE IF EXISTS dws_metric_offline;
DROP TABLE IF EXISTS dws_metric_realtime;
DROP TABLE IF EXISTS dwd_behavior_dirty;
DROP TABLE IF EXISTS dwd_behavior_log;
DROP TABLE IF EXISTS t_usage;
DROP TABLE IF EXISTS t_ticket;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_bill;
DROP TABLE IF EXISTS t_promo;
DROP TABLE IF EXISTS t_package;
DROP TABLE IF EXISTS t_account;

-- ===========================================================================
-- 一、业务表（7 张）
-- ===========================================================================

-- ① 账号表。游客不建账号（无 VISITOR 角色），所以大屏里的 user_id 可以为空。
CREATE TABLE t_account (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  login_name  VARCHAR(32)  NOT NULL COMMENT '用户填手机号，管理员填用户名',
  password    VARCHAR(64)  NOT NULL COMMENT 'BCrypt 哈希',
  role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ANALYST / ADMIN',
  id_card     VARCHAR(18)  NULL     COMMENT '身份证，展示时脱敏（前 6 后 4）',
  region      VARCHAR(16)  NULL     COMMENT '归属地，省级名',
  package_id  BIGINT       NULL     COMMENT '当前套餐，一用户一套餐',
  balance     DECIMAL(10,2) NULL    COMMENT '余额',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_login_name (login_name),
  KEY idx_package (package_id)
) ENGINE = InnoDB COMMENT = '账号表';

-- ② 套餐表。三个量纲：data_quota = GB，voice_quota = 分钟，sms_quota = 条。
--    ⚠️ status=0（停售）的套餐也要保留，大屏套餐分布 join 时**不得**加 status=1 过滤，
--       否则用过该套餐的用户会整批从饼图上消失。
CREATE TABLE t_package (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  name        VARCHAR(64)   NOT NULL,
  monthly_fee DECIMAL(10,2) NOT NULL COMMENT '月费（元）',
  data_quota  DECIMAL(10,2) NOT NULL COMMENT '流量额度（GB）',
  voice_quota INT           NOT NULL COMMENT '通话额度（分钟）',
  sms_quota   INT           NOT NULL COMMENT '短信额度（条）',
  description VARCHAR(512)  NULL,
  status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1 在售 / 0 停售',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT = '套餐表';

-- ③ 促销活动表。「进行中」= status=1 且 start_time <= NOW() <= end_time。
CREATE TABLE t_promo (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  title       VARCHAR(128) NOT NULL,
  content     TEXT         NULL,
  package_id  BIGINT       NULL,
  start_time  DATETIME     NOT NULL,
  end_time    DATETIME     NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  PRIMARY KEY (id),
  KEY idx_time (start_time, end_time)
) ENGINE = InnoDB COMMENT = '促销活动表';

-- ④ 费用流水表。金额一律存正数，方向由 type 区分（BILL 消费 / PAY 缴费）。
CREATE TABLE t_bill (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  user_id     BIGINT        NOT NULL,
  type        VARCHAR(8)    NOT NULL COMMENT 'BILL 消费 / PAY 缴费',
  amount      DECIMAL(10,2) NOT NULL COMMENT '正数',
  occur_time  DATETIME      NOT NULL,
  bill_month  CHAR(7)       NULL COMMENT '2026-09，仅消费有值',
  remark      VARCHAR(128)  NULL,
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_occur (user_id, occur_time),
  KEY idx_user_type_month (user_id, type, bill_month)
) ENGINE = InnoDB COMMENT = '费用流水表';

-- ⑤ 套餐办理记录表。办理即时生效：写本表的同时改 t_account.package_id，
--    本表 status 固定为 1（已生效），不做状态流转。
CREATE TABLE t_order (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  user_id     BIGINT   NOT NULL,
  package_id  BIGINT   NOT NULL,
  order_time  DATETIME NOT NULL,
  status      TINYINT  NOT NULL DEFAULT 1 COMMENT '固定 1 = 已生效',
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, order_time)
) ENGINE = InnoDB COMMENT = '套餐办理记录表';

-- ⑥ 工单表。状态机 PENDING → PROCESSING → DONE；类型四类见《开发文档》§5.2 ⑥。
CREATE TABLE t_ticket (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  user_id     BIGINT        NOT NULL,
  type        VARCHAR(16)   NOT NULL COMMENT 'NETWORK / BILLING / CONSULT / COMPLAINT',
  content     VARCHAR(1024) NOT NULL,
  status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PROCESSING / DONE',
  handler_id  BIGINT        NULL COMMENT '处理人（管理员账号 id）',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handle_time DATETIME      NULL COMMENT '未处理为 NULL',
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE = InnoDB COMMENT = '工单表';

-- ⑦ 用量表（汇总式）。一人一月一行。
--    ⚠️ uk_user_month 不是可选项：没有它，重跑一次 ETL 就会插入第二行，
--       余量 = 额度 − 用量 会悄悄翻倍，页面数字正常但全错。
CREATE TABLE t_usage (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  user_id     BIGINT        NOT NULL,
  usage_month CHAR(7)       NOT NULL COMMENT '账期，2026-09',
  used_data   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本月已用流量（GB）',
  used_voice  INT           NOT NULL DEFAULT 0 COMMENT '本月已用通话（分钟）',
  used_sms    INT           NOT NULL DEFAULT 0 COMMENT '本月已用短信（条）',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_month (user_id, usage_month)
) ENGINE = InnoDB COMMENT = '用量表（汇总式）';

-- ===========================================================================
-- 二、行为数据表（4 张）
-- ===========================================================================

-- ⑧ 行为明细表。按 event_date 月分区。
--    ⚠️ MySQL 要求**每一个唯一索引都包含分区列**，所以 uk_event 是
--       (event_id, event_date)，不是只对 event_id 建唯一索引；主键同理。
--    ⚠️ event_date 是**北京日期**，由 ETL 按 Asia/Shanghai 从 event_time 换算，
--       不能直接取 UTC 字面量的日期（早 8 点前的记录会跑到前一天）。
CREATE TABLE dwd_behavior_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  event_id    CHAR(36)     NOT NULL,
  event_date  DATE         NOT NULL COMMENT '北京日期，分区列',
  event_code  VARCHAR(32)  NOT NULL,
  event_time  DATETIME(3)  NOT NULL COMMENT 'UTC 时间',
  user_id     BIGINT       NULL COMMENT '游客为 NULL',
  session_id  CHAR(36)     NOT NULL,
  page        VARCHAR(64)  NOT NULL,
  region      VARCHAR(16)  NOT NULL COMMENT '省级名，逐字等于 china.json',
  device      VARCHAR(16)  NOT NULL COMMENT 'PC / MOBILE / OTHER',
  ip          VARCHAR(45)  NULL COMMENT 'IPv6 兼容',
  ext         JSON         NULL COMMENT '套餐 ID / 工单 ID 等',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (id, event_date),
  UNIQUE KEY uk_event (event_id, event_date),
  KEY idx_event_date (event_date),
  KEY idx_session (session_id),
  KEY idx_user_date (user_id, event_date)
) ENGINE = InnoDB
COMMENT = '行为明细表（月分区）'
PARTITION BY RANGE COLUMNS (event_date) (
  PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
  PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
  PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
  PARTITION p_future VALUES LESS THAN (MAXVALUE)
);

-- ⑨ 死信表。保留原始行，不静默丢弃；fail_reason 用前端 DIRTY_RULE 的七个枚举值。
CREATE TABLE dwd_behavior_dirty (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  source      VARCHAR(16)  NOT NULL COMMENT 'REALTIME / OFFLINE',
  raw_content TEXT         NOT NULL COMMENT '原始上报行（JSON 字符串）',
  fail_reason VARCHAR(32)  NOT NULL COMMENT '七条规则之一',
  fail_time   DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_source_time (source, fail_time),
  KEY idx_reason (fail_reason)
) ENGINE = InnoDB COMMENT = '行为数据死信表';

-- ⑩ 实时指标表。本阶段不写入（实时链路不在闭环内），保留结构。
CREATE TABLE dws_metric_realtime (
  metric_code  VARCHAR(32)   NOT NULL,
  metric_value DECIMAL(18,4) NOT NULL,
  stat_time    DATETIME      NOT NULL COMMENT '指标时间桶',
  PRIMARY KEY (metric_code, stat_time)
) ENGINE = InnoDB COMMENT = '实时指标表';

-- ⑪ 离线指标表。一行 = 某统计日、某指标编码、某维度值。
--    retention 的 dimension_value 形如 '2026-09-17|d1'（见 api/screen.js 的说明）。
CREATE TABLE dws_metric_offline (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  metric_code     VARCHAR(32)   NOT NULL COMMENT 'region_dist / package_dist / activity_level / funnel / retention',
  dimension       VARCHAR(16)   NOT NULL COMMENT 'region / package / level / step / day',
  dimension_value VARCHAR(64)   NOT NULL,
  stat_date       DATE          NOT NULL,
  metric_value    DECIMAL(18,4) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_metric (metric_code, dimension, dimension_value, stat_date),
  KEY idx_stat_date (stat_date)
) ENGINE = InnoDB COMMENT = '离线指标表';
