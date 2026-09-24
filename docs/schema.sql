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
--   test  / test       游客（只读）
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

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role_id`, `status`)
SELECT 'test',
       '$2a$12$ytWlN8zXfBwOXYisbVJFAORg7SVKemFAN9VfGaZ2fQ2hnvdBR.Vg2',
       '测试账号',
       r.`id`, 1
FROM `sys_role` r WHERE r.`role_key` = 'guest'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`), `real_name` = VALUES(`real_name`);


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
