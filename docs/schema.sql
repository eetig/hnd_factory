-- =============================================================================
-- hnd_factory 数据库结构 + 初始化数据
-- 数据库：MySQL 8.0+（使用了 utf8mb4_0900_ai_ci 排序规则）
--
-- 用法：
--   mysql -h127.0.0.1 -uroot -p --default-character-set=utf8mb4 < docs/schema.sql
--
-- ⚠️ **`--default-character-set=utf8mb4` 不能省**：客户端不指定时按本机默认编码
--    （Windows 中文环境下是 GBK）连库，中文**注释**会被写坏 —— 表/列注释变成
--    「鐗╂枡搴撳瓨姹囨?」，而数据本身没问题（写入走 JDBC，charset 是对的）。
--    症状很容易误判成「库编码错了」，其实是建表那一次连接的编码问题。
--    已经建坏了不用重建表，用同样的命令重放一遍 ALTER ... COMMENT 即可（见文末备注）。
--
-- 可重复执行：第 1、2 部分均为幂等写法（IF NOT EXISTS / NOT EXISTS 判重），
--            重复执行不会报错、不会产生重复数据。
-- 第 3 部分为「老库升级」脚本，默认注释掉，仅在既有旧库上升级时按需执行。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `factory_db`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `factory_db`;


-- =============================================================================
-- 第 1 部分：表结构
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 生产工单 / 报工汇总（Excel「工单汇总」导入）
-- 业务唯一键：order_no
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_order` (
  `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `order_no`            varchar(32)   NOT NULL COMMENT '订单号，如 1000225020',
  `storage_tank`        varchar(16)   DEFAULT NULL COMMENT '储罐号，如 150储',
  `plant_code`          varchar(8)    DEFAULT NULL COMMENT '工厂代码，如 1503',
  `material_code`       varchar(32)   NOT NULL COMMENT '物料编码，如 114001897（关联领料单）',
  `material_desc`       varchar(128)  DEFAULT NULL COMMENT '物料描述，如 HND-V150',
  `team_name`           varchar(32)   DEFAULT NULL COMMENT '班组名称',
  `customer_brand`      varchar(32)   DEFAULT NULL COMMENT '客户牌号',
  `order_type`          varchar(8)    DEFAULT NULL COMMENT '订单类型，如 ZC1',
  `mrp_controller`      varchar(8)    DEFAULT NULL COMMENT 'MRP控制员，如 M02',
  `producer_count`      int           DEFAULT NULL COMMENT '生产主人员数',
  `batch_no`            varchar(32)   DEFAULT NULL COMMENT '批次号，如 2608280467',
  `order_qty`           decimal(18,4) DEFAULT NULL COMMENT '订单数量(KG)',
  `unit`                varchar(8)    DEFAULT NULL COMMENT '计量单位，KG',
  `prod_version`        varchar(16)   DEFAULT NULL COMMENT '生产版本，如 0000/0002',
  `plan_start_date`     date          DEFAULT NULL COMMENT '基本开始日期',
  `plan_finish_date`    date          DEFAULT NULL COMMENT '基本完成日期',
  `confirmed_qty`       decimal(18,4) DEFAULT NULL COMMENT '确认的产量(KG)',
  `conf_qty`            decimal(18,4) DEFAULT NULL COMMENT '确认产量CONF_',
  `delivered_qty`       decimal(18,4) DEFAULT NULL COMMENT '已交货数量GMPS',
  `actual_finish_date`  date          DEFAULT NULL COMMENT '实际完成日期',
  `last_changed_by`     varchar(32)   DEFAULT NULL COMMENT '最后更改人，如 HND CW002',
  `sys_status`          varchar(64)   DEFAULT NULL COMMENT '系统状态，如 PRC BASC RQ',
  `storage_tank_name`   varchar(64)   DEFAULT NULL COMMENT '储罐名称',
  `change_date`         date          DEFAULT NULL COMMENT '更改日期',
  `change_time`         datetime      DEFAULT NULL COMMENT '更改时间',
  `per_barrel_weight`   decimal(18,4) DEFAULT NULL COMMENT '每桶重量AMEIN',
  `create_time`         datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_plan_start` (`plan_start_date`),
  KEY `idx_actual_finish` (`actual_finish_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产工单/报工汇总表';


-- ---------------------------------------------------------------------------
-- 工单物料图片（file_name 指向 MinIO 对象，访问 url 由 file-service 实时签名）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `work_order_image` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`    varchar(32)  NOT NULL COMMENT '工单号',
  `file_name`   varchar(128) NOT NULL COMMENT 'MinIO 文件名',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单物料图片表';


-- ---------------------------------------------------------------------------
-- SAP 货物移动凭证（Excel「货物移动」导入）
-- 导入策略：按 order_no 整体替换（先删该订单旧记录再写入）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `material_movement` (
  `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `order_no`          varchar(32)   NOT NULL COMMENT '生产工单号，关联work_order.order_no',
  `material_code`     varchar(32)   NOT NULL COMMENT '物料编码',
  `material_desc`     varchar(128)  DEFAULT NULL COMMENT '物料描述',
  `movement_item`     int           DEFAULT NULL COMMENT '货物移动项目行号',
  `material_doc_item` int           DEFAULT NULL COMMENT '物料文档项目号',
  `batch_no`          varchar(32)   DEFAULT NULL COMMENT '批次号',
  `storage_location`  varchar(16)   DEFAULT NULL COMMENT '存储地点，如5001/7101/5004',
  `unit`              varchar(8)    DEFAULT NULL COMMENT '基本计量单位 KG',
  `quantity`          decimal(18,4) NOT NULL COMMENT '数量(带符号)：正数=入库，负数=出库/消耗',
  `movement_type`     varchar(8)    DEFAULT NULL COMMENT '移动类型：261=领料消耗,262=退料,101=成品入库',
  `material_doc`      varchar(32)   DEFAULT NULL COMMENT 'SAP物料凭证号，如4903520829',
  `credit_flag`       char(1)       DEFAULT NULL COMMENT '借/贷标记：H=借方(入库), S=贷方(消耗)',
  `posting_date`      date          DEFAULT NULL COMMENT '过账日期',
  `create_time`       datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_material_doc` (`material_doc`),
  KEY `idx_posting_date` (`posting_date`),
  KEY `idx_movement_type` (`movement_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SAP货物移动凭证表';


-- ---------------------------------------------------------------------------
-- 生产入库单（Excel「生产入库」导入）
-- 业务唯一键：(document_no + material_code + inbound_date) —— 同一单据下不同物料、
--   或同物料不同日期，都是不同记录（日期是行身份的一部分，变更-047）
--   ⚠️ 日期列可为 NULL，而 MySQL 唯一索引不约束 NULL —— 应用层把 NULL 归一成空串
--      参与比对（DocMaterialDateKey），document 模式另强制行日期非空
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `production_inbound` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `seq_no`        int           DEFAULT NULL COMMENT '序号',
  `document_no`   varchar(32)   DEFAULT NULL COMMENT '单据号（与物料编码、日期组成唯一键）',
  `inbound_date`  date          DEFAULT NULL COMMENT '日期（行身份的一部分，唯一键成员）',
  `material_name` varchar(128)  DEFAULT NULL COMMENT '物料名称',
  `material_code` varchar(32)   DEFAULT NULL COMMENT '物料编码',
  `inbound_qty`   decimal(18,4) DEFAULT NULL COMMENT '入库数量',
  `unit`          varchar(16)   DEFAULT NULL COMMENT '单位',
  `file_name`     varchar(255)  DEFAULT NULL COMMENT '单据图片文件名(MinIO，来源 Excel 内嵌图)',
  `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_no_material_date` (`document_no`,`material_code`,`inbound_date`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_inbound_date` (`inbound_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产入库单';


-- ---------------------------------------------------------------------------
-- 领料汇总（Excel「领料汇总」导入）
-- 业务唯一键：(document_no + material_code + pick_date)
--   NULL 日期的口径同上（production_inbound 的注释）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `material_pick_summary` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `seq_no`        int           DEFAULT NULL COMMENT '序号',
  `document_no`   varchar(32)   DEFAULT NULL COMMENT '单据号（与物料编码、日期组成唯一键）',
  `pick_date`     date          DEFAULT NULL COMMENT '日期（行身份的一部分，唯一键成员）',
  `material_name` varchar(128)  DEFAULT NULL COMMENT '物料名称',
  `material_code` varchar(32)   DEFAULT NULL COMMENT '物料编码',
  `pick_qty`      decimal(18,4) DEFAULT NULL COMMENT '领料数量',
  `unit`          varchar(16)   DEFAULT NULL COMMENT '单位',
  `file_name`     varchar(255)  DEFAULT NULL COMMENT '单据图片文件名(MinIO，来源 Excel 内嵌图)',
  `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_no_material_date` (`document_no`,`material_code`,`pick_date`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_pick_date` (`pick_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领料汇总单';


-- ---------------------------------------------------------------------------
-- 物料库存汇总（Excel「库存汇总」导入，页面「物料查询」用）
--
-- 业务唯一键：(plant_code + material_code + storage_location)。
--   源文件里同一物料常有多行（硅粉 111001785 会出现 6,000 / 9,482 / 34,000 三行，
--   其它列完全相同），导入时按这个键把 stock_qty **相加**存成一条。
--
-- 再次导入 = 当前库存快照：先把本次涉及工厂的 stock_qty 清零（**行保留**，
--   物料信息以后还查得到），再 upsert 本次的行。见 MaterialStockServiceImpl#saveImported。
--
-- ⚠️ plant_code / storage_location 用 NOT NULL DEFAULT '' 而不是可空 ——
--    MySQL 唯一索引不约束 NULL，列可空时同键能插进多条，唯一键形同虚设
--    （tank_level_record 那张表记过同一条教训）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `material_stock` (
  `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `plant_code`       varchar(8)    NOT NULL DEFAULT '' COMMENT '工厂，如 1503',
  `material_code`    varchar(32)   NOT NULL DEFAULT '' COMMENT '物料，如 111001785',
  `material_desc`    varchar(128)  DEFAULT NULL COMMENT '物料描述，如 硅粉',
  `spec`             varchar(255)  DEFAULT NULL COMMENT '规格型号，如 500KG/袋',
  `storage_location` varchar(16)   NOT NULL DEFAULT '' COMMENT '存储地点，如 1001',
  `storage_desc`     varchar(64)   DEFAULT NULL COMMENT '存储地点描述，如 原材料仓',
  `unit`             varchar(16)   DEFAULT NULL COMMENT '基本计量单位，如 KG',
  `stock_qty`        decimal(18,3) NOT NULL DEFAULT 0 COMMENT '非限制使用的库存（同键多行相加）',
  `last_import_time` datetime      DEFAULT NULL COMMENT '最近一次被导入的时间',
  `create_time`      datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plant_material_location` (`plant_code`,`material_code`,`storage_location`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_material_desc` (`material_desc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料库存汇总（SAP 库存导出）';


-- ---------------------------------------------------------------------------
-- 物料领料单（旧版：单据 + 附件，对应 MaterialPickService，目前已不在用）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `material_pick` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pick_no`       varchar(64)  NOT NULL COMMENT '领料单号',
  `pick_date`     datetime     DEFAULT NULL COMMENT '领料时间',
  `material_name` varchar(128) DEFAULT NULL COMMENT '物料名称',
  `pick_weight`   double       DEFAULT NULL COMMENT '领料重量',
  `remark`        varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料领料单(旧版)';


-- ---------------------------------------------------------------------------
-- 角色表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_name`   varchar(32) NOT NULL COMMENT '角色名称',
  `role_key`    varchar(32) NOT NULL COMMENT '角色标识 admin/team_leader/operator/guest',
  `description` varchar(128) DEFAULT NULL COMMENT '描述',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';


-- ---------------------------------------------------------------------------
-- 角色权限中间表
-- 唯一键 uk_role_permission 用于防止同一角色重复授予同一权限
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id`        bigint      NOT NULL COMMENT '角色ID',
  `permission_key` varchar(64) NOT NULL COMMENT '权限标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_key`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限中间表';


-- ---------------------------------------------------------------------------
-- 用户表（密码为 BCrypt 密文，由 BCryptUtil.encode() 生成）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    varchar(32) NOT NULL COMMENT '账号',
  `password`    varchar(64) NOT NULL COMMENT '密码(BCrypt密文)',
  `real_name`   varchar(32) DEFAULT NULL COMMENT '真实姓名',
  `role_id`     bigint      DEFAULT NULL COMMENT '角色ID，关联sys_role.id',
  `status`      tinyint     DEFAULT '1' COMMENT '状态 1启用 0禁用',
  `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';


-- ---------------------------------------------------------------------------
-- 物料主数据（图片解析辅助录入用：按物料名称反查编码与单位）
--
-- 业务唯一键：(material_code, material_name) —— 编码与名称【各自都不唯一】：
--   源数据里 114002501 同时对应「HND-2171前馏份」和「HND-2171过渡馏份」；
--   外贸包材也存在多个编码共用同一名称。故唯一键必须取两者组合。
--
-- 不存归一化列：名称归一化只在 Java（MaterialNameNormalizer）里实现一份，
-- 否则 SQL 里的归一化值会和代码实现各存一份、迟早不一致。表仅数百行，
-- 查询时全量取回内存匹配，开销可忽略。
--
-- 维护方式：一次性导入（见第 2 部分），之后手工维护。
--   停用某条请置 enabled = 0，不要删行 —— 删了会丢失「为何查不到」的线索。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `material_master` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_code`  varchar(32)  NOT NULL COMMENT '物料编码',
  `material_name`  varchar(128) NOT NULL COMMENT '物料名称（识别结果按此字段反查）',
  `spec`           varchar(255) DEFAULT NULL COMMENT '规格',
  `unit`           varchar(16)  DEFAULT NULL COMMENT '单位（查到时自动带出）',
  `material_type`  varchar(16)  DEFAULT NULL COMMENT '物料类型',
  `material_group` varchar(16)  DEFAULT NULL COMMENT '物料组',
  `product_group`  varchar(16)  DEFAULT NULL COMMENT '产品组',
  `source_sheet`   varchar(64)  DEFAULT NULL COMMENT '来源工作表（可追溯）',
  `source_row`     int          DEFAULT NULL COMMENT '来源行号（可追溯）',
  `enabled`        tinyint      NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0停用（停用不参与匹配）',
  `create_time`    datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_name` (`material_code`, `material_name`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_material_name` (`material_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料主数据';


-- ---------------------------------------------------------------------------
-- 月底车间各储罐液位记录
--
-- 每月月底下午 3 点抄录车间各储罐（产品 / 原料）的液位，
-- 「理论质量」由液位换算得出，与实测重量存在差异，以实际测量为准
-- （该说明同时展示在页面对应 Tab 的表格下方）。
--
-- 「图据」（现场照片）存在**子表 tank_level_image** 里（一条记录可多张），
-- 子表只存**文件名**，对外访问路径由后端拼 /files、/thumbs
-- —— 与工单图片、领料汇总、生产入库的图片约定一致（《前后端改动统筹》契约 2.3），
-- 前端拿到的始终是相对路径，同源部署无需改配置。
--
-- 唯一键 (记录日期 + 容器编号)：**已废弃**（2026-10-20，变更-127，见本文件第 24 步）。
--   历史：变更-004-1 把唯一键挂在 (日期 + 容器编号) 上；变更-013 让 tank_code 可空、
--   空编号存 NULL，靠"MySQL 唯一索引不约束 NULL"来放行「同一天多条」。
--   ⚠️ 这条假设在生产上**塌了两次**：新增记录的空编号实际落成了空串 ''（MyBatis-Plus
--   跳过 null 字段 → 吃列的 DEFAULT，而生产列的 DEFAULT 是 ''），空串受唯一键约束，
--   于是「一天只能存一条」；2026-10-20 手机端同一天记第二台容器必报
--   「该记录日期已存在一条容器编号为空的记录」。
--   现在：唯一键整条去掉（使用方口径：同一天可以多条，重复只做保存前提示），
--   且 tank_code 无值时**显式写 NULL**（实体 TankLevelRecord 上的 insertStrategy=ALWAYS），
--   不再依赖任何列的默认值。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tank_level_record` (
  `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `record_date`        date          NOT NULL COMMENT '记录日期（每月月底下午3点抄录）',
  `location`           varchar(32)   NOT NULL DEFAULT '' COMMENT '属地（车间/区域）',
  `category`           varchar(8)    DEFAULT NULL COMMENT '所属：产品 / 原料',
  `material_code`      varchar(32)   DEFAULT NULL COMMENT '物料编码（关联物料主数据，可空）',
  `material_name`      varchar(128)  DEFAULT NULL COMMENT '物料名称',
  `tank_name`          varchar(64)   NOT NULL COMMENT '容器名称（储罐号）',
  `tank_code`          varchar(64)   DEFAULT NULL COMMENT '容器编号（设备位号）；界面不填，无值时为 NULL',
  `level_value`        decimal(18,4) DEFAULT NULL COMMENT '容器液位',
  `theoretical_weight` decimal(18,4) DEFAULT NULL COMMENT '理论质量(KG)',
  `create_time`        datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_date` (`record_date`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_tank_code` (`tank_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月底车间各储罐液位记录';

-- ---------------------------------------------------------------------------
-- 变更-004-1（既有库升级）：加「容器编号」列并把唯一键换到 (记录日期 + 容器编号)。
-- CREATE TABLE IF NOT EXISTS 对已存在的表不会生效，老库要单独执行下面三句：
--
--   ALTER TABLE `tank_level_record`
--     ADD COLUMN `tank_code` varchar(64) NOT NULL DEFAULT ''
--       COMMENT '容器编号（设备位号，与记录日期联合唯一）' AFTER `tank_name`;
--
--   UPDATE `tank_level_record` SET `tank_code` = `tank_name` WHERE `tank_code` = '';
--     -- 历史行先按容器名称兜一个编号，真实位号由业务侧补齐后覆盖
--
--   ALTER TABLE `tank_level_record`
--     DROP INDEX `uk_date_location_tank`,
--     ADD UNIQUE KEY `uk_date_tank` (`record_date`, `tank_code`),
--     ADD KEY `idx_tank_code` (`tank_code`);
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- 月底储罐液位记录图据（一条记录可多张，变更-011）
--
-- 与 work_order_image 同一套模式（子表 + file_name + 按归属查），两处差异有理由：
--
--   · 关联键用自增 record_id 而不是业务键：工单挂 order_no（业务键，永不变），
--     液位记录的业务键是 (record_date, tank_code) —— 而这两个字段正是用户会在
--     页面上编辑的，挂业务键的话每改一次日期/编号都要级联改图片表。
--
--   · file_name 用 varchar(255) 而不是 128：与 tank_level_record 原来的
--     file_name 列保持一致（那一列已由本表取代）。
--
-- 不建外键：与全库既有约定一致（work_order_image 也没有）。
-- 代价是删记录时必须由 Service 主动清图片，否则留下孤儿行 + MinIO 孤儿文件。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tank_level_image` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_id`   bigint       NOT NULL COMMENT '所属液位记录 id（tank_level_record.id）',
  `file_name`   varchar(255) NOT NULL COMMENT 'MinIO 文件名（经 file-service 访问）',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月底储罐液位记录图据表';


-- ---------------------------------------------------------------------------
-- 设备台账（静设备）
--
-- 来源：线下《设备台账（静设备）2025.06》各页 + 《容器封头参数表 2025.06》
--   （一车间 / 三甲车间 / 四甲车间 / 罐区 / 公用工程），一次性导入（见第 2 部分）。
--
-- 为什么把「封头容积 / 每 mm 液位对应体积」一起存进来：
--   月底抄表要把液位(mm)换算成质量 —— 质量 = 液位 × 每 mm 对应质量。
--   每 mm 对应质量 = 每 mm 对应体积(m3) × 介质密度(t/m3) × 1000：
--   体积由设备几何决定（封头容积 + 筒体截面积），密度由物料决定，
--   因此这两个量必须分开存 —— 同一个罐装不同物料，每 mm 对应的质量并不相同。
--   源台账只给到体积（V1m3/mm），密度留空由业务侧按物料补；
--   两者缺一时不得用默认密度 1.0 兜底，否则会静默算出「看起来合法」的错值。
--
-- 业务键的取舍：
--   位号可缺（罐区、水封类有整页没有位号），名称 + 规格也不唯一 ——
--   台账里真的有 3 台参数完全相同的「真空缓冲罐 DN700x1000」，业务键无法唯一标识。
--   故唯一键取「来源工作表 + 行号」作**导入幂等键**（重复执行本文件不会翻倍），
--   检索走 idx_equipment_code / idx_equipment_name。手工新增的行请同样写明来源
--   （工作表填 'manual'，行号取同工作表最大值 +1），否则重复导入会多出一行。
--
-- 容器类型（container_type）：
--   按 spec 派生 —— 含「管束」为 4（再沸器）、含「卧式」为 1、含「平底」为 2（平底立式）、都不含为 3（立式）。
--   源台账把容器型式写在规格后面的括号里（「DN1000x3400（卧式）」「Φ800x1100（平底）」），
--   但业务要按型式分桶时不能让调用方各写一遍字符串匹配，故落成整型列。
--   「管束」那一条排最前：再沸器既是卧式又带管束，顺序反了就判不到 4。
--   ⚠️ 本列**可空**，回填只填 NULL —— 见下面回填语句与列注释里的理由。
--   判定规则与回填语句见第 2 部分（幂等：**只填 NULL 的行**，人工改过的不动）。
--   暂不建索引：全表 91 行（将来几百行），低基数列上索引帮不上优化器。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `equipment_ledger` (
  `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `equipment_code`  varchar(64)   NOT NULL DEFAULT '' COMMENT '设备位号（产品编号），源台账缺号时为空串',
  `equipment_name`  varchar(128)  NOT NULL COMMENT '设备名称（如 三甲粗品罐）',
  `nickname`        varchar(64)   DEFAULT NULL COMMENT '设备昵称（短名，供下拉与搜索用；可空）',
  `workshop`        varchar(32)   NOT NULL DEFAULT '' COMMENT '车间/装置（一车间/三甲车间/四甲车间/罐区/公用工程）',
  `spec`            varchar(128)  DEFAULT NULL COMMENT '设备规格（公称直径×筒体长度，如 DN1600x2000）',
  -- ⚠️ **可空且无默认值**（2026-10-06 起）：它是由 spec 派生的输出列，回填语句只填 NULL 的行，
  -- 人工在「设备数据维护」页改过的值就不会被重复执行本文件刷回去。原先 NOT NULL DEFAULT 3，
  -- 分不出「默认的 3」和「人工设的 3」，维护页一上线就会互相打架。
  `container_type`  tinyint       DEFAULT NULL COMMENT '容器类型 1卧式 2平底立式 3立式 4再沸器（由 spec 判定：含「管束」→4，含「卧式」→1，含「平底」→2，都不含→3；NULL = 尚未派生）',
  -- 以下 7 列专供「压力容器体积计算」页（变更-025）。前 5 列是该页**自己算**体积用的，
  -- 不依赖 head_volume / volume_per_mm 那套线性近似（那套在卧式罐、以及液面落在封头里时不成立）。
  -- 口径务必与 head_volume 对齐：**head_volume = 单个封头的「曲面 + 直边」**，
  -- 所以 shell_length 存的是**不含直边**的筒体直段长，直边另存一列。
  -- 这几列填齐（且 enabled=1）的行才会出现在该页的下拉里；留空即「本行不参与体积计算」。
  -- 「朝向」不另开列：复用 container_type（**1 或 4** = 卧式，2/3 = 立式）—— 该列已有的规矩是
  -- 「型式由 spec 派生，要改就改 spec」，再开一列会出现两列互相矛盾的可能。
  -- ⚠️ 因此新增卧式容器时 spec 里必须带「卧式」二字，否则会被当成立式。
  `inner_diameter`    decimal(18,2) DEFAULT NULL COMMENT '容器内径(mm)',
  `shell_length`      decimal(18,2) DEFAULT NULL COMMENT '筒体直段长(mm)，**不含封头直边**',
  `straight_flange`   decimal(18,2) DEFAULT NULL COMMENT '封头直边(mm)，无直边填 0',
  `top_head_depth`    decimal(18,2) DEFAULT NULL COMMENT '上/左封头曲面深度(mm)，标准椭圆封头 = 内径/4',
  `bottom_head_depth` decimal(18,2) DEFAULT NULL COMMENT '下/右封头曲面深度(mm)；NULL 或 0 = 平底',
  `image_file`        varchar(128)  DEFAULT NULL COMMENT '容器底图文件名；深色版按 -dark 后缀约定取（两套前端各自的资源里同名、分辨率不同）',
  `image_bounds`      json          DEFAULT NULL COMMENT '底图坐标，**按图片宽度归一化**（width 恒为 1、height 是长宽比、其余为占图宽的比例）—— 归一化才能让原图端与 1/2 压图端共用同一组坐标。由 resources/compress-vessel-images.py 打印',
  `thickness`       varchar(32)   DEFAULT NULL COMMENT '筒体厚度(mm)，源台账有 8mm/10mm 这类多段写法',
  `volume`          decimal(18,4) DEFAULT NULL COMMENT '容积(m3)，源台账 V= 前缀',
  `heat_area`       decimal(18,4) DEFAULT NULL COMMENT '换热面积(m2)，源台账 F= 前缀（与容积互斥）',
  `head_volume`     decimal(18,6) DEFAULT NULL COMMENT '封头容积(m3)，平底容器无此项',
  `volume_per_mm`   decimal(18,8) DEFAULT NULL COMMENT '每mm液位对应体积(m3/mm)，源台账 V1m3/mm；8 位小数不可压缩',
  `mass_per_mm`     decimal(18,6) DEFAULT NULL COMMENT '每mm液位对应质量(kg/mm) = volume_per_mm × density × 1000，已核定才填',
  `density`         decimal(18,4) DEFAULT NULL COMMENT '介质密度(t/m3)，用于换算 mass_per_mm；随物料而定，可空',
  `medium`          varchar(128)  DEFAULT NULL COMMENT '介质（物料名称）',
  `design_temp`     varchar(32)   DEFAULT NULL COMMENT '操作温度(℃)，源台账含 150/120 这类多工况写法，故用字符串',
  `design_pressure` varchar(32)   DEFAULT NULL COMMENT '操作压力(MPa)，源台账含 常压/负压/0.5/0.2 等写法，故用字符串',
  `source_sheet`    varchar(64)   DEFAULT NULL COMMENT '来源台账工作表（可追溯）',
  `source_row`      int           DEFAULT NULL COMMENT '来源行号（可追溯；与 source_sheet 构成导入幂等键）',
  `remark`          varchar(255)  DEFAULT NULL COMMENT '备注（如 需用软件计算、平底无封头容积）',
  `enabled`         tinyint       NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0停用（停用不参与检索）',
  `create_time`     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_row` (`source_sheet`, `source_row`),
  KEY `idx_equipment_code` (`equipment_code`),
  KEY `idx_equipment_name` (`equipment_name`),
  KEY `idx_workshop` (`workshop`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备台账（静设备：位号/名称/规格/容器类型/封头容积/每mm液位对应量）';


-- ---------------------------------------------------------------------------
-- 采购进度跟踪（变更-091；变更-096 补字段）
--
-- 这一页记的是「谁、哪天提出要什么料、要求哪天到、下了哪张采购单、到的哪张送货单」，
-- 即**需求登记 → 提单 → 到货**一条线。物料名称与编码在这里是**快照文本**（不是外键）：
-- 采购记录要能独立读懂，历史单据也不该被后来的主数据改名影响。
--
-- ⚠️ `purchase_order_no` 的语义（变更-096 使用方澄清）：存的是**OA 提单号**，
--   不是采购下给供应商的订单号。作用是用它回查 OA 单、看那张单上的希望到货日期。
--    为免重建索引，**没有改物理列名**，只改注释与代码里对它的称呼（oaNo）。
--
-- ⚠️ **不存「状态」列**（变更-096 的刻意设计）：状态由 OA 单号/送货单/日期**派生**，
--   规则见 PurchaseTrackingVO：
--     没 OA 单号            → 待提OA
--     有单号、没送货单       → 等货（过 required_arrival_date 即「超期」，临近 3 天即「临期」）
--     有送货单              → 已到货（received_quantity < quantity 为「部分到货」）
--   存状态一定会和实际字段打架（例如状态写着「已到货」但送货单被删了），派生则永不脱节。
--
-- ⚠️ 物料编码**刻意不做唯一键**：
--   ① 采购是流水账 —— 同一物料在不同需求批次里本来就会重复出现（这不算错误数据）；
--   ② 源数据里编码本身就不唯一 —— `material_master` 实测 246 行 / 241 个编码，
--      有 5 个编码各对应两个不同物料（见那张表的 `uk_code_name` 注释）。
--
-- ⚠️ 查编码要看清楚查哪张表（**两张表不是一回事**，2026-10-16 使用方澄清）：
--   · `material_master` = **SAP 物料主数据**（SAP 侧维护，图片解析按名称反查编码/单位用）——
--     采购登记**不写这张表**，写进去会污染 SAP 主数据、也会干扰反查；
--   · `material_stock` = 库存 + **编码登记总表**（使用方指定）：采购登记进来的新编码落在这里，
--     固定落在 plant_code=1503 / storage_location=0000 / storage_desc='采购登记（无库存）'，
--     库存记 0。好处：不在任何真实仓里、一眼看出是采购登记的、
--     被下一次库存导入清零但**永不删除**（该导入只清数量、保留行），
--     且用「库位=0000」能一次查出所有采购登记过的编码。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `purchase_tracking` (
  `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键（页面上就是「序号」）',
  `requester`             varchar(32)  NOT NULL DEFAULT '' COMMENT '需求人',
  `request_date`          date         DEFAULT NULL COMMENT '提出需求日期',
  `material_name`         varchar(128) NOT NULL DEFAULT '' COMMENT '物料名称（快照）',
  `material_code`         varchar(32)  NOT NULL DEFAULT '' COMMENT '物料编码（快照）',
  `spec`                  varchar(255) DEFAULT NULL COMMENT '规格型号',
  `purpose`               varchar(255) DEFAULT NULL COMMENT '用途（这料拿去干什么用；变更-098 起；NULL = 未填）',
  `category`              varchar(16)  DEFAULT NULL COMMENT '类别（备件/五金/原料/设备/其他；变更-096 起，变更-097c 加五金；NULL = 未分类，页面显示「—」）',
  `quantity`              decimal(12,3) DEFAULT NULL COMMENT '需求数量（变更-096 起；NULL = 历史数据未记，页面显示「—」）',
  `unit`                  varchar(16)  DEFAULT NULL COMMENT '单位（个/台/套/件/米/公斤…；变更-096 起；NULL = 未填）',
  `required_arrival_date` date         DEFAULT NULL COMMENT '要求到货日期（超期/临期的判定基准，源自 OA 单上的希望到货日期）',
  `purchase_order_no`     varchar(64)  NOT NULL DEFAULT '' COMMENT 'OA 单号（变更-096 起语义明确：这是**OA 提单号**，不是采购下给供应商的订单号）；还没提单时留空',
  `oa_url`                varchar(512) NOT NULL DEFAULT '' COMMENT 'OA 表单链接（变更-100 起；从浏览器地址栏复制的整条 URL，页面把单号渲染成指向它的链接；空串 = 还没粘过）',
  `delivery_note`         varchar(64)  NOT NULL DEFAULT '' COMMENT '送货单**图片路径**（/files/xxx，ADR-0004 固定拼接；变更-094 起是图片，不再填单号）；空串 = 还没到货；历史上存过文本单号，老数据按文本原样显示',
  `received_quantity`     decimal(12,3) DEFAULT NULL COMMENT '实际到货数量（变更-096 起；NULL = 未记；< quantity 即部分到货）',
  `actual_arrival_date`   date         DEFAULT NULL COMMENT '实际到货日期（变更-096 起；NULL = 还没到货；算出「提前/延后几天」靠它）',
  `remark`                varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time`           datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_purchase_order_no` (`purchase_order_no`),
  KEY `idx_request_date` (`request_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购进度跟踪（需求登记→提OA单→到货）';


-- ---------------------------------------------------------------------------
-- 资料归档（变更-126，2026-10-20）
--
-- 两张表：分类（两级、使用方维护）+ 台账。设计理由见 docs/资料归档-开工方案.md。
-- 约定与全库一致：可选字符串一律 NOT NULL DEFAULT ''；分页 ORDER BY 末尾带唯一 id；
-- 不建业务唯一键（同一份资料可能有多册/多年）；删除用 deleted 软删、文件不物理删。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `archive_category` (
  `id`         bigint      NOT NULL AUTO_INCREMENT,
  `parent_id`  bigint      DEFAULT NULL COMMENT '为空 = 大类；否则指向大类 id（只支持两级）',
  `name`       varchar(64) NOT NULL                COMMENT '类目名（同一父下不许重名）',
  `sort_no`    int         NOT NULL DEFAULT 0      COMMENT '同层排序，小的在前',
  `create_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`),
  UNIQUE KEY `uk_parent_name` (`parent_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料分类（两级，使用方维护）';

CREATE TABLE IF NOT EXISTS `archive_document` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `name`        varchar(128) NOT NULL                COMMENT '资料名称（必填）',
  `category_id` bigint       NOT NULL                COMMENT '分类 id（必填）',
  `file_name`   varchar(128) NOT NULL DEFAULT ''     COMMENT '对象名（**裸名**，不含 archive/ 前缀：<32hex>.<ext>；前缀是 file-service 的实现细节）；空 = 只有纸质没有电子件',
  `orig_name`   varchar(255) NOT NULL DEFAULT ''     COMMENT '上传时的原文件名（下载时还原）',
  `file_size`   bigint       NOT NULL DEFAULT 0      COMMENT '字节数；0 = 无电子件',
  `mime_type`   varchar(128) NOT NULL DEFAULT ''     COMMENT '上传时声明的 Content-Type（供下载时回落）',
  `expire_date` date         DEFAULT NULL            COMMENT '有效期至（证照/校验/培训类）；NULL = 无有效期',
  `origin_place` varchar(128) NOT NULL DEFAULT ''    COMMENT '纸质原件在哪：部门/柜子/责任人',
  `doc_no`      varchar(64)  NOT NULL DEFAULT ''     COMMENT '编号/文号',
  `owner`       varchar(64)  NOT NULL DEFAULT ''     COMMENT '责任人',
  `remark`      varchar(500) NOT NULL DEFAULT '',
  `deleted`     tinyint      NOT NULL DEFAULT 0      COMMENT '软删：1 = 已删（只有 admin 能删）；文件保留',
  `created_by`  varchar(64)  NOT NULL DEFAULT ''     COMMENT '录入人用户名',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_expire` (`expire_date`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料台账（检查找得到 + 到期能提醒）';


-- =============================================================================
-- 第 2 部分：初始化数据（幂等，可重复执行）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 角色
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role` (`role_name`, `role_key`, `description`) VALUES
  ('管理员', 'admin',       '全部权限'),
  ('班组长', 'team_leader', '只读（写操作仅管理员）'),
  ('操作工', 'operator',    '仅查看'),
  ('游客',   'guest',       '只读，不能执行任何写操作')
ON DUPLICATE KEY UPDATE
  `role_name`   = VALUES(`role_name`),
  `description` = VALUES(`description`);


-- ---------------------------------------------------------------------------
-- 角色权限
--
-- 权限清单：
--   work_order:view          查看工单        work_order:add           新增工单
--   work_order:edit          编辑工单        work_order:delete        删除工单
--   work_order:import        导入工单(Excel) work_order:image:upload  工单图片上传
--   work_order:image:delete  删除工单图片    goods_move:view          查看货物移动
--   goods_move:import        导入货物移动    pick:view                查看领料汇总
--   inbound:view             查看生产入库
--   tank_level:edit          维护储罐液位记录（新增/编辑，含图据上传删除）
--   tank_level:delete        删除储罐液位记录
--
-- 说明：查询类接口后端已放开免登录，*:view 主要供前端做按钮显隐；
--      后端实际强制校验的是写操作权限（edit/import/upload/delete）。
--
-- 写操作（增删改）一律只授给 admin：不以 :view 结尾的都是写权限，全部收在管理员手里。
-- 业务要求「只有 admin 能增删改」——前端靠这份清单隐藏入口，后端另有 SaTokenConfigure
-- 的集中闸门兜底（非 admin 调写接口一律 403，不依赖本表数据）。
-- 原先 tank_level:* 单独只授 admin 的理由（月度台账原始记录，改错影响后续算量），
-- 现在对全部写权限一视同仁。
--
-- ⚠️ 下面这段 INSERT 只做「补」、不负责「撤」：老库里 team_leader / operator 早就被授过
--    写权限，光改这份清单不会让它们消失 —— 靠紧随其后的那段 DELETE 收口。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`role_id`, `permission_key`)
SELECT r.`id`, x.`perm`
FROM `sys_role` r
JOIN (
  -- admin：全部权限
  SELECT 'admin' AS rk, 'work_order:view'         AS perm UNION ALL
  SELECT 'admin', 'work_order:add'                UNION ALL
  SELECT 'admin', 'work_order:edit'               UNION ALL
  SELECT 'admin', 'work_order:delete'             UNION ALL
  SELECT 'admin', 'work_order:import'             UNION ALL
  SELECT 'admin', 'work_order:image:upload'       UNION ALL
  SELECT 'admin', 'work_order:image:delete'       UNION ALL
  SELECT 'admin', 'goods_move:view'               UNION ALL
  SELECT 'admin', 'goods_move:import'             UNION ALL
  SELECT 'admin', 'pick:view'                     UNION ALL
  SELECT 'admin', 'inbound:view'                  UNION ALL
  SELECT 'admin', 'tank_level:edit'               UNION ALL
  SELECT 'admin', 'tank_level:delete'             UNION ALL
  -- 设备数据维护（2026-10-06）：写权限照例只授 admin（同「写操作一律仅 admin」的既定口径）。
  -- 单独一个权限位而不是复用 tank_level:edit —— 将来要放开给非 admin 的维护员时，
  -- 只需给那个角色加这一位，不必动接口。
  SELECT 'admin', 'equipment:edit'                UNION ALL
  -- 采购进度跟踪（2026-10-16，变更-091）：同样单独一个权限位、同样只授 admin。
  -- 查询（/api/purchase/list）与其它列表接口一样免登录 —— 采购进度是厂内公开信息；
  -- 只有「登记/改/删」需要这一位。
  SELECT 'admin', 'purchase:edit'                 UNION ALL
  -- 资料归档（2026-10-20，变更-126）：同样单独一个权限位、只授 admin。
  -- ⚠️ 这一位只管「增/改/删」——查看是**全员可看**（Q5 的决策），所以没有 archive:view；
  --    登录由全局闸门保证，非 admin 也能进页面、能下载。
  SELECT 'admin', 'archive:edit'                  UNION ALL
  -- team_leader：仅查看（写权限一律只授 admin，见下方撤权段）
  SELECT 'team_leader', 'work_order:view'         UNION ALL
  SELECT 'team_leader', 'goods_move:view'         UNION ALL
  SELECT 'team_leader', 'pick:view'               UNION ALL
  SELECT 'team_leader', 'inbound:view'            UNION ALL
  -- operator：仅查看
  SELECT 'operator', 'work_order:view'            UNION ALL
  SELECT 'operator', 'goods_move:view'            UNION ALL
  SELECT 'operator', 'pick:view'                  UNION ALL
  SELECT 'operator', 'inbound:view'               UNION ALL
  -- guest：仅查看
  SELECT 'guest', 'work_order:view'               UNION ALL
  SELECT 'guest', 'goods_move:view'               UNION ALL
  SELECT 'guest', 'pick:view'                     UNION ALL
  SELECT 'guest', 'inbound:view'
) x ON x.rk = r.`role_key`
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_permission` p
  WHERE p.`role_id` = r.`id` AND p.`permission_key` = x.`perm`
);


-- ---------------------------------------------------------------------------
-- 写权限收归 admin：撤掉非 admin 角色手里的写权限（幂等，可重复执行）
--
-- 上面那段 INSERT ... WHERE NOT EXISTS 是「只补不撤」的，所以老库需要这段来对齐：
-- 跑完本文件后，任何非 admin 角色的写权限都会被清掉，只剩 *:view。
-- 判定口径：写权限 = 权限清单里不以 :view 结尾的那 9 个标识（与 @SaCheckPermission
-- 用到的完全一一对应，含两个预留未用的 work_order:add / work_order:delete）。
-- 注意与 SaTokenConfigure 的集中闸门是两层：这里管「没授权就别想有」，那里管
-- 「哪怕授权了、非 admin 也一律 403」。
-- ---------------------------------------------------------------------------
DELETE p FROM `sys_role_permission` p
JOIN `sys_role` r ON r.`id` = p.`role_id`
WHERE r.`role_key` <> 'admin'
  AND p.`permission_key` IN (
    'work_order:add',
    'work_order:edit',
    'work_order:delete',
    'work_order:import',
    'work_order:image:upload',
    'work_order:image:delete',
    'goods_move:import',
    'tank_level:edit',
    'tank_level:delete',
    -- ⚠️ 新增写权限位时**必须**追加到这里（本清单要求与 @SaCheckPermission 一一对应）：
    -- 漏了它，被误授给非 admin 的新权限位就不会被这段撤掉，「写权限一律只授 admin」出现缺口。
    'purchase:edit',
    -- 资料归档（2026-10-20，变更-126）：同一条规矩，新增写权限位必须同时加进这份撤权清单。
    'archive:edit'
  );


-- ---------------------------------------------------------------------------
-- 初始账号
--
--   admin / admin123   管理员（全部权限）
--   guest / admin      游客（只读，不能导入/上传任何文件）
--   test  / test       游客（只读，早期测试账号）
--   eetig / （不记录）   管理员（决策-004 新增；明文不落仓库，种子见下方最后一条）
--
-- 密码为 BCrypt 密文（$2a$ 前缀，与 Sa-Token 内置 BCrypt 兼容）。
-- 如需重置密码，用项目的 org.example.util.BCryptUtil.encode("新密码") 生成后替换。
-- ---------------------------------------------------------------------------
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role_id`, `status`)
SELECT 'admin',
       '$2a$12$9El20uc9FqyIg1dvJhETmOGnb01EC7yxLwn/bM500uLfqRtrMrkWi',
       '系统管理员',
       r.`id`, 1
FROM `sys_role` r WHERE r.`role_key` = 'admin'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`), `real_name` = VALUES(`real_name`);

-- 游客账号：只读，不能使用任何文件上传（导入 Excel / 上传工单图片）。
-- 权限来自 guest 角色，该角色只授予 *:view，不含 import / image:upload。
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role_id`, `status`)
SELECT 'guest',
       '$2a$12$qwC0z3.3HxTs.4Rkuc/iFOG4rIpmY2xGpa3ZomM3VolHj9rB1ZbrW',
       '游客',
       r.`id`, 1
FROM `sys_role` r WHERE r.`role_key` = 'guest'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`), `real_name` = VALUES(`real_name`);

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role_id`, `status`)
SELECT 'test',
       '$2a$12$ytWlN8zXfBwOXYisbVJFAORg7SVKemFAN9VfGaZ2fQ2hnvdBR.Vg2',
       '测试账号',
       r.`id`, 1
FROM `sys_role` r WHERE r.`role_key` = 'guest'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`), `real_name` = VALUES(`real_name`);

-- 第二个管理员（决策-004）：与 admin 同角色，权限同样来自 admin 角色（13 项）。
-- 这一条的**明文密码不写进仓库**（上面三条是历史遗留的写法，不再沿用）——
-- 本文件里只留密文，需要重置时用 org.example.util.BCryptUtil.encode("新密码") 生成后替换。
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role_id`, `status`)
SELECT 'eetig',
       '$2a$12$63/rcK3f01zHKpD9nsKp.OP52IYpDSY0W.bOw0aWy80adDKl0vkxS',
       '管理员',
       r.`id`, 1
FROM `sys_role` r WHERE r.`role_key` = 'admin'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`), `real_name` = VALUES(`real_name`);


-- ---------------------------------------------------------------------------
-- 物料主数据
--
-- 来源：HND 物料查询加载项的 materials.json（244 条），一次性导入。
-- 该 JSON 由某份 Excel（含「主产品及联、副产品」「包材（自用）」「外贸包材」等工作表）
-- 转换而来，source_sheet / source_row 保留以便追溯。
--
-- ⚠️ 源数据未经校验，已知存在自相矛盾的行
--    （如 spec 写「500ml/瓶」而 name 写「1000ml」），导入后建议抽查。
--
-- 幂等：以 (material_code, material_name) 为键 ON DUPLICATE KEY UPDATE。
--   enabled 有意不在更新列中 —— 重复执行不会把手工停用的行重新启用。
-- ---------------------------------------------------------------------------
INSERT INTO `material_master`
  (`material_code`, `material_name`, `spec`, `unit`, `material_type`, `material_group`, `product_group`, `source_sheet`, `source_row`)
VALUES
  ('114001895', 'HND-TMOS(H)', '5、25kg塑桶、200kg塑桶、吨桶', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 2),
  ('114002486', 'HND-2171', '5、25kg塑桶、200kg铁桶、吨桶', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 3),
  ('114001896', 'HND-V171', '5、25kg塑桶、200kg铁桶、吨桶', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 4),
  ('114001897', 'HND-V150', '5、25kg塑桶、200kg铁桶、槽车', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 5),
  ('114001898', 'HND-TMOS(L)', '5、25kg塑桶、200kg塑桶、吨桶', 'KG', 'Z004', '121299', '20', '主产品及联、副产品', 6),
  ('114001899', 'HND-D171', '/', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 7),
  ('114001900', 'HND-D150', '/', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 8),
  ('114001901', 'HND-D2150', '/', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 9),
  ('114001902', 'HND-N113', '5、25kg塑桶、200kg铁桶、吨桶', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 10),
  ('114001903', '电石渣', '槽车', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 11),
  ('114001904', '硅渣', '/', 'KG', 'Z004', '121201', '20', '主产品及联、副产品', 12),
  ('113000129', '三甲氧基氢硅烷', '25kg铁桶', 'KG', 'Z003', '1311', '20', '主产品及联、副产品', 13),
  ('113000130', '乙炔', '/', 'KG', 'Z003', '1311', '20', '主产品及联、副产品', 14),
  ('114002504', '回收甲醇', '/', 'KG', 'Z003', '1311', '20', '主产品及联、副产品', 15),
  ('114002501', 'HND-2171前馏份', '200kg铁桶', 'KG', 'Z004', '1311', '20', '主产品及联、副产品', 16),
  ('114002501', 'HND-2171过渡馏份', '200kg铁桶', 'KG', 'Z004', '1311', '20', '主产品及联、副产品', 17),
  ('114002499', 'HND-2171高沸物', '200kg铁桶', 'KG', 'Z004', '1311', '20', '主产品及联、副产品', 18),
  ('114001938', 'HND-V171合成粗品', '/', 'KG', 'Z004', '1311', '20', '主产品及联、副产品', 19),
  ('114001940', 'HND-V150合成粗品', '/', 'KG', 'Z004', '1311', '20', '主产品及联、副产品', 20),
  ('114001905', '盐酸', '25kg/桶', 'KG', 'Z004', '121201', '20', '原材料', 2),
  ('111001785', '硅粉', '500kg/袋', 'KG', 'Z001', '1101', '20', '原材料', 3),
  ('111001786', '电石', '1000kg/袋', 'KG', 'Z001', '1101', '20', '原材料', 4),
  ('111001787', '三氯氢硅', '吨/槽车', 'KG', 'Z001', '1101', '20', '原材料', 5),
  ('111001788', '甲醇', '吨/槽车', 'KG', 'Z001', '1101', '20', '原材料', 6),
  ('111001789', '次氯酸钠', '25kg/桶', 'KG', 'Z001', '1101', '20', '原材料', 7),
  ('111001790', '液碱', '吨/槽车', 'KG', 'Z001', '1101', '20', '原材料', 8),
  ('111001791', '无水氯化钙', '25kg/袋', 'KG', 'Z001', '1101', '20', '原材料', 9),
  ('111001792', '氯铂酸', '10g/瓶', 'KG', 'Z001', '1101', '20', '原材料', 10),
  ('111001793', '氯苯', '200kg/桶', 'KG', 'Z001', '1101', '20', '原材料', 11),
  ('111001808', '1.2-双三甲氧基硅基乙烷（溶剂）', '200kg/桶', 'KG', 'Z001', '1102', '20', '原材料', 12),
  ('111001836', '碳酸氢钠', '500g/瓶', 'KG', 'Z001', '1102', '20', '原材料', 13),
  ('111001838', '甲醇钠', '160kg/桶', 'KG', 'Z001', '1102', '20', '原材料', 14),
  ('111001856', '二甲基二苯基醚', '220kg/桶', 'KG', 'Z001', '1102', '20', '原材料', 15),
  ('111001418', '烯丙基缩水甘油醚', '190kg/桶', 'KG', 'Z001', '1101', '20', '原材料', 16),
  ('111002160', '电子甲醇（HB6017）', NULL, NULL, NULL, NULL, NULL, '原材料', 17),
  ('112004289', '三甲氧基氢硅烷辅料包', '10kg/包', 'KG', 'Z002', '14', '17', '催化剂和辅料包', 2),
  ('112004290', 'HND-TMOS(H)_辅料包', '3-5kg/桶', 'KG', 'Z002', '14', '17', '催化剂和辅料包', 3),
  ('112004291', 'HND-V171_辅料包', '3-5kg/桶', 'KG', 'Z002', '14', '17', '催化剂和辅料包', 4),
  ('112004292', 'HND-V150_辅料包', '3-5kg/桶', 'KG', 'Z002', '14', '17', '催化剂和辅料包', 5),
  ('111001831', '四甲基四乙烯基环四硅氧烷', '25kg/桶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 6),
  ('111001832', '咔唑', '1KG/袋', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 7),
  ('111001833', '150抗氧化剂', '25kg/袋', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 8),
  ('111001834', '吩噻嗪', '200kg/桶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 9),
  ('111001835', '乙烯基双封头', '25kg/桶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 10),
  ('111001411', '正丁醇', '25kg/桶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 11),
  ('111000363', '异丙醇', '25kg/桶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 12),
  ('112005353', 'HND-2171辅料包', '3-5kg/桶', 'KG', 'Z002', '14', '17', '催化剂和辅料包', 13),
  ('111002449', '冰乙酸', '500ml/瓶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 14),
  ('111002448', '乙酰丙酮', '500ml/瓶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 15),
  ('111002109', '四氢呋喃', '500ml/瓶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 16),
  ('111002110', '环体', '200kg/桶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 17),
  ('230010882', '二甲苯', '500ml/瓶', 'KG', 'Z001', '1102', '17', '催化剂和辅料包', 18),
  ('220000440', '5kg 塑料桶 白色_华耐德', '5kg/桶', 'PC', 'Z006', '2001', '10', '包材（自用）', 2),
  ('220000441', '25kg 塑料桶 蓝色_华耐德', '25kg/桶', 'PC', 'Z006', '2001', '10', '包材（自用）', 3),
  ('220000442', '25kg 内衬铁桶 蓝色_华耐德', '25kg/桶', 'PC', 'Z006', '2002', '10', '包材（自用）', 4),
  ('220000443', '200kg 塑料桶 蓝色_华耐德', '200kg/桶', 'PC', 'Z006', '2001', '10', '包材（自用）', 5),
  ('220000444', '200kg 内衬PVF铁桶 蓝色_华耐德', '200kg/桶', 'PC', 'Z006', '2002', '10', '包材（自用）', 6),
  ('220000445', '吨桶PE 白色_华耐德', '1000kg/桶', 'PC', 'Z006', '2003', '10', '包材（自用）', 7),
  ('220000464', '20kg_塑料内桶_白色_华耐德', '20kg/桶', 'PC', 'Z006', '2001', '10', '包材（自用）', 8),
  ('220000465', '190kg_铁桶_蓝色_华耐德', '190kg/桶', 'PC', 'Z006', '2002', '10', '包材（自用）', 9),
  ('220000466', '950kg_IBC桶_白色_华耐德', '950kg/桶', 'PC', 'Z006', '2003', '10', '包材（自用）', 10),
  ('220000467', '5kg_内涂桶_蓝色_华耐德', '5kg/桶', 'PC', 'Z006', '2001', '10', '包材（自用）', 11),
  ('220000468', '28kg_塑料桶_蓝色_华耐德', '28kg/桶', 'PC', 'Z006', '2001', '10', '包材（自用）', 12),
  ('220000486', '190kg_塑料桶_蓝色_华耐德', '190kg/桶', 'PC', 'Z006', '2002', '10', '包材（自用）', 13),
  ('220000469', '1000ml氟化塑料瓶', '1000ml/瓶', 'ml', 'Z006', '2008', '10', '包材（自用）', 14),
  ('220000470', '500ml氟化塑料瓶', '500ml/瓶', 'ml', 'Z006', '2008', '10', '包材（自用）', 15),
  ('220000471', '250ml氟化塑料瓶', '250ml/瓶', 'ml', 'Z006', '2008', '10', '包材（自用）', 16),
  ('220000472', '100ml氟化塑料瓶', '100ml/瓶', 'ml', 'Z006', '2008', '10', '包材（自用）', 17),
  ('115055979', 'HND-V171_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 2),
  ('115055980', 'HND-V150_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 3),
  ('115055981', 'HND-TMOS(H)_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 4),
  ('115055982', 'HND-D171_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 5),
  ('115055983', 'HND-D150_200KG_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 6),
  ('115055984', 'HND-D2150_200KG_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 7),
  ('115055985', 'HND-N113_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 8),
  ('115055989', 'HND-V171_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 9),
  ('115055990', 'HND-V150_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 10),
  ('115055991', 'HND-TMOS(H)_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 11),
  ('115055992', 'HND-D171_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 12),
  ('115055993', 'HND-D150_200KG_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 13),
  ('115055994', 'HND-D2150_200KG_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 14),
  ('115055995', 'HND-N113_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 15),
  ('115056001', 'HND-TMOS(L)_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1539', '20', '包成品（自用）', 16),
  ('115056003', 'HND-D150_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1539', '20', '包成品（自用）', 17),
  ('115056004', 'HND-D2150_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1539', '20', '包成品（自用）', 18),
  ('115056008', '盐酸_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1539', '20', '包成品（自用）', 19),
  ('115057979', 'HND-V150_槽车', '吨/槽车', 'KG', 'Z005', '1539', '20', '包成品（自用）', 20),
  ('115056009', '盐酸_槽车', '吨/槽车', 'KG', 'Z005', '1503', '20', '包成品（自用）', 21),
  ('115056200', 'HND-TMOS(H)_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 22),
  ('115056201', 'HND-TMOS(H)_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 23),
  ('115056202', 'HND-TMOS(H)_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 24),
  ('115056203', 'HND-TMOS(H)_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 25),
  ('115056204', 'HND-V171_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 26),
  ('115056205', 'HND-V171_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 27),
  ('115056206', 'HND-V171_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 28),
  ('115056207', 'HND-V171_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 29),
  ('115056208', 'HND-V150_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 30),
  ('115056209', 'HND-V150_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 31),
  ('115056210', 'HND-V150_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 32),
  ('115056211', 'HND-V150_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 33),
  ('115056212', 'HND-D171_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 34),
  ('115056213', 'HND-D171_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 35),
  ('115056214', 'HND-D171_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 36),
  ('115056215', 'HND-D171_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 37),
  ('115056216', 'HND-N113_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 38),
  ('115056217', 'HND-N113_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 39),
  ('115056218', 'HND-N113_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 40),
  ('115056219', 'HND-N113_1000kg_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 41),
  ('115056298', 'HND-TMOS(L)_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 42),
  ('115056299', 'HND-TMOS(L)_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 43),
  ('115056300', 'HND-TMOS(L)_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 44),
  ('115056301', 'HND-TMOS(L)_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 45),
  ('115056302', 'HND-TMOS(L)_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 46),
  ('115056406', 'HND-V171_20kg_塑料内桶_白色_华耐德', '20kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 47),
  ('115056407', 'HND-V171_25kg_塑料桶_蓝色_华耐德(外贸)', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 48),
  ('115056408', 'HND-V171_190kg_铁桶_蓝色_华耐德', '190kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 49),
  ('115056409', 'HND-V171_950kg_IBC桶_白色_华耐德', '950kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 50),
  ('115056410', 'HND-TMOS(H)_5kg_内涂桶_蓝色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 51),
  ('115056411', 'HND-TMOS(L)_5kg_内涂桶_蓝色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 52),
  ('115056412', 'HND-N113_28kg_塑料桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 53),
  ('115056413', 'HND-N113_190kg_塑料桶_蓝色_华耐德', '190kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 54),
  ('115056414', 'HND-N113_190kg_铁桶_蓝色_华耐德', '190kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 55),
  ('115056415', 'HND-N113_900kg_IBC桶_白色_华耐德', '900kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 56),
  ('115058270', 'HND-V171_28kg_塑料桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 57),
  ('115058271', 'HND-V150_28kg_塑料桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 58),
  ('115058272', 'HND-TMOS(L)_28kg_塑料桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 59),
  ('115058273', 'HND-TMOS(H)28kg_塑料桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 60),
  ('115060158', 'HND-560_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 61),
  ('115060159', 'HND-560_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 62),
  ('115060160', 'HND-560_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 63),
  ('115060161', 'HND-560_28kg_塑料桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 64),
  ('115060162', 'HND-560_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 65),
  ('115060163', 'HND-560_200kg 内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 66),
  ('115060164', 'HND-560_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 67),
  ('115060391', 'HND-2171高沸物_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 68),
  ('115060365', 'HND-2171过渡馏份_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 69),
  ('115060366', 'HND-2171过渡馏份_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 70),
  ('115060594', '三甲氧基氢硅烷_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 71),
  ('115060595', '三甲氧基氢硅烷_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 72),
  ('115060596', '三甲氧基氢硅烷_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 73),
  ('115060597', '三甲氧基氢硅烷_28kg塑料桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 74),
  ('115060598', '三甲氧基氢硅烷_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 75),
  ('115060599', '三甲氧基氢硅烷_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 76),
  ('115060192', 'HND-2171_5kg_塑料桶_白色_华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 77),
  ('115060193', 'HND-2171_25kg_塑料桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 78),
  ('115060194', 'HND-2171_25kg_内衬铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 79),
  ('115060195', 'HND-2171_28kg塑料桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 80),
  ('115060196', 'HND-2171_200kg_塑料桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 81),
  ('115060197', 'HND-2171_200kg_内衬PVF铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 82),
  ('115060198', 'HND-2171_吨桶PE_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '包成品（自用）', 83),
  ('220000647', '5KG方形塑料外部贸易桶_白色_华耐德', '5kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 2),
  ('220000463', '25kg_塑料外贸桶_蓝色_华耐德', '25kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 3),
  ('220000611', '25kg_塑料外部贸易桶_蓝色_华耐德', '25kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 4),
  ('220000612', '25kg_内衬外部贸易铁桶_蓝色_华耐德', '25kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 5),
  ('220000613', '28kg_塑料外部贸易桶_蓝色_华耐德', '28kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 6),
  ('220000614', '200kg_内衬PVF外贸铁桶_蓝色_华耐德', '200kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 7),
  ('220000615', 'PE_外部贸易吨桶_白色_华耐德', '1000kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 8),
  ('220000616', 'PE_外部贸易吨桶_黑色_华耐德', '1000kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 9),
  ('220000620', '200kg_塑料外部贸易桶_蓝色_华耐德', '200kg/桶', 'PC', 'Z006', '2001', '10', '外贸包材', 10),
  ('220000469', '1000ml外贸氣化塑料瓶_客供_外贸', '1000ml/瓶', 'PC', 'Z006', '2001', '10', '外贸包材', 11),
  ('220000470', '1000ml外贸氣化塑料瓶_客供_外贸', '500ml/瓶', 'PC', 'Z006', '2001', '10', '外贸包材', 12),
  ('220000471', '1000ml外贸氣化塑料瓶_客供_外贸', '250ml/瓶', 'PC', 'Z006', '2001', '10', '外贸包材', 13),
  ('220000472', '1000ml外贸氣化塑料瓶_客供_外贸', '100ml/瓶', 'PC', 'Z006', '2001', '10', '外贸包材', 14),
  ('115059719', 'HND-V171_25kg_塑料外部贸易桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 2),
  ('115059720', 'HND-V171_25kg_内衬外部贸易铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 3),
  ('115059721', 'HND-V171_28kg_塑料外部贸易桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 4),
  ('115059722', 'HND-V171_200kg_内衬PVF外贸铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 5),
  ('115059723', 'HND-V171_PE_外部贸易吨桶_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 6),
  ('115059724', 'HND-V171_PE_外部贸易吨桶_黑色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 7),
  ('115059725', 'HND-V150_25kg_塑料外部贸易桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 8),
  ('115059726', 'HND-V150_25kg_内衬外部贸易铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 9),
  ('115059727', 'HND-V150 28kg 塑料外部贸易桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 10),
  ('115059728', 'HND-V150_200kg_内衬PVF外贸铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 11),
  ('115059729', 'HND-V150_PE_外部贸易吨桶_白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 12),
  ('115059730', 'HND-V150_PE_外部贸易吨桶_黑色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 13),
  ('115059731', 'HND-TMOS(L)_25kg 塑料外部贸易桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 14),
  ('115059732', 'HND-TMOS(L)_25kg_内衬外部贸易铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 15),
  ('115059733', 'HND-TMOS(L)_28kg_塑料外部贸易桶_蓝色_华耐德', '28kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 16),
  ('115059734', 'HND-TMOS(L)_200kg_内衬PVF外贸铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 17),
  ('115059735', 'HND-TMOS(L)_PE_外部贸易吨桶_白色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 18),
  ('115059736', 'HND-TMOS(L)_PE_外部贸易吨桶_黑色_华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 19),
  ('115059767', 'HND-V171_200kg_塑料外部贸易桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 20),
  ('115059768', 'HND-V150_200kg_塑料外部贸易桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 21),
  ('115059769', 'HND-TMOS(L)_200kg_塑料外部贸易桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 22),
  ('115060113', 'HND-560 5kg 塑料外部贸易桶 蓝色 华耐德', '5kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 23),
  ('115060114', 'HND-560 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 24),
  ('115060115', 'HND-560 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 25),
  ('115060116', 'HND-N823 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 26),
  ('115060117', 'HND-N823 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 27),
  ('115060118', 'HND-530 25kg 塑料外部贸易桶 蓝色 华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 28),
  ('115060119', 'HND-530 25kg_内衬外部贸易铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 29),
  ('115060120', 'HND-540 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 30),
  ('115060121', 'HND-540 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 31),
  ('115060122', 'HND-550 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 32),
  ('115060123', 'HND-550 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 33),
  ('115060124', 'HND-561 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 34),
  ('115060125', 'HND-561 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 35),
  ('115060126', 'HND-563 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 36),
  ('115060127', 'HND-563 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 37),
  ('115060128', 'HND-570 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 38),
  ('115060129', 'HND-570 PE 外部贸易吨桶 黑色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 39),
  ('115060130', 'HND-602 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 40),
  ('115060131', 'HND-602 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 41),
  ('115060132', 'HND-792 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 42),
  ('115060133', 'HND-530 25kg 塑料外部贸易桶 蓝色 华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 43),
  ('115060134', 'HND-540 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 44),
  ('115060135', 'HND-540 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 45),
  ('115060136', 'HND-MEKO 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 46),
  ('115060137', 'HND-MEKO PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 47),
  ('115060138', 'HND-PTMS 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 48),
  ('115060139', 'HND-MOS 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 49),
  ('115060140', 'HND-MOS PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 50),
  ('115060141', 'HND-MOS 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 51),
  ('115060142', 'HND-MOS PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 52),
  ('115060143', 'HND-DBTDA 25kg 塑料外部贸易桶 蓝色 华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 53),
  ('115060144', 'HND-DBTDA 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 54),
  ('115060145', 'HND-DBTDL 25kg 塑料外部贸易桶 蓝色 华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 55),
  ('115060146', 'HND-DBTDL 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 56),
  ('115060147', 'HND-SI28 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 57),
  ('115060148', 'HND-SI28 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 58),
  ('115060149', 'HND-HMDSO 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 59),
  ('115060150', 'HND-HMDS 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 60),
  ('115060151', 'HND-SI40 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 61),
  ('115060152', 'HND-SI40 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 62),
  ('115060153', 'HND-N123 200kg 内衬PVF外贸铁桶 蓝色 华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 63),
  ('115060154', 'HND-N123 PE 外部贸易吨桶 白色 华耐德', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 64),
  ('115061010', 'HND-2171 25kg 塑料桶 蓝色_华耐德 客供 外贸', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 65),
  ('115061011', 'HND-2171 200kg 塑料桶 蓝色 华耐德 客供 外贸', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 66),
  ('115061012', 'HND-2171 200kg 内衬PVF铁桶 蓝色 华耐德 客供 外贸', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 67),
  ('115061013', 'HND-2171 吨桶PE 白色_华耐德 客供 外贸', '1000kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 68),
  ('115060609', '三甲氧基氢硅烷_25kg_内衬pvf外贸铁桶_蓝色_华耐德', '25kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 69),
  ('115060610', '三甲氧基氢硅烷_200kg_内衬pvf外贸铁桶_蓝色_华耐德', '200kg/桶', 'KG', 'Z005', '1531', '20', '外贸包成品', 70),
  ('115060155', 'HND-C123 槽车', '吨/槽车', 'KG', 'Z005', '1531', '20', '外贸包成品', 71),
  ('115060156', 'HND-STC 槽车', '吨/槽车', 'KG', 'Z005', '1531', '20', '外贸包成品', 72),
  ('115060157', 'HND-N803 槽车', '吨/槽车', 'KG', 'Z005', '1531', '20', '外贸包成品', 73),
  ('115060640', 'HND-530_1000ml 外贸氟化塑料瓶_客供_外贸', '1000ml/瓶', 'KG', 'Z005', '1531', '20', '外贸包成品', 74),
  ('115061006', 'HND-530_500ml 外贸氟化塑料瓶_客供_外贸', '500ml/瓶', 'KG', 'Z005', '1531', '20', '外贸包成品', 75),
  ('115061007', 'HND-530_250ml 外贸氟化塑料瓶_客供_外贸', '250ml/瓶', 'KG', 'Z005', '1531', '20', '外贸包成品', 76),
  ('115061008', 'HND-530_100ml 外贸氟化塑料瓶_客供_外贸', '100ml/瓶', 'KG', 'Z005', '1531', '20', '外贸包成品', 77),
  ('111001810', '硫酸', '/', 'KG', 'Z001', '1102', '17', '其它', 2),
  ('111001811', '双氧水', '/', 'KG', 'Z001', '1102', '17', '其它', 3),
  ('111001812', '聚合氯化铝', '10kg/袋', 'KG', 'Z001', '1102', '17', '其它', 4),
  ('111001813', '聚丙烯酰胺', '10kg/袋', 'KG', 'Z001', '1102', '17', '其它', 5),
  ('111001814', '硫酸亚铁', '/', 'KG', 'Z001', '1102', '17', '其它', 6)
ON DUPLICATE KEY UPDATE
  `spec`           = VALUES(`spec`),
  `unit`           = VALUES(`unit`),
  `material_type`  = VALUES(`material_type`),
  `material_group` = VALUES(`material_group`),
  `product_group`  = VALUES(`product_group`),
  `source_sheet`   = VALUES(`source_sheet`),
  `source_row`     = VALUES(`source_row`);


-- ---------------------------------------------------------------------------
-- 设备台账（静设备）
--
-- 来源：《容器封头参数表 2025.06》（容器名称 / 直径×筒体 / V封头容积 m3 / V1m3/mm），
--       逐行照抄，source_row 即源表序号，便于与纸面核对。
--
-- 说明与已知疑点（照抄 + 标注，不改数）：
--   1. 源表只给「每 mm 液位对应体积」(V1m3/mm)，**未给介质密度**，故 mass_per_mm、
--      density、medium 一律留空 —— 质量要等业务侧按物料密度补，本文件不猜。
--      补齐后可执行（例）：
--        UPDATE `equipment_ledger` SET `medium`='甲醇', `density`=0.792
--          WHERE `medium` IS NULL AND `equipment_name` LIKE '甲醇%';
--   2. 平底容器（正/安全/逆水封、氮气缓冲罐、高位槽、甲醇储罐…）源表只有一列数值，
--      按 V1m3/mm 落库，head_volume 留空并注明「平底」。
--   3. 源表标注「需用软件计算」的行（卧式罐、部分缓冲罐）：head_volume 有值、volume_per_mm 留空。
--   4. 两处疑似笔误，已按原值落库并在 remark 标注，待业务确认后覆盖：
--      · 碱液高位槽 / 次钠高位槽（Φ1800x1840）的 V1 填 0.000785 —— 按 Φ1800 应为 0.002545
--        （0.000785 是 Φ1000 的值）；
--      · 三只 150 粗蒸 / 含苄溶剂 / 150 反应粗品罐（DN2800x1700）的 V1 填 0.01134 ——
--        该值对应公称直径约 3800mm，按 DN2800 应为 0.00616。
--
-- 幂等：以 (source_sheet, source_row) 为键 ON DUPLICATE KEY UPDATE；
--       **不覆盖** density / medium / mass_per_mm / enabled —— 重复执行本文件不会
--       把业务侧补好的密度和手工停用的行冲掉（同 material_master 的 enabled 处理）。
-- ---------------------------------------------------------------------------
INSERT INTO `equipment_ledger`
  (`equipment_code`, `equipment_name`, `spec`, `head_volume`, `volume_per_mm`, `remark`, `source_sheet`, `source_row`)
VALUES
  ('', '三甲加料罐',        'DN1600x2000',                0.5864, 0.00201,  NULL, '容器封头参数表2025.06', 1),
  ('', '反应物接受罐（171）', 'DN2400x3600',               1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 2),
  ('', '立式泵尾气缓冲罐',   'DN1000x3400（卧式）',         0.1505, NULL,     '源表标注「需用软件计算」，V1m3/mm 待补齐', '容器封头参数表2025.06', 3),
  ('', '粗蒸溶剂接受罐',     'DN2000x1500',                1.1257, 0.00314,  NULL, '容器封头参数表2025.06', 4),
  ('', '粗蒸粗品接受罐',     'DN2000x1500',                1.1257, 0.00314,  NULL, '容器封头参数表2025.06', 5),
  ('', '尾渣罐（高沸）',     'DN1400x1600',                0.3977, 0.00154,  NULL, '容器封头参数表2025.06', 6),
  ('', '前分罐',            'DN2000x1500',                1.1257, 0.00314,  NULL, '容器封头参数表2025.06', 7),
  ('', '产品罐',            'DN2000x1500',                1.1257, 0.00314,  NULL, '容器封头参数表2025.06', 8),
  ('', '溶剂接受罐（带搅拌）', 'DN2400x3600',               1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 9),
  ('', '粗品接受罐',         'DN2400x3600',                1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 10),
  ('', '反应尾气冷凝罐',     'DN1200x1400',                0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 11),
  ('', '真空缓冲罐',         'DN700x1000',                 0.0545, 0.000385, NULL, '容器封头参数表2025.06', 12),
  ('', '171产品包装罐A/B',   'DN1400x1600',                0.3977, 0.00154,  NULL, '容器封头参数表2025.06', 13),
  ('', '高含量产品接受罐',   'DN1600x1500',                0.5864, 0.00201,  NULL, '容器封头参数表2025.06', 14),
  ('', '150产品罐',         'DN2400x1700',                1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 15),
  ('', '尾渣罐（高沸）',     'DN1500/DN1400x1200（夹套）',   0.3977, 0.00154,  NULL, '容器封头参数表2025.06', 16),
  ('', '低含量产品接受罐',   'DN1900x1400',                0.9687, 0.00284,  NULL, '容器封头参数表2025.06', 17),
  ('', '前馏接受罐',         'DN1900x1400',                0.9687, 0.00284,  NULL, '容器封头参数表2025.06', 18),
  ('', '溶剂罐（中间）',     'DN1900x1400',                0.9687, 0.00284,  NULL, '容器封头参数表2025.06', 19),
  ('', '150前馏罐',         'DN2400x1700',                1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 20),
  ('', '150粗蒸粗品罐',      'DN2800x1700',                3.1198, 0.01134,  '源表 V1m3/mm=0.01134 对应公称直径约 3800mm，与规格 DN2800 不符（应为 0.00616），待业务核对', '容器封头参数表2025.06', 21),
  ('', '含苄溶剂储罐',       'DN2800x1700',                3.1198, 0.01134,  '源表 V1m3/mm=0.01134 对应公称直径约 3800mm，与规格 DN2800 不符（应为 0.00616），待业务核对', '容器封头参数表2025.06', 22),
  ('', '150反应粗品罐',      'DN2800x1700',                3.1198, 0.01134,  '源表 V1m3/mm=0.01134 对应公称直径约 3800mm，与规格 DN2800 不符（应为 0.00616），待业务核对', '容器封头参数表2025.06', 23),
  ('', '150反应尾气缓冲罐',  'DN1400x1200',                0.3977, 0.00154,  NULL, '容器封头参数表2025.06', 24),
  ('', '真空缓冲罐',         'DN800x1000',                 0.0796, 0.000503, NULL, '容器封头参数表2025.06', 25),
  ('', '150搪瓷罐',         'DN900/DN800x1000（夹套）',     0.0796, 0.000503, NULL, '容器封头参数表2025.06', 26),
  ('', '正水封',            'Φ800x1100（平底）',            NULL,   0.000503, '平底，源表未给封头容积', '容器封头参数表2025.06', 27),
  ('', '安全水封',          'Φ800x1100（平底）',            NULL,   0.000503, '平底，源表未给封头容积', '容器封头参数表2025.06', 28),
  ('', '逆水封',            'Φ800x1100（平底）',            NULL,   0.000503, '平底，源表未给封头容积', '容器封头参数表2025.06', 29),
  ('', '乙炔缓冲罐',         'Φ1200x2500（卧式）',          0.2545, NULL,     '源表标注「需用软件计算」，V1m3/mm 待补齐', '容器封头参数表2025.06', 30),
  ('', '氮气缓冲罐',         'Φ1000x1800（平底）',          NULL,   0.000785, '平底，源表未给封头容积', '容器封头参数表2025.06', 31),
  ('', '次钠高位槽',         'Φ1800x1840（平底）',          NULL,   0.000785, '平底；源表 V1 填 0.000785（Φ1000 的值），按 Φ1800 应为 0.002545，待业务核对', '容器封头参数表2025.06', 32),
  ('', '次钠配料槽',         'Φ2250x2540（平底）',          NULL,   0.00398,  '平底，源表未给封头容积', '容器封头参数表2025.06', 33),
  ('', '碱液高位槽',         'Φ1800x1840（平底）',          NULL,   0.000785, '平底；源表 V1 填 0.000785（Φ1000 的值），按 Φ1800 应为 0.002545，待业务核对', '容器封头参数表2025.06', 34),
  ('', '高位水罐',          'Φ2000x2500（卧式）',          1.1257, NULL,     '源表标注「需用软件计算」，V1m3/mm 待补齐', '容器封头参数表2025.06', 35),
  ('', '自来水高位水箱',     'Φ1200x2500（卧式）',          0.2545, NULL,     '源表标注「需用软件计算」，V1m3/mm 待补齐', '容器封头参数表2025.06', 36),
  ('', '甲醇计量罐',         'DN2300/DN2200x3400（夹套）',   1.5459, 0.0038,   NULL, '容器封头参数表2025.06', 37),
  ('', '硅粉计量罐',         'DN1400x1600（下锥体高470）',   0.359,  0.00154,  '封头容积取下锥体容积', '容器封头参数表2025.06', 38),
  ('', '脱水釜尾气缓冲罐',   'DN800x1200',                 0.0796, 0.000503, NULL, '容器封头参数表2025.06', 39),
  ('', '配料釜尾气缓冲罐',   'DN800x1200',                 0.0796, 0.000503, NULL, '容器封头参数表2025.06', 40),
  ('', '原装溶剂计量罐',     'DN800x2700',                 0.0796, 0.000503, NULL, '容器封头参数表2025.06', 41),
  ('', '应急缓冲罐',         'DN1200x1400',                0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 42),
  ('', '三甲粗品罐',         'DN1700/DN1600x2000（夹套）',   0.5864, 0.00201,  NULL, '容器封头参数表2025.06', 43),
  ('', '反应尾气缓冲罐',     'DN1000x1500（卧式）',          0.1505, NULL,     '源表未给 V1m3/mm，待补齐', '容器封头参数表2025.06', 44),
  ('', '三甲产品中转罐',     'DN1800x3400',                0.827,  0.00254,  NULL, '容器封头参数表2025.06', 45),
  ('', '抽出物接受罐',       'DN1400x1600',                0.3977, 0.00154,  NULL, '容器封头参数表2025.06', 46),
  ('', '导热油补加罐',       'DN1400x1600',                0.3977, 0.00154,  NULL, '容器封头参数表2025.06', 47),
  ('', '真空缓冲罐',         'DN700x1000',                 0.0545, 0.000385, NULL, '容器封头参数表2025.06', 48),
  ('', '真空缓冲罐',         'DN700x1000',                 0.0545, 0.000385, NULL, '容器封头参数表2025.06', 49),
  ('', '真空缓冲罐',         'DN700x1000',                 0.0545, 0.000385, '与序号 48/50 同规格，源表确为三台独立容器', '容器封头参数表2025.06', 50),
  ('', '回收溶剂储罐',       'DN2200x3400',                1.5459, 0.0038,   NULL, '容器封头参数表2025.06', 51),
  ('', '甲醇接受罐',         'DN1100/DN1000x1600（夹套）',   0.1505, 0.000785, NULL, '容器封头参数表2025.06', 52),
  ('', '轻组分接受罐',       'DN1000x1600',                 0.1505, 0.000785, NULL, '容器封头参数表2025.06', 53),
  ('', '三甲接受罐',         'DN1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 54),
  ('', '20m3渣浆储罐（P=15KW）', 'DN2400x3600',             1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 55),
  ('', '导热油储油罐',       'Φ1400x1600',                 0.3977, 0.00154,  NULL, '容器封头参数表2025.06', 56),
  ('', '溶剂蒸馏缓冲罐A',    'Φ700x1000',                  0.0545, 0.000385, NULL, '容器封头参数表2025.06', 57),
  ('', '溶剂蒸馏缓冲罐B',    'Φ700x1000',                  0.0545, 0.000385, NULL, '容器封头参数表2025.06', 58),
  ('', '溶剂前分处理一级缓冲罐A', 'Φ700x1000',              0.0545, 0.000385, NULL, '容器封头参数表2025.06', 59),
  ('', '溶剂前分处理一级缓冲罐B', 'Φ700x1000',              0.0545, 0.000385, NULL, '容器封头参数表2025.06', 60),
  ('', '溶剂前分处理二级缓冲罐A', 'Φ700x1000',              0.0545, 0.000385, NULL, '容器封头参数表2025.06', 61),
  ('', '溶剂前分处理二级缓冲罐B', 'Φ700x1000',              0.0545, 0.000385, NULL, '容器封头参数表2025.06', 62),
  ('', '立式泵尾气缓冲罐',   'Φ1000x3400（卧式）',          0.1505, NULL,     '源表标注「需用软件计算」，V1m3/mm 待补齐', '容器封头参数表2025.06', 63),
  ('', '四甲粗品接收罐A/B',  'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 64),
  ('', '甲醇滴加罐',         'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 65),
  ('', '四甲粗品接收罐',     'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 66),
  ('', '三甲粗品罐（四甲）',  'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 67),
  ('', '甲基三甲氧前分罐',   'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 68),
  ('', '甲基三甲氧产品罐',   'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 69),
  ('', '溶剂产品罐A/B',      'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 70),
  ('', '溶剂过滤液罐A/B',    'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 71),
  ('', '三甲前分罐',         'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 72),
  ('', '溶剂前分罐',         'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 73),
  ('', '配料釜尾气缓冲罐',   'Φ1200x1400',                 0.2545, 0.00113,  NULL, '容器封头参数表2025.06', 74),
  ('', '四甲产品中转罐',     'Φ2200x3400',                 1.5459, 0.0038,   NULL, '容器封头参数表2025.06', 75),
  ('', '甲基三甲氧粗品进料罐', 'Φ2200x3400',                1.5459, 0.0038,   NULL, '容器封头参数表2025.06', 76),
  ('', '甲基三甲氧产品中转罐', 'Φ2200x3400',                1.5459, 0.0038,   NULL, '容器封头参数表2025.06', 77),
  ('', '甲基三甲氧精品中转罐', 'Φ2200x3400',                1.5459, 0.0038,   NULL, '容器封头参数表2025.06', 78),
  ('', '四甲粗品中转罐',     'Φ2400x3600',                 1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 79),
  ('', '四甲半成品进料罐',   'Φ2400x3600',                 1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 80),
  ('', '四甲精品产品罐',     'DN2500/DN2400x4000',         1.9905, 0.00452,  NULL, '容器封头参数表2025.06', 81),
  ('', '甲醇接受罐',         'DN1900x1400',                 0.9687, 0.00284,  NULL, '容器封头参数表2025.06', 82),
  ('', '低含量四甲接受罐',   'DN1900x1400',                 0.9687, 0.00284,  NULL, '容器封头参数表2025.06', 83),
  ('', '高含量四甲接受罐',   'DN1900x1400',                 0.9687, 0.00284,  NULL, '容器封头参数表2025.06', 84),
  ('', '甲醇滴加罐',         'DN1600x1500',                 0.5864, 0.00201,  NULL, '容器封头参数表2025.06', 85),
  ('', '精品四甲尾渣罐',     'DN1500/DN1400x1400',          0.3977, 0.00154,  NULL, '容器封头参数表2025.06', 86),
  ('', '三氯氢硅储罐',       'DN2800x5500（卧式）',          3.1198, NULL,     '源表标注「需用软件计算」，V1m3/mm 待补齐', '容器封头参数表2025.06', 87),
  ('', '应急罐',            'DN2800x5500（卧式）',          3.1198, NULL,     '源表标注「需用软件计算」，V1m3/mm 待补齐', '容器封头参数表2025.06', 88),
  ('', '150产品罐',         'DN3600x4800（平底）',          NULL,   0.0102,   '平底，源表未给封头容积', '容器封头参数表2025.06', 89),
  ('', '甲醇储罐A',          'DN5200x5200（平底）',          NULL,   0.0212,   '平底，源表未给封头容积', '容器封头参数表2025.06', 90),
  ('', '甲醇储罐B',          'DN5200x5200（平底）',          NULL,   0.0212,   '平底，源表未给封头容积', '容器封头参数表2025.06', 91)
ON DUPLICATE KEY UPDATE
  `equipment_name` = VALUES(`equipment_name`),
  `spec`           = VALUES(`spec`),
  `head_volume`    = VALUES(`head_volume`),
  `volume_per_mm`  = VALUES(`volume_per_mm`),
  `remark`         = VALUES(`remark`);
  -- 有意不更新：density / medium / mass_per_mm（业务侧补的密度不能被重复导入冲掉）、
  --            enabled（手工停用的行不能被重新启用）


-- ---------------------------------------------------------------------------
-- 容器几何参数回填（变更-025）—— 只服务「压力容器体积计算」页
--
-- 这 7 列不在源台账《容器封头参数表》里，是照着**图纸/铭牌**填的，
-- 所以与上面的 91 行批量导入分开写：批量那份抄源表，这里这份逐台核对。
--
-- ⚠️ **每列都用 `COALESCE(列, 值)` 只填空、不覆盖**（2026-10-06 起）。
-- 原因：这些列现在是「设备数据维护」页可改的，用 `= 值` 会在重复执行本文件时
-- 把维护页刚改的值刷回去。与种子段「不冲掉业务侧补的 density / medium / enabled」同一条原则。
--
-- 口径（必须与 head_volume 对齐，填错一位整页数值就偏）：
--   · `shell_length` 是**不含封头直边**的筒体直段长，直边单列 `straight_flange`
--     —— 因为库里的 head_volume = 单个封头的「曲面 + 直边」，
--     拿它反算就能校验几何填得对不对（见下面每行的对账值）。
--   · 卧式容器的两端封头相同，`top_head_depth` 与 `bottom_head_depth` 填一样的值；
--     立式平底容器的 `bottom_head_depth` 留 NULL。
--   · 「朝向」不另开列：`container_type` 为 1 或 4 即卧式（该列由 spec 派生，见上一节）。
--
-- 定位方式用 (source_sheet, source_row) 而不是 id —— 那是本表既有的业务键，
-- 换库/重导后 id 会变，这两个不会。重复执行无副作用。
-- ---------------------------------------------------------------------------

-- 三氯氢硅储罐（Φ2800x5500 卧式，两端椭圆封头 D/4=700，直边 40）
--   对账：head_volume 应为 (2/3)π·1.4²·0.7 + π·1.4²·0.04 = 3.1202，库里 3.1198 ✓
--   介质与密度：源台账未给（第 2 部分开头那条示例 UPDATE 一直是注释状态），
--   这里补上前端原本写死的值。**用 COALESCE 只填空、不覆盖** ——
--   与上面种子段「不冲掉业务侧补的密度」是同一条原则，重复执行本段也不会改动手工改过的值。
UPDATE `equipment_ledger` SET
  `inner_diameter`    = COALESCE(`inner_diameter`,    2800),
  `shell_length`      = COALESCE(`shell_length`,      5500),
  `straight_flange`   = COALESCE(`straight_flange`,   40),
  `top_head_depth`    = COALESCE(`top_head_depth`,    700),
  `bottom_head_depth` = COALESCE(`bottom_head_depth`, 700),
  `image_file`        = COALESCE(`image_file`,        'vessel.png'),
  `image_bounds`      = COALESCE(`image_bounds`,      CAST('{"width":2150,"height":1060,"left":75,"right":2069,"top":131,"bottom":931}' AS JSON)),
  `medium`            = COALESCE(`medium`,  '三氯氢硅'),
  `density`           = COALESCE(`density`, 1.35)
WHERE `source_sheet` = '容器封头参数表2025.06' AND `source_row` = 87;

-- 甲醇计量罐（DN2300 夹套 / 内罐 DN2200x3400，**上下都有封头** D/4=550，直边 40）
--   对账：head_volume 应为 (2/3)π·1.1²·0.55 + π·1.1²·0.04 = 1.5459，库里 1.5459 ✓ 分毫不差
UPDATE `equipment_ledger` SET
  `inner_diameter`    = COALESCE(`inner_diameter`,    2200),
  `shell_length`      = COALESCE(`shell_length`,      3400),
  `straight_flange`   = COALESCE(`straight_flange`,   40),
  `top_head_depth`    = COALESCE(`top_head_depth`,    550),
  `bottom_head_depth` = COALESCE(`bottom_head_depth`, 550),
  `image_file`        = COALESCE(`image_file`,        'vessel-methanol.png'),
  `image_bounds`      = COALESCE(`image_bounds`,      CAST('{"width":708,"height":1520,"left":72,"right":627.5,"top":83,"tangent":218,"tangentBottom":1114,"bottom":1247}' AS JSON)),
  `medium`            = COALESCE(`medium`,  '甲醇'),
  `density`           = COALESCE(`density`, 0.792)
WHERE `source_sheet` = '容器封头参数表2025.06' AND `source_row` = 37;

-- 150产品罐（Φ3600x4800，**平底**，只有上封头 D/4=900，无直边）
--   注①：这一行源表未给 head_volume（remark 写着「平底，源表未给封头容积」），
--        故上面那套对账对它不适用 —— 页面上会自动跳过没有 head_volume 的行
--   注②：**不写 `bottom_head_depth`** —— 平底就该留 NULL，而 `COALESCE(bottom_head_depth, NULL)`
--        是个空操作；写成 `= NULL` 反而会把维护页刚填的下封头深清掉。
UPDATE `equipment_ledger` SET
  `inner_diameter`    = COALESCE(`inner_diameter`, 3600),
  `shell_length`      = COALESCE(`shell_length`,   4800),
  `straight_flange`   = COALESCE(`straight_flange`, 0),
  `top_head_depth`    = COALESCE(`top_head_depth`, 900),
  `image_file`        = COALESCE(`image_file`,     'vessel-product150.png'),
  `image_bounds`      = COALESCE(`image_bounds`,   CAST('{"width":1760,"height":1938,"left":131,"right":1351,"top":63,"tangent":310,"bottom":1930}' AS JSON)),
  `medium`            = COALESCE(`medium`,  '乙烯基三氯硅烷'),
  `density`           = COALESCE(`density`, 1.27)
WHERE `source_sheet` = '容器封头参数表2025.06' AND `source_row` = 89;

-- 再沸器：**不在源台账里**（来自厂家图纸），手工补一行。
--   source_sheet 用独立的名字，避免与那 91 行混在一起；source_row 给 1。
--   对账：head_volume = (2/3)π·1.1²·0.55 + π·1.1²·0.04 = 1.5459（与甲醇计量罐同直径同封头）
--   ⚠️ 壳体内置 U 型管束（87 根 φ25、罐内 2 程 × 3.6m）**尚未落库** ——
--      当前由前端的临时覆盖表顶着，见《前后端改动统筹》变更-025 的技术债。
INSERT INTO `equipment_ledger`
  (`equipment_name`, `spec`, `head_volume`, `volume`, `remark`, `source_sheet`, `source_row`,
   `inner_diameter`, `shell_length`, `straight_flange`, `top_head_depth`, `bottom_head_depth`,
   `image_file`, `image_bounds`)
VALUES
  ('再沸器', 'DN2200x4000（卧式·内置U型管束）', 1.5459, 21.2,
   '厂家图纸。壳体内置 U 型管束 87 根 φ25（罐内 2 程 × 3.6m），管束参数待落库。volume 是铭牌全容积，与按几何算出的 18.30 m³ 不一致，页面上只作对照、不参与计算。spec 里的「内置U型管束」是**必须的**——container_type=4 由它派生，见下方回填语句',
   '手工补充·再沸器图纸', 1,
   2200, 4000, 40, 550, 550,
   'vessel-reboiler.png',
   '{"width":1616,"height":820,"left":53,"right":1377,"top":92,"bottom":659}')
ON DUPLICATE KEY UPDATE
  -- 全部走 COALESCE：重复执行只填「还是空」的列，**不覆盖**维护页改过的值。
  -- 这几列在本表里都非空（或新增行已由上面的 VALUES 填好），所以对已存在的行实际是空操作 ——
  -- 上面那份 VALUES 只在**首次插入**时生效。这是有意的：人工判定优先于本文件的抄录。
  `equipment_name`    = COALESCE(`equipment_name`,    VALUES(`equipment_name`)),
  `spec`              = COALESCE(`spec`,              VALUES(`spec`)),
  `head_volume`       = COALESCE(`head_volume`,       VALUES(`head_volume`)),
  `volume`            = COALESCE(`volume`,            VALUES(`volume`)),
  `remark`            = COALESCE(`remark`,            VALUES(`remark`)),
  `inner_diameter`    = COALESCE(`inner_diameter`,    VALUES(`inner_diameter`)),
  `shell_length`      = VALUES(`shell_length`),
  `straight_flange`   = VALUES(`straight_flange`),
  `top_head_depth`    = VALUES(`top_head_depth`),
  `bottom_head_depth` = VALUES(`bottom_head_depth`),
  `image_file`        = VALUES(`image_file`),
  `image_bounds`      = VALUES(`image_bounds`);


-- ---------------------------------------------------------------------------
-- 容器类型回填
--
-- 规则：`spec` 含「管束」→ 4；含「卧式」→ 1；含「平底」→ 2；都不含 → 3。
--
-- **4 = 再沸器**（卧式带内置管束那一种，体积计算页要扣掉管内排液体积）。
-- 它也是个**派生列**：靠 spec 里的「管束」二字识别。
--
-- ⚠️ **只填 `container_type IS NULL` 的行**（2026-10-06 起）。原因：有了「设备数据维护」页之后
-- 这一列是可人工改的 —— 原先「每次执行都按 spec 重算全表」的写法会把维护页刚改的值刷回去。
-- 与该表既有的「不覆盖业务侧补的 density / medium / enabled」是同一条原则。
-- 代价：**光在界面上改这一列、不改 spec，下次重跑本文件不会同步**（本来也不会 —— 只在 NULL 时才填），
-- 这正是想要的：人工的判定优先于文本派生。
--
-- 为什么在导入之后单独跑一条 UPDATE，而不是写进上面的 VALUES：
--   它是**纯派生列**（只由 spec 决定），用一条语句算全表，比在 91 行 VALUES 里
--   手抄 91 个数字可靠 —— 手抄的那份迟早会和 spec 不一致，且将来追加的
--   「另 6 页设备台账」不必再逐行判定。
--
-- 几个判定细节：
--   · **顺序固定「先管束、再卧式、再平底」**。顺序有实质影响，不是随手排的：
--     再沸器既是「卧式」又「带管束」，管束那条必须排在前面才判得到 4。
--     另外两种写法在源台账里互斥（都写在规格的括号里），顺序只是给将来留一个确定答案。
--   · spec 为 NULL / 空串 → 落 3（「都不包含」的自然结果），不猜成 1 或 2。
--   · LIKE 的 % 只加在词两侧，不锚定括号 —— 源台账里这两种写法既有全角括号
--     「（卧式）」也有别的形态，锚定括号会漏判。
--
-- ⚠️ **位置有讲究：必须排在上面「容器几何参数回填」之后。** 那一节会**插入新行**
--    （再沸器不在源台账里，是手工补的），而本列是「每次执行都按 spec 重算全表」的
--    输出列 —— 排在它前面就会漏算刚插进来的行。实测踩过：再沸器的 spec 明明写着
--    「（卧式）」，却因为本语句先跑而落成了 3（其他），体积计算页会把它画成立式。
--
-- 实测分布（91 行 + 手工补充行）：卧式 8 / 平底立式 10 / 立式 73 / 再沸器 1。
-- ---------------------------------------------------------------------------
UPDATE `equipment_ledger`
SET `container_type` = CASE
      WHEN `spec` LIKE '%管束%' THEN 4
      WHEN `spec` LIKE '%卧式%' THEN 1
      WHEN `spec` LIKE '%平底%' THEN 2
      ELSE 3
    END
WHERE `container_type` IS NULL;


-- =============================================================================
-- 第 3 部分：老库升级脚本（默认注释，仅在既有旧库上升级时按需执行）
--
-- 说明：MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
--       "Duplicate column name / Duplicate key name" 错误，属正常现象，
--       按需逐条执行即可。全新库执行完第 1、2 部分后不需要本节。
-- =============================================================================

-- -- 1) 生产入库单表（含单据图片列）
-- CREATE TABLE IF NOT EXISTS `production_inbound` ( ... );   -- 见第 1 部分

-- -- 2) 领料汇总表（含单据图片列）
-- CREATE TABLE IF NOT EXISTS `material_pick_summary` ( ... ); -- 见第 1 部分

-- -- 3) 生产入库单：新增单据号列，并建立 (单据号+物料编码) 唯一键
-- --    ⚠️ 2026-10-12 起唯一键已加日期维度（变更-047）：跑完本步的库请接着跑第 17) 步
-- ALTER TABLE `production_inbound`
--   ADD COLUMN `document_no` varchar(32) DEFAULT NULL COMMENT '单据号（与物料编码组成唯一键）' AFTER `seq_no`;
-- ALTER TABLE `production_inbound`
--   ADD UNIQUE KEY `uk_document_no_material` (`document_no`,`material_code`);

-- -- 4) 领料汇总：新增单据号列 + 唯一键（同第 3) 步，需接着跑第 17) 步）
-- ALTER TABLE `material_pick_summary`
--   ADD COLUMN `document_no` varchar(32) DEFAULT NULL COMMENT '单据号（与物料编码组成唯一键）' AFTER `seq_no`;
-- ALTER TABLE `material_pick_summary`
--   ADD UNIQUE KEY `uk_document_no_material` (`document_no`,`material_code`);

-- -- 5) 角色权限表：新增唯一键（防止同一角色重复授予同一权限）
-- ALTER TABLE `sys_role_permission`
--   ADD UNIQUE KEY `uk_role_permission` (`role_id`,`permission_key`);

-- -- 6) 权限标识变更：production_inbound:view 已废弃，改用 inbound:view
-- DELETE FROM `sys_role_permission` WHERE `permission_key` = 'production_inbound:view';

-- -- 7) 新增货物移动导入权限
-- INSERT INTO `sys_role_permission` (`role_id`, `permission_key`)
-- SELECT id, 'goods_move:import' FROM `sys_role`
-- WHERE NOT EXISTS (
--   SELECT 1 FROM `sys_role_permission` p
--   WHERE p.`role_id` = `sys_role`.`id` AND p.`permission_key` = 'goods_move:import'
-- );

-- -- 8) 新增游客角色与测试账号
-- INSERT INTO `sys_role` (`role_name`, `role_key`, `description`)
--   VALUES ('游客','guest','只读，不能执行任何写操作')
--   ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- -- 9) 新增游客账号 guest/admin（只读，不能导入/上传文件）
-- --    权限由 guest 角色决定，无需额外授予：该角色只有 *:view，没有 import 与 image:upload
-- INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role_id`, `status`)
-- SELECT 'guest',
--        '$2a$12$qwC0z3.3HxTs.4Rkuc/iFOG4rIpmY2xGpa3ZomM3VolHj9rB1ZbrW',
--        '游客',
--        r.`id`, 1
-- FROM `sys_role` r WHERE r.`role_key` = 'guest'
-- ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`), `real_name` = VALUES(`real_name`);

-- -- 10) 月底车间各储罐液位记录表（变更-004）
-- CREATE TABLE IF NOT EXISTS `tank_level_record` ( ... );   -- 见第 1 部分
--
-- -- 示例：补录一条月底液位记录（图据填 file-service 的文件名，没拍照就留空）
-- INSERT INTO `tank_level_record`
--   (`record_date`, `location`, `category`, `material_code`, `material_name`,
--    `tank_name`, `tank_code`, `level_value`, `theoretical_weight`, `file_name`)
-- VALUES
--   ('2026-08-31', '一车间', '产品', '114001897', 'HND-V150', 'V150储罐A', 'V150-A', 1250.0000, 1500.0000, NULL);
--
-- -- 注意：唯一键是 (record_date, tank_code)。同一时点同一容器重复插入会撞唯一键，
-- --       补录/更正时用 ON DUPLICATE KEY UPDATE 覆盖，别直接 INSERT。

-- -- 11) 设备台账（静设备）表（变更-005）
-- CREATE TABLE IF NOT EXISTS `equipment_ledger` ( ... );    -- 见第 1 部分
-- --    种子数据（容器封头参数表 91 行）在第 2 部分，用完整的第 2 部分做增量导入即可：
-- --      mysql -h127.0.0.1 -uroot -p factory_db < docs/schema.sql
-- --    （第 1、2 部分都是幂等写法，重复执行不会翻倍，也不会覆盖业务侧补的密度/停用标记）
-- --
-- -- 之后补介质密度与每 mm 液位对应质量（示例，按实际物料密度填写）：
-- -- UPDATE `equipment_ledger` SET `medium` = '甲醇', `density` = 0.792,
-- --        `mass_per_mm` = `volume_per_mm` * 0.792 * 1000
-- --   WHERE `equipment_name` IN ('甲醇储罐A', '甲醇储罐B');

-- -- 12) 设备台账：新增容器类型列（变更-006；枚举在 变更-025 扩了第 4 个值）
-- --     全新库跑完第 1 部分就有了，本节只给「变更-005 之后已建表」的旧库升级用。
-- --     DEFAULT 3 让既有行先落到「其他」，紧接着的回填语句再按 spec 逐行纠正。
-- --     ⚠️ 变更-025 起多了 4 = 卧式带内置管束（再沸器那一种），判定顺序「先管束再卧式」
-- --        有实质影响 —— 再沸器两个词都占，顺序反了就落到 1 而不是 4。
-- --        已经跑过本步的库再跑一次即可（UPDATE 是幂等的，会把已有行刷成新枚举）。
-- ALTER TABLE `equipment_ledger`
--   ADD COLUMN `container_type` tinyint NOT NULL DEFAULT 3
--     COMMENT '容器类型 1卧式 2平底 3其他 4卧式带内置管束（由 spec 判定：含「管束」→4，含「卧式」→1，含「平底」→2，都不含→3）'
--   AFTER `spec`;
-- -- 回填（与第 2 部分同一段逻辑；重复执行无副作用）
-- UPDATE `equipment_ledger`
-- SET `container_type` = CASE
--       WHEN `spec` LIKE '%管束%' THEN 4
--       WHEN `spec` LIKE '%卧式%' THEN 1
--       WHEN `spec` LIKE '%平底%' THEN 2
--       ELSE 3
--     END;

-- -- 13) 储罐液位记录：同步「抄录时间」的列注释（变更-007 的收尾）
-- --     变更-007 把页面上「每月月底下午 2 点」改成 3 点（线下台账口径本就是 3 点），
-- --     但漏了库里这条列注释。注释不影响读写，只影响看表结构的人 ——
-- --     不补的话，下次对账又会有人照着「2 点」去核。
-- ALTER TABLE `tank_level_record`
--   MODIFY COLUMN `record_date` date NOT NULL COMMENT '记录日期（每月月底下午3点抄录）';

-- -- 14) 中文注释被写坏时怎么修（2026-10-01 实际踩过）
-- --     现象：表/列注释显示成「鐗╂枡搴撳瓨姹囨?」，但**数据是好的**（中文数据正常显示）。
-- --     原因：建表那一次 mysql 客户端没带 --default-character-set=utf8mb4，
-- --           按本机默认编码（中文 Windows 下是 GBK）连库，注释在写进数据字典那一刻就被解错；
-- --           数据没事是因为它走 JDBC 写入，charset 一直是对的。
-- --     修法：**不用重建表、不用重导数据**，带正确编码重放一遍注释即可（以 material_stock 为例）：
-- ALTER TABLE `material_stock` COMMENT = '物料库存汇总（SAP 库存导出）';
-- ALTER TABLE `material_stock`
--   MODIFY COLUMN `plant_code` varchar(8) NOT NULL DEFAULT '' COMMENT '工厂，如 1503';
-- -- ……其余列同理，列定义直接抄本文件第 1 部分的建表语句（MODIFY COLUMN 要求写全类型/可空/默认值）。

-- -- 15) 储罐液位图据改为子表（变更-011）
-- --     变更-008 时图据是 tank_level_record.file_name 单列（一条记录一张）；
-- --     改为多张后由 tank_level_image 承载。三步，**顺序不能反**：
-- --
-- --     ① 建子表（语句见第 1 部分，全新库跑完第 1 部分就有了）
-- CREATE TABLE IF NOT EXISTS `tank_level_image` ( ... );
-- --
-- --     ② 把原有的单张图搬进子表（本来就没图的行不会被选出来，重复执行只会
-- --        再插一遍相同数据 —— 所以**这步只跑一次**，跑完确认行数再进第③步）
-- INSERT INTO `tank_level_image` (`record_id`, `file_name`)
-- SELECT `id`, `file_name` FROM `tank_level_record`
-- WHERE `file_name` IS NOT NULL AND `file_name` <> '';
-- --
-- --     ③ 核对第②步的插入行数 = 原表里有图据的行数，确认后再删列
-- --        （删列前先 `SELECT id, file_name FROM tank_level_record WHERE file_name <> ''`
-- --          留个底，这一步不可逆）
-- ALTER TABLE `tank_level_record` DROP COLUMN `file_name`;

-- -- 16) 储罐液位记录：记录日期不再唯一（变更-013，2026-10-02）
-- --     现象：手机上（电脑端一样）新增第二条记录时报
-- --           「该记录日期已有一条记录（容器编号为空时按日期唯一），请改记录日期。」
-- --     原因：容器编号那一栏已从界面撤掉，新增记录的 tank_code 落到列默认值 ''，
-- --           唯一键 uk_date_tank(record_date, tank_code) 于是退化成「一天一条」。
-- --     改法：tank_code 改为可空，空编号存 NULL —— MySQL 唯一索引不约束 NULL
-- --           （多个 NULL 互不相等），同一天可记任意多条；有编号的历史行仍受保护。
-- --     ⚠️ 两句都要跑：第一句放行 NULL，第二句把历史行的 '' 换成 NULL，
-- --        否则旧行占着 '' 会和后面新插的 NULL 各算一条，看着像「没生效」。
-- ALTER TABLE `tank_level_record`
--   MODIFY COLUMN `tank_code` varchar(64) DEFAULT NULL
--     COMMENT '容器编号（设备位号）；为空时同一天可有多条，填了则与记录日期联合唯一';
-- UPDATE `tank_level_record` SET `tank_code` = NULL WHERE `tank_code` = '';

-- -- 15) 设备台账：新增容器几何参数与底图列（变更-025）
-- --     全新库跑完第 1 部分就有了，本节只给「变更-006 之后已建表」的旧库升级用。
-- --     7 列都允许 NULL —— 填齐（且 enabled=1）的行才会出现在「压力容器体积计算」页的下拉里。
-- --     列注释里那几条口径（shell_length 不含直边、image_bounds 按图片宽度归一化、
-- --     朝向复用 container_type）不要省，否则下次填数的人一定会填错。
-- ALTER TABLE `equipment_ledger`
--   ADD COLUMN `inner_diameter`    decimal(18,2) DEFAULT NULL COMMENT '容器内径(mm)' AFTER `container_type`,
--   ADD COLUMN `shell_length`      decimal(18,2) DEFAULT NULL COMMENT '筒体直段长(mm)，**不含封头直边**',
--   ADD COLUMN `straight_flange`   decimal(18,2) DEFAULT NULL COMMENT '封头直边(mm)，无直边填 0',
--   ADD COLUMN `top_head_depth`    decimal(18,2) DEFAULT NULL COMMENT '上/左封头曲面深度(mm)，标准椭圆封头 = 内径/4',
--   ADD COLUMN `bottom_head_depth` decimal(18,2) DEFAULT NULL COMMENT '下/右封头曲面深度(mm)；NULL 或 0 = 平底',
--   ADD COLUMN `image_file`        varchar(128)  DEFAULT NULL COMMENT '容器底图文件名；深色版按 -dark 后缀约定取',
--   ADD COLUMN `image_bounds`      json          DEFAULT NULL COMMENT '底图坐标，按图片宽度归一化（width 恒为 1、height 是长宽比）';
--
-- -- 升级后接着跑**第 2 部分的「容器几何参数回填」那一节**（3 条 UPDATE + 再沸器的 INSERT），
-- -- 它按 (source_sheet, source_row) 定位、可重复执行。

-- -- 17) 单据行唯一键加日期维度：uk_document_no_material → uk_document_no_material_date
-- --     （变更-047，2026-10-12）
-- --
-- --     需求（D4）：同一单据号 + 同一物料编码 + 不同日期必须能各存一行
-- --     —— 图片识别改为行级日期后，日期成了行身份的一部分，唯一键必须跟上。
-- --
-- --     安全性：新键是旧键的**超集**（严格更松），既有数据天然满足；
-- --     库先于后端迁移也不会破坏旧服务（旧代码不会写同 (单据号,物料编码) 的第二行）。
-- --
-- --     ⚠️ 日期列可为 NULL，而 MySQL 唯一索引**不约束 NULL** —— 应用层三键把 NULL
-- --        归一成空串参与比对（DocMaterialDateKey），document 模式另强制行日期非空；
-- --        本步骤**不**把日期改成 NOT NULL（历史行要回填，本轮不动）。
-- --
-- --     ⚠️ DDL 隐式提交、不可回滚：执行前先做数据备份（_dbfix/DbBackup 导出两表快照），
-- --        那是唯一的退路。回滚语句附在本步末尾。
-- --
-- --     改前核对（只读）：行数基线（记进变更-047 文档的验证记录）。
-- --     预期：第二个查询两表都应返回 **0 行**（旧键更严，本来就不可能有重复）。
-- SELECT 'production_inbound' AS tbl, COUNT(*) AS rows_cnt FROM `production_inbound`
-- UNION ALL
-- SELECT 'material_pick_summary', COUNT(*) FROM `material_pick_summary`;
-- SELECT `document_no`, `material_code`, COUNT(*) FROM `production_inbound`
--  GROUP BY `document_no`, `material_code` HAVING COUNT(*) > 1;
-- SELECT `document_no`, `material_code`, COUNT(*) FROM `material_pick_summary`
--  GROUP BY `document_no`, `material_code` HAVING COUNT(*) > 1;
-- --
-- ALTER TABLE `production_inbound`
--   DROP KEY `uk_document_no_material`,
--   ADD UNIQUE KEY `uk_document_no_material_date` (`document_no`,`material_code`,`inbound_date`);
-- ALTER TABLE `material_pick_summary`
--   DROP KEY `uk_document_no_material`,
--   ADD UNIQUE KEY `uk_document_no_material_date` (`document_no`,`material_code`,`pick_date`);
-- --
-- --     列注释同步（注释不影响读写，但不补的话下次看表结构的人会照着旧口径核）：
-- ALTER TABLE `production_inbound`
--   MODIFY COLUMN `document_no` varchar(32) DEFAULT NULL COMMENT '单据号（与物料编码、日期组成唯一键）',
--   MODIFY COLUMN `inbound_date` date DEFAULT NULL COMMENT '日期（行身份的一部分，唯一键成员）';
-- ALTER TABLE `material_pick_summary`
--   MODIFY COLUMN `document_no` varchar(32) DEFAULT NULL COMMENT '单据号（与物料编码、日期组成唯一键）',
--   MODIFY COLUMN `pick_date` date DEFAULT NULL COMMENT '日期（行身份的一部分，唯一键成员）';
-- --
-- --     迁移后复核：SHOW INDEX 应出现 uk_document_no_material_date 且旧键消失；行数与基线一致。
-- --
-- --     回滚（⚠️ **有损**）：迁移后若已产生「同单据同物料不同日期」的行，旧键装不下，
-- --     回滚前必须先对账并人工处置（保哪一行由业务定），否则 ADD UNIQUE KEY 直接失败：
-- --     SELECT `document_no`, `material_code`, COUNT(*) FROM `material_pick_summary`
-- --      GROUP BY `document_no`, `material_code` HAVING COUNT(*) > 1;   -- 入库表同理
-- ALTER TABLE `production_inbound`
--   DROP KEY `uk_document_no_material_date`,
--   ADD UNIQUE KEY `uk_document_no_material` (`document_no`,`material_code`);
-- ALTER TABLE `material_pick_summary`
--   DROP KEY `uk_document_no_material_date`,
--   ADD UNIQUE KEY `uk_document_no_material` (`document_no`,`material_code`);


-- -- 18) 采购进度跟踪（变更-091，2026-10-16）
-- --
-- --     ① 建表：执行第 1 部分末尾那段 `CREATE TABLE IF NOT EXISTS purchase_tracking`（幂等）。
-- --
-- --     ② 写权限：**新权限位必须同时授给 admin**，否则会出现
-- --        「接口有 @SaCheckPermission 闸门、但没有任何角色拿得到钥匙」的哑火 ——
-- --        表现是保存一律无权限，而单看代码完全看不出问题（变更-085 那类坑的权限版）。
-- --        查询接口（/api/purchase/list）与其它列表一样**免登录**，所以不需要 view 权限位。
-- INSERT INTO `sys_role_permission` (`role_id`, `permission_key`)
-- SELECT r.`id`, 'purchase:edit'
-- FROM `sys_role` r
-- WHERE r.`role_key` = 'admin'
--   AND NOT EXISTS (
--     SELECT 1 FROM `sys_role_permission` p
--     WHERE p.`role_id` = r.`id` AND p.`permission_key` = 'purchase:edit'
--   );
-- --
-- --     复核：SELECT r.role_key, p.permission_key FROM sys_role_permission p
-- --             JOIN sys_role r ON r.id = p.role_id
-- --            WHERE p.permission_key = 'purchase:edit';      -- 期望恰好 1 行：admin
-- --
-- --     生产已于 2026-10-16 执行（见变更-091 的部署记录）。


-- -- 19) 采购进度跟踪：`delivery_note` 改成「送货单图片路径」（变更-094，2026-10-16）
-- --
-- --     使用方说明「送货单是一张图片」，所以这一列改成存前端拼好的 `/files/xxx`
-- --     （ADR-0004 固定拼接）。**只是同步列注释、不动数据**：varchar(64) 装得下
-- --     `/files/` + 36 字符的文件名，历史上存过的文本单号保持原样、按文本显示。
-- --     （与第 13) 步「同步列注释」同一种小改动。）
-- ALTER TABLE `purchase_tracking`
--   MODIFY COLUMN `delivery_note` varchar(64) NOT NULL DEFAULT ''
--   COMMENT '送货单**图片路径**（/files/xxx，ADR-0004 固定拼接；变更-094 起是图片，不再填单号）；空串 = 还没到货；历史上存过文本单号，老数据按文本原样显示';
-- --
-- --     复核：SHOW FULL COLUMNS FROM purchase_tracking LIKE 'delivery_note';


-- -- 20) 采购进度跟踪：补「类别/数量/单位/实际到货数量/实际到货日期」，并明确 OA 单号语义（变更-096，2026-10-17）
-- --
-- --     为什么补：使用方要「实时掌握进度、别误工期」，但原表既没有数量也没有实际到货日期，
-- --     "部分到货""平均拖了几天"这类判断根本算不出来；类别则是之后查数据的第一个切面。
-- --     五列**全部可空**：历史数据（当时 1 条）保持原样，页面显示「—」，不做任何回填。
-- --
-- --     ① 加列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，先核对再执行；重复执行会报 Duplicate column）：
-- ALTER TABLE `purchase_tracking`
--   ADD COLUMN `category`            varchar(16)   DEFAULT NULL COMMENT '类别（备件/五金/原料/设备/其他；变更-096 起，变更-097c 加五金；NULL = 未分类，页面显示「—」）' AFTER `spec`,
--   ADD COLUMN `quantity`            decimal(12,3) DEFAULT NULL COMMENT '需求数量（变更-096 起；NULL = 历史数据未记，页面显示「—」）' AFTER `category`,
--   ADD COLUMN `unit`                varchar(16)   DEFAULT NULL COMMENT '单位（个/台/套/件/米/公斤…；变更-096 起；NULL = 未填）' AFTER `quantity`,
--   ADD COLUMN `received_quantity`   decimal(12,3) DEFAULT NULL COMMENT '实际到货数量（变更-096 起；NULL = 未记；< quantity 即部分到货）' AFTER `delivery_note`,
--   ADD COLUMN `actual_arrival_date` date          DEFAULT NULL COMMENT '实际到货日期（变更-096 起；NULL = 还没到货；算出「提前/延后几天」靠它）' AFTER `received_quantity`;
-- --
-- --     改前核对（只读，记进变更-096 的验证记录）：
-- SELECT COUNT(*) AS rows_before FROM `purchase_tracking`;
-- SHOW FULL COLUMNS FROM `purchase_tracking`;
-- --
-- --     ② 语义同步：`purchase_order_no` 存的是 **OA 提单号**，不是采购订单号。
-- --        **不改列名**（改列名要连 `idx_purchase_order_no` 一起重建，纯增风险），只改注释：
-- ALTER TABLE `purchase_tracking`
--   MODIFY COLUMN `purchase_order_no` varchar(64) NOT NULL DEFAULT ''
--   COMMENT 'OA 单号（变更-096 起语义明确：这是**OA 提单号**，不是采购下给供应商的订单号）；还没提单时留空';
-- --
-- --     ③ 权限收紧（**这次改的是代码，不是表**，但一并记在这里免得漏）：
-- --        第 18) 步当时写的是「查询接口 /api/purchase/list 免登录，所以不需要 view 权限位」。
-- --        使用方确认「这页只有管理员能看」，所以 `SaTokenConfigure` 的 `ADMIN_READ_PATTERNS`
-- --        增加 `/api/purchase/**`，**不新增权限位**（避免变更-091 记的那个坑：新权限位要同时
-- --        播种 admin + 追加 schema 撤权清单，漏一个就出现"管理员反而没权限"）。
-- --        复核：不登录访问 GET /api/purchase/list?pageNum=1&pageSize=10 → 期望 401，不是 200。
-- --
-- --     改后复核（只读）：
-- SELECT COUNT(*) AS rows_after FROM `purchase_tracking`;   -- 期望与 rows_before 一致
-- SHOW FULL COLUMNS FROM `purchase_tracking`
--  WHERE Field IN ('category','quantity','unit','received_quantity','actual_arrival_date','purchase_order_no');
-- --     期望：5 列存在且 Default 为 NULL；purchase_order_no 的注释以「OA 单号」开头。
-- --     另：SELECT * FROM purchase_tracking LIMIT 1;  期望 5 个新列全为 NULL（历史行未被回填）。
-- --
-- --     回滚（无损，因为全是可空列、没有代码依赖旧值）：
-- ALTER TABLE `purchase_tracking`
--   DROP COLUMN `category`,
--   DROP COLUMN `quantity`,
--   DROP COLUMN `unit`,
--   DROP COLUMN `received_quantity`,
--   DROP COLUMN `actual_arrival_date`;
-- --     ⚠️ 回滚前先备份这 5 列的数据（DECIMAL 列丢的是数量，重新录入代价高）：
-- --     CREATE TABLE purchase_tracking_bak_096 AS SELECT id, category, quantity, unit,
-- --            received_quantity, actual_arrival_date FROM purchase_tracking;
-- --
-- --     生产执行时间：**2026-10-17 17:10 已执行**（见变更-096 的部署记录）。
-- --     执行前备份：/home/eetig/backup/purchase_tracking-20261017.sql（mysqldump，md5 f5e471a0a5d52040f0e5df3c1e1dfc93）
-- --                  /home/eetig/backup/purchase_tracking-20261017-create.sql（SHOW CREATE TABLE 快照）
-- --     执行后复核：17 列（原 12 + 新 5）；行数仍为 1；那一行的 5 个新列全为 NULL；
-- --                 purchase_order_no 注释已变为「OA 单号…」。
-- --     回滚锚点：镜像 hnd_factory:pre-096（=旧镜像 50c6b1e9），jar 备份 hnd_factory.jar.bak-20261017-171800。


-- -- 21) 采购需求详情：加「用途」（变更-098，2026-10-18）
-- --
-- --     使用方要求「规格型号之后加一栏，叫用途」——记这料拿去干什么用。
-- --     可空：历史行保持 NULL，页面显示「—」，不做回填。
-- --
-- --     ① 加列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column）：
-- ALTER TABLE `purchase_tracking`
--   ADD COLUMN `purpose` varchar(255) DEFAULT NULL
--   COMMENT '用途（这料拿去干什么用；变更-098 起；NULL = 未填）' AFTER `spec`;
-- --
-- --     改前核对（只读）：
-- SELECT COUNT(*) AS rows_before FROM `purchase_tracking`;
-- SHOW FULL COLUMNS FROM `purchase_tracking`;
-- --
-- --     改后复核（只读）：
-- SELECT COUNT(*) AS rows_after FROM `purchase_tracking`;   -- 期望与 rows_before 一致
-- SHOW FULL COLUMNS FROM `purchase_tracking` LIKE 'purpose';
-- --     期望：1 列、Default 为 NULL、注释以「用途」开头；另 SELECT * LIMIT 1 该列为 NULL。
-- --
-- --     回滚（无损）：
-- ALTER TABLE `purchase_tracking` DROP COLUMN `purpose`;
-- --     ⚠️ 回滚前备份该列数据：
-- --     CREATE TABLE purchase_tracking_bak_098 AS SELECT id, purpose FROM purchase_tracking;
-- --
-- --     生产执行时间：**2026-10-18 07:2x 已执行**（见变更-098 的部署记录）。

-- -- 22) 采购需求详情：加「OA 表单链接」（变更-100，2026-10-18）
-- --
-- --     使用方要求「点单号直接跳到 OA 里那张单」。**试过用单号现拼链接，走不通**：
-- --     OA 的「按单号筛列表」地址 /ssc/fee/#/j_path=%2FlistAll&…&criteria=docNumber:<单号>
-- --     在未登录时会被 302 到 login.jsp，**登录后落回门户首页**（2026-10-18 实测），
-- --     不会回到那个 hash；表单页 fsscFeeMain.do?method=view 又只认 fdId。
-- --     ⇒ 只能让使用方把表单地址复制进来存着（零解析、用户粘什么就打开什么）。
-- --
-- --     ① 加列（与 oaNo 同口径：NOT NULL DEFAULT ''，空串=未粘；MySQL 8 不支持 IF NOT EXISTS）：
-- ALTER TABLE `purchase_tracking`
--   ADD COLUMN `oa_url` varchar(512) NOT NULL DEFAULT ''
--   COMMENT 'OA 表单链接（变更-100 起；整条 URL，点单号直达；空串 = 还没粘过）' AFTER `purchase_order_no`;
-- --
-- --     改前核对（只读）：
-- SELECT COUNT(*) AS rows_before FROM `purchase_tracking`;
-- SHOW FULL COLUMNS FROM `purchase_tracking`;
-- --
-- --     改后复核（只读）：
-- SELECT COUNT(*) AS rows_after FROM `purchase_tracking`;          -- 期望与 rows_before 一致
-- SELECT id, CONCAT('[', oa_url, ']') FROM `purchase_tracking`;    -- 期望全是「[]」（空串）
-- --
-- --     回滚（无损）：
-- ALTER TABLE `purchase_tracking` DROP COLUMN `oa_url`;

-- ---------------------------------------------------------------------------
-- 23) 资料归档：两张表 + archive:edit 权限位（变更-126，2026-10-20）
--
--     使用方原话：「我们是一家化工厂，目前的痛点是资料没人归纳整理，也就是没有资料员…
--     想在 hnd-factory 添加一个资料归档的功能」。方案见 docs/资料归档-开工方案.md（已定 6 条）。
--
--     为什么是两张表：
--       · archive_category —— 分类。**资料员的第一件事就是分类**；两级、类目存库由使用方维护
--         （各厂类目差异极大，写死在代码里必错）。
--       · archive_document —— 台账。必填只有「名称/分类/文件（或纸质）」，其余选填
--         （必填一多就没人录，变更-085 那次 92 行空值就是教训）。
--
--     ⚠️ 本步**完全幂等**：建表用 CREATE TABLE IF NOT EXISTS；权限是 INSERT ... WHERE NOT EXISTS
--        + 撤权 DELETE，都可重复执行 —— 与第 18) 步的写法一致。
--
--     执行顺序：本步只动库，不动代码（表先建好，代码后上，回滚更容易）。
--
--     改前核对（只读）：
--       SELECT COUNT(*) FROM information_schema.TABLES
--        WHERE TABLE_SCHEMA='factory_db' AND TABLE_NAME IN ('archive_category','archive_document');  -- 期望 0
--
--     （表结构见文件第 1 部分末尾的两条 CREATE TABLE；这里只记执行与复核。）
--
--     改后复核（只读）：
--       SHOW CREATE TABLE `archive_category`;    -- 期望：uk_parent_name (parent_id, name)
--       SHOW CREATE TABLE `archive_document`;    -- 期望：idx_category / idx_expire / idx_name 三个索引
--       SELECT `permission_key` FROM `sys_role_permission` p
--         JOIN `sys_role` r ON r.id = p.role_id
--        WHERE r.role_key='admin' AND p.permission_key='archive:edit';   -- 期望 1 行
--       SELECT COUNT(*) FROM `sys_role_permission` p
--         JOIN `sys_role` r ON r.id = p.role_id
--        WHERE r.role_key <> 'admin' AND p.permission_key='archive:edit'; -- 期望 0 行（写权限只授 admin）
--
--     回滚（无损：新表、没有代码依赖）：
--       DROP TABLE IF EXISTS `archive_document`;
--       DROP TABLE IF EXISTS `archive_category`;
--       DELETE p FROM `sys_role_permission` p
--         JOIN `sys_role` r ON r.id = p.role_id
--        WHERE p.permission_key = 'archive:edit';
--     ⚠️ 一旦开始用（有真实资料上传），回滚前必须先把 MinIO 里的 archive/ 对象和两张表一起备份。
--
--     生产执行：**尚未执行**（等后端与前端一起上；执行后在此补时间与备份 md5）。

-- 资料归档的初始 7 大类（变更-126）。
--
-- 为什么要有种子：分类树空着的话，使用方第一次进来看到一片空白，不知道该从哪开始分类。
-- 这 7 类是化工厂最常见的大类，**子类由使用方自己加**（各厂差异全在子类上）。
--
-- ⚠️ 幂等靠 WHERE NOT EXISTS：`uk_parent_name (parent_id, name)` 对 parent_id IS NULL
--    是**不生效**的（MySQL 唯一索引不约束 NULL），光靠唯一键会种出重复大类。
--    应用层也补了同层查重（ArchiveServiceImpl#saveCategory），两处都在。
INSERT INTO `archive_category` (`parent_id`, `name`, `sort_no`)
SELECT NULL, x.`name`, x.`sort_no`
FROM (
    SELECT '证照与资质' AS `name`, 10 AS `sort_no` UNION ALL
    SELECT '安全与环保',          20 UNION ALL
    SELECT '设备与图纸',          30 UNION ALL
    SELECT '质量与检验',          40 UNION ALL
    SELECT '台账与记录',          50 UNION ALL
    SELECT '合同与采购',          60 UNION ALL
    SELECT '人员与培训',          70
) x
WHERE NOT EXISTS (
    SELECT 1 FROM `archive_category` c
     WHERE c.`parent_id` IS NULL AND c.`name` = x.`name`
);
-- ---------------------------------------------------------------------------
-- 24) 液位记录：去掉唯一键 uk_date_tank + 空容器编号归一为 NULL（变更-127，2026-10-20）
--
--     起因（使用方真机截图）：同一天录第二台容器必报
--       「该记录日期已存在一条容器编号为空的记录，请改记录日期。」
--     而界面从变更-014 起就**没有「容器编号」这一栏**，用户无从下手。
--
--     根因（两层，缺一不可）：
--       ① 生产库 `tank_code` 列的 DEFAULT 仍是 ''（变更-013 只改了"可空"，没改默认值），
--          而实体没有 insert 策略、MyBatis-Plus 默认 NOT_NULL **跳过 null 字段** →
--          INSERT 里根本没有 tank_code → 落库吃默认值 '' → 空串受唯一键约束 → 一天一条。
--          （2026-10-20 生产数据复核：32 行里 30 行是 NULL、2 行是 ''，正是两条写入路径的指纹。）
--       ② 唯一键 (record_date, tank_code) 本身按使用方口径就该去掉：
--          「同一天可以录多条，重复只提示一次」——不能让库在没有任何提示的情况下拒绝。
--
--     代码侧配套（已随本变更合入，缺了它本步会失效）：
--       · `TankLevelRecord.tankCode` 加了 `@TableField(insertStrategy = ALWAYS)` → 显式写 NULL；
--       · `TankLevelRecordServiceImpl` 把空白编号归一成 null，并按「日期 + 容器名称」
--         做保存前提示（409 + confirmDuplicate 可继续）。
--     ⚠️ 也就是说：**代码先上、本步后跑**也不会再出现"一天只能一条"（NULL 不受约束）；
--        本步做的是清语义 + 清历史脏数据。
--
--     改前核对（只读）：
--       SHOW CREATE TABLE `tank_level_record`;                       -- 期望仍能看到 uk_date_tank
--       SELECT COUNT(*) AS empty_code FROM `tank_level_record` WHERE `tank_code` = '';   -- 生产期望 2
--       SELECT COUNT(*) AS null_code  FROM `tank_level_record` WHERE `tank_code` IS NULL; -- 生产期望 30
--
--     执行（三句，顺序不可换；第 2 句必须在第 1 句之后 —— 否则去掉约束前就有重复空串行会报错）：
--       ALTER TABLE `tank_level_record` DROP INDEX `uk_date_tank`;
--       UPDATE `tank_level_record` SET `tank_code` = NULL WHERE `tank_code` = '';
--       ALTER TABLE `tank_level_record`
--         MODIFY COLUMN `tank_code` varchar(64) DEFAULT NULL
--           COMMENT '容器编号（设备位号）；界面不填，无值时为 NULL';
--
--     改后复核（只读）：
--       SHOW CREATE TABLE `tank_level_record`;   -- 期望：无 uk_date_tank；tank_code 为 DEFAULT NULL
--       SELECT COUNT(*) FROM `tank_level_record` WHERE `tank_code` = '';   -- 期望 0
--       SELECT COUNT(*) FROM `tank_level_record`;                          -- 期望与改前总行数一致
--
--     回滚（⚠️ 有损前提：只有在"同一天同容器"没有产生重复行时才可无损回滚）：
--       ALTER TABLE `tank_level_record`
--         MODIFY COLUMN `tank_code` varchar(64) DEFAULT '' COMMENT '容器编号（设备位号）';
--       UPDATE `tank_level_record` SET `tank_code` = '' WHERE `tank_code` IS NULL;
--       ALTER TABLE `tank_level_record`
--         ADD UNIQUE KEY `uk_date_tank` (`record_date`, `tank_code`);
--       -- 回滚前先跑这句对账，非空则**不要**回滚（加回唯一键会直接失败）：
--       SELECT `record_date`, COUNT(*) FROM `tank_level_record`
--        GROUP BY `record_date` HAVING COUNT(*) > 1;
--
--     本地执行：**已执行 2026-10-20 11:14**（本机 factory_db；执行后 SHOW INDEX 只剩 PRIMARY /
--       idx_record_date / idx_material_code / idx_tank_code）。
--     生产执行：**待执行**（本机连不上生产库；脚本见上，执行后在此补时间与备份 md5）。

-- ===========================================================================
-- 24) 分层访问与写操作留痕（2026-10-20，变更-129）
--
-- 使用方要求：游客能看见所有功能但点锁定的提示"需要注册"；注册用户只读；
-- 写操作一律回管理员；**所有越权尝试与写操作都要留痕（谁 / IP / 什么时候 / 动了什么）**。
--
-- 本步只做两件事：
--   a) 建 audit_event 表（审计落点）
--   b) 建一个新的只读角色 member（自助注册的账号用它）
--
-- 改前核对：
--   SELECT COUNT(*) FROM information_schema.tables
--    WHERE table_schema = DATABASE() AND table_name = 'audit_event';   -- 期望 0
--   SELECT COUNT(*) FROM sys_role WHERE role_key = 'member';           -- 期望 0
--
-- 改后复核：
--   SELECT COUNT(*) FROM information_schema.tables
--    WHERE table_schema = DATABASE() AND table_name = 'audit_event';   -- 期望 1
--   SELECT id, role_name, role_key FROM sys_role WHERE role_key = 'member';   -- 期望 1 行
--   SELECT COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id WHERE r.role_key = 'member';       -- 期望 0（member 不给写权限）
--
-- 回滚：
--   DROP TABLE IF EXISTS audit_event;
--   DELETE FROM sys_role WHERE role_key = 'member';   -- 若无用户引用可直接删
--
-- ⚠️ member 是**只读**角色：**不要**给它任何 *:edit / *:import / *:upload / *:delete。
--    它连 *:view 都不需要 —— 四个台账页改成"登录即可看"，走的是登录闸门而不是权限位。
-- ===========================================================================

CREATE TABLE IF NOT EXISTS `audit_event` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event`         varchar(32)  NOT NULL COMMENT 'register/login/logout/write_denied/write_ok/locked_clicked',
  `username`      varchar(64)  NOT NULL DEFAULT '' COMMENT '登录名；匿名记空串',
  `ip`            varchar(64)  NOT NULL DEFAULT '' COMMENT 'X-Forwarded-For 第一个，回落到 remoteAddr',
  `user_agent`    varchar(255) NOT NULL DEFAULT '' COMMENT 'User-Agent，截断 255',
  `method`        varchar(8)   NOT NULL DEFAULT '' COMMENT 'GET/POST/...；注册与登录事件留空',
  `path`          varchar(255) NOT NULL DEFAULT '' COMMENT '请求路径',
  `target`        varchar(128) NOT NULL DEFAULT '' COMMENT '目标：表名或文件前缀（如 archive/）',
  `params_digest` varchar(64)  NOT NULL DEFAULT '' COMMENT '参数摘要 sha256 前 16 位；**绝不存正文**',
  `result`        varchar(16)  NOT NULL DEFAULT '' COMMENT 'ok / denied',
  `created_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_created` (`created_at`),
  KEY `idx_username` (`username`),
  KEY `idx_event` (`event`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='写操作与越权尝试的留痕（IP/UA 属个人数据：仅 admin 可查，保留 180 天）';

-- 自助注册的只读账号角色（幂等：已存在只更新描述）
INSERT INTO `sys_role` (`role_name`, `role_key`, `description`)
VALUES ('普通用户', 'member', '自助注册的只读账号；写操作一律回管理员（变更-129）')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);
-- ===========================================================================
-- 第 25 步：物料主数据按 SAP 全量重建（变更-159，2026-10-23）
--
-- 背景：原 material_master 只有 245 行（从 Excel 抠的一小部分，用于图片解析按名称反查），
-- 本次换成 SAP 导出的全量 30,434 行（物料清单1022.xlsx），并承载"专门查物料"的页面。
--
-- 三条来自真实数据的结论，决定了表结构：
--   ① **物料编码不唯一**：30,434 行只有 30,392 个编码——罐类物料一个编码有 8 行
--      （主档 + 7 个评估类型），所以主键是独立自增 id，唯一键是 (编码, 评估类型)；
--   ② **评估类型是罐体状态/等级**（新/A/B/C/D/翻新/内衬），价格挂在它上面，
--      7 种状态价各不相同（例 220000445：新 433.63 / B 303.54 / D 108.41 / 内衬 152.5）；
--   ③ **停用是靠名称标记的**：SAP 没有状态列。实测「禁用」4,799 行、含「禁用」其它写法
--      211 行、含「作废」351 行、含「勿用」29 行 → 合计 5,390 行停用；
--      另有 2,136 行名字带「_待禁用」→ 单列一档 status=2（仍可搜到，界面挂黄标）。
--
-- 结论分布（导入后核对）：status 1=22,908 / 2=2,136 / 0=5,390；编码 30,392；
--   有评估类型 42 行；价格>0 7,938 行；采购组空 507；MRP 空 27,265。
--
-- ⚠️ 回滚：本机已留 material_master_bak_1023（旧 245 行）。
--    RENAME TABLE material_master TO material_master_v159,
--                 material_master_bak_1023 TO material_master;
-- ===========================================================================

CREATE TABLE `material_master` (
  `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键（独立自增；物料编码不唯一）',
  `material_code`     char(9)      NOT NULL COMMENT '物料编码',
  `valuation_type`    varchar(8)   NOT NULL DEFAULT '' COMMENT '评估类型：空=主档；A/B/C/D/内衬/新/翻新',
  `material_name`     varchar(255) NOT NULL COMMENT '物料描述',
  `name_norm`         varchar(255) NOT NULL DEFAULT '' COMMENT '归一化名称（图片解析反查用）',
  `spec`              varchar(255) DEFAULT NULL COMMENT '规格（SAP 导出无此列，保留兼容旧接口）',
  `unit`              varchar(16)  DEFAULT NULL COMMENT '基本计量单位',
  `material_type`     varchar(8)   NOT NULL COMMENT '物料类型 Z001-Z010',
  `material_group`    varchar(16)  DEFAULT NULL COMMENT '物料组',
  `product_group`     varchar(16)  DEFAULT NULL COMMENT '产品组（保留兼容）',
  `procurement_group` varchar(8)   DEFAULT NULL COMMENT '采购组',
  `mrp_type`          varchar(4)   DEFAULT NULL COMMENT 'MRP 类型',
  `valuation_class`   varchar(8)   DEFAULT NULL COMMENT '评估类（ValCl）',
  `price_control`     char(1)      DEFAULT NULL COMMENT '价格控制 V/S',
  `price`             decimal(18,4) DEFAULT NULL COMMENT '价格（按 price_unit）',
  `price_unit`        int          NOT NULL DEFAULT 1 COMMENT '价格单位 1/1000',
  `currency`          char(3)      NOT NULL DEFAULT 'CNY' COMMENT '货币',
  `plant`             varchar(8)   DEFAULT NULL COMMENT '工厂（当前恒 1503）',
  `creator`           varchar(16)  DEFAULT NULL COMMENT '创建人',
  `changed_at`        date         DEFAULT NULL COMMENT '上次更改',
  `status`            tinyint      NOT NULL DEFAULT 1 COMMENT '1 正常 / 2 待停用 / 0 停用',
  `enabled`           tinyint      NOT NULL DEFAULT 1 COMMENT '兼容旧接口：status<>0 即 1',
  `source_sheet`      varchar(64)  DEFAULT NULL COMMENT '来源工作表',
  `source_row`        int          DEFAULT NULL COMMENT '来源行号',
  `source_file`       varchar(128) DEFAULT NULL COMMENT '来源文件名',
  `imported_at`       datetime     DEFAULT NULL COMMENT '导入时间',
  `create_time`       datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_vt` (`material_code`, `valuation_type`),
  KEY `idx_name_norm` (`name_norm`),
  KEY `idx_code` (`material_code`),
  KEY `idx_name` (`material_name`(64)),
  KEY `idx_type_code` (`material_type`, `material_code`),
  KEY `idx_group` (`material_group`),
  KEY `idx_status` (`status`),
  KEY `idx_vt` (`valuation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料主数据（一码 x 评估类型 一行）';

-- 四张字典：界面上的中文名、徽标配色、说明全从这里来，代码里不写 if
CREATE TABLE `material_type_dict` (
  `type_code` varchar(8) NOT NULL, `type_name` varchar(32) NOT NULL,
  `default_mrp` varchar(4) DEFAULT NULL, `default_procurement_group` varchar(8) DEFAULT NULL,
  `color` varchar(16) DEFAULT NULL, `sort_no` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料类型字典';

CREATE TABLE `procurement_group_dict` (
  `group_code` varchar(8) NOT NULL, `group_name` varchar(32) NOT NULL,
  `meaning` varchar(128) DEFAULT NULL, `sort_no` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购组字典';

CREATE TABLE `mrp_type_dict` (
  `mrp_code` varchar(4) NOT NULL, `mrp_label` varchar(16) NOT NULL,
  `meaning` varchar(128) DEFAULT NULL, `sort_no` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`mrp_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MRP 类型字典';

CREATE TABLE `material_valuation_type_dict` (
  `valuation_type` varchar(8) NOT NULL, `label` varchar(16) NOT NULL,
  `meaning` varchar(128) NOT NULL, `sort_no` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`valuation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评估类型字典（罐体状态/等级）';

-- 字典数据见同目录 material-dict-seed.sql（10 + 9 + 4 + 8 行，幂等 INSERT ... ON DUPLICATE KEY UPDATE）