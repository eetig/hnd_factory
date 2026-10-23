-- 物料主数据字典种子（变更-159，2026-10-23）
-- 幂等：重复执行只更新显示名/说明/排序。
-- 用法：mysql -h127.0.0.1 -uroot -p --default-character-set=utf8mb4 factory_db < docs/material-dict-seed.sql

INSERT INTO `material_type_dict`
  (`type_code`,`type_name`,`default_mrp`,`default_procurement_group`,`color`,`sort_no`) VALUES
  ('Z001','原材料','PD','106','#4C6B2A',1),
  ('Z002','辅料包','ND','','#8A6410',2),
  ('Z003','半成品','PD','','#2E7F8A',3),
  ('Z004','罐成品','M0','','#A85F0C',4),
  ('Z005','包成品','M0','998','#B84A72',5),
  ('Z006','包材','ND','108','#3F73C4',6),
  ('Z007','设备及配件','','107','#6E2A66',7),
  ('Z008','备品备件','','107','#4F6480',8),
  ('Z009','行政服务','','107','#6B6355',9),
  ('Z010','服务/费用','','107','#8A4A2E',10)
ON DUPLICATE KEY UPDATE
  `type_name`=VALUES(`type_name`), `default_mrp`=VALUES(`default_mrp`),
  `default_procurement_group`=VALUES(`default_procurement_group`),
  `color`=VALUES(`color`), `sort_no`=VALUES(`sort_no`);

INSERT INTO `procurement_group_dict` (`group_code`,`group_name`,`meaning`,`sort_no`) VALUES
  ('','未分配','该物料未分配采购组',0),
  ('106','原材料采购','负责 Z001 原材料采购',1),
  ('108','包材采购','负责 Z006 包装材料采购',2),
  ('107','设备-备件-行政-服务','负责 Z007/Z008/Z009/Z010',3),
  ('998','成品采购','成品/外购成品采购',4),
  ('999','特殊采购','临时/项目/历史遗留，需业务确认',5),
  ('101','待确认','仅 1 行，业务待确认',6),
  ('102','待确认','仅 1 行，业务待确认',7),
  ('105','待确认','仅 1 行，业务待确认',8)
ON DUPLICATE KEY UPDATE
  `group_name`=VALUES(`group_name`), `meaning`=VALUES(`meaning`), `sort_no`=VALUES(`sort_no`);

INSERT INTO `mrp_type_dict` (`mrp_code`,`mrp_label`,`meaning`,`sort_no`) VALUES
  ('','未维护','导出里无 MRP 类型（89.6% 的行）',0),
  ('PD','按计划驱动','系统按需求自动算采购/生产计划',1),
  ('M0','按库存生产','按库存水平触发补货',2),
  ('ND','不参与计划','不自动算需求，手工管理',3)
ON DUPLICATE KEY UPDATE
  `mrp_label`=VALUES(`mrp_label`), `meaning`=VALUES(`meaning`), `sort_no`=VALUES(`sort_no`);

INSERT INTO `material_valuation_type_dict` (`valuation_type`,`label`,`meaning`,`sort_no`) VALUES
  ('','主档','物料主档本身（价格控制 V 移动平均价）',0),
  ('新','全新未使用桶','全新未使用桶',1),
  ('A','一级新桶','质量最优',2),
  ('B','二级新桶','轻微瑕疵',3),
  ('C','三级桶','可继续使用',4),
  ('D','四级桶','需降级使用',5),
  ('翻新','翻新桶','经过清洗、修复的二手桶',6),
  ('内衬','带内衬吨桶','带内衬的吨桶（如防腐蚀内衬）',7)
ON DUPLICATE KEY UPDATE
  `label`=VALUES(`label`), `meaning`=VALUES(`meaning`), `sort_no`=VALUES(`sort_no`);