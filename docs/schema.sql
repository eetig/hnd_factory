-- =============================================================================
-- hnd_factory 数据库结构 + 初始化数据
-- 数据库：MySQL 8.0+（使用了 utf8mb4_0900_ai_ci 排序规则）
--
-- 用法：
--   mysql -h127.0.0.1 -uroot -p < docs/schema.sql
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
-- 工单物料图片（file_name 指向 MinIO 对象，访问 url 由 img-service 实时签名）
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
-- 业务唯一键：(document_no + material_code) —— 同一单据下不同物料是不同记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `production_inbound` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `seq_no`        int           DEFAULT NULL COMMENT '序号',
  `document_no`   varchar(32)   DEFAULT NULL COMMENT '单据号（与物料编码组成唯一键）',
  `inbound_date`  date          DEFAULT NULL COMMENT '日期',
  `material_name` varchar(128)  DEFAULT NULL COMMENT '物料名称',
  `material_code` varchar(32)   DEFAULT NULL COMMENT '物料编码',
  `inbound_qty`   decimal(18,4) DEFAULT NULL COMMENT '入库数量',
  `unit`          varchar(16)   DEFAULT NULL COMMENT '单位',
  `file_name`     varchar(255)  DEFAULT NULL COMMENT '单据图片文件名(MinIO，来源 Excel 内嵌图)',
  `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_no_material` (`document_no`,`material_code`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_inbound_date` (`inbound_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产入库单';


-- ---------------------------------------------------------------------------
-- 领料汇总（Excel「领料汇总」导入）
-- 业务唯一键：(document_no + material_code)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `material_pick_summary` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `seq_no`        int           DEFAULT NULL COMMENT '序号',
  `document_no`   varchar(32)   DEFAULT NULL COMMENT '单据号（与物料编码组成唯一键）',
  `pick_date`     date          DEFAULT NULL COMMENT '日期',
  `material_name` varchar(128)  DEFAULT NULL COMMENT '物料名称',
  `material_code` varchar(32)   DEFAULT NULL COMMENT '物料编码',
  `pick_qty`      decimal(18,4) DEFAULT NULL COMMENT '领料数量',
  `unit`          varchar(16)   DEFAULT NULL COMMENT '单位',
  `file_name`     varchar(255)  DEFAULT NULL COMMENT '单据图片文件名(MinIO，来源 Excel 内嵌图)',
  `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_no_material` (`document_no`,`material_code`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_pick_date` (`pick_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领料汇总单';


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


-- =============================================================================
-- 第 2 部分：初始化数据（幂等，可重复执行）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 角色
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role` (`role_name`, `role_key`, `description`) VALUES
  ('管理员', 'admin',       '全部权限'),
  ('班组长', 'team_leader', '本组工单编辑'),
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
--
-- 说明：查询类接口后端已放开免登录，*:view 主要供前端做按钮显隐；
--      后端实际强制校验的是写操作权限（edit/import/upload/delete）。
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
  -- team_leader：除「删除工单」外
  SELECT 'team_leader', 'work_order:view'         UNION ALL
  SELECT 'team_leader', 'work_order:add'          UNION ALL
  SELECT 'team_leader', 'work_order:edit'         UNION ALL
  SELECT 'team_leader', 'work_order:import'       UNION ALL
  SELECT 'team_leader', 'work_order:image:upload' UNION ALL
  SELECT 'team_leader', 'work_order:image:delete' UNION ALL
  SELECT 'team_leader', 'goods_move:view'         UNION ALL
  SELECT 'team_leader', 'goods_move:import'       UNION ALL
  SELECT 'team_leader', 'pick:view'               UNION ALL
  SELECT 'team_leader', 'inbound:view'            UNION ALL
  -- operator：可查看 + 可导入货物移动，不能改工单
  SELECT 'operator', 'work_order:view'            UNION ALL
  SELECT 'operator', 'goods_move:view'            UNION ALL
  SELECT 'operator', 'goods_move:import'          UNION ALL
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
-- 初始账号
--
--   admin / admin123   管理员（全部权限）
--   guest / admin      游客（只读，不能导入/上传任何文件）
--   test  / test       游客（只读，早期测试账号）
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
-- ALTER TABLE `production_inbound`
--   ADD COLUMN `document_no` varchar(32) DEFAULT NULL COMMENT '单据号（与物料编码组成唯一键）' AFTER `seq_no`;
-- ALTER TABLE `production_inbound`
--   ADD UNIQUE KEY `uk_document_no_material` (`document_no`,`material_code`);

-- -- 4) 领料汇总：新增单据号列 + 唯一键
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
