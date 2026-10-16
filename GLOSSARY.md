# 领域词表（GLOSSARY.md）

本文件给 hnd_factory 的领域概念**定名**。写代码、写注释、做架构评审时用这里的词；
引入新概念（尤其是「单据类型」「唯一键」「图据」这类会反复出现的）先在这里定名，再写实现。

> 由 2026-10-16 的架构评审（improve-codebase-architecture）建立。当时本仓没有词表也没有 ADR，
> 「五种单据为什么各写一份导入工具」「hnd-file 为什么还留着」这类问题只能靠翻提交记录回答。

## 单据与单据类型

| 词 | 含义 | 代码对应 |
|---|---|---|
| **单据类型 / bill type** | Excel 导入与 OCR 确认共用的五种业务单据分类 | `WorkOrderImportController.TYPE_*`；`detectType(headers, filename, templateCode)` |
| **工单汇总** | 生产工单的汇总表（按订单号 upsert） | `WorkOrder` / `work_order` / `WorkOrderImportUtil` |
| **货物移动** | SAP 货物移动明细 | `MaterialMovement` / `material_movement` / `GoodsMoveImportUtil` |
| **领料汇总** | 领料单汇总（行身份含日期） | `MaterialPickSummary` / `material_pick_summary` / `MaterialPickSummaryImportUtil` |
| **库存汇总** | SAP 库存导出（工厂 + 存储地点是唯一键组成部分） | `MaterialStock` / `material_stock` / `MaterialStockImportUtil` |
| **生产入库单** | 生产入库明细 | `ProductionInbound` / `production_inbound` / `ProductionInboundImportUtil` |
| **单据行唯一键** | 业务上的「同一行」判据：单据号 + 物料编码 + **日期** | `uk_document_no_material_date`；见 ADR-0001 |
| **整单删除 / 载入编辑** | 入库面板的两种编辑语义（追加 vs 覆盖），严格模式下会删行 | `DocumentDiffPlanner`、`DocSaveOptions.allowEmpty` |

## 单元格与取值

| 词 | 含义 | 代码对应 |
|---|---|---|
| **Excel 序列号** | Excel 日期的内部表示：自 1899-12-30 起的天数（如 `46202`） | `ExcelValueParser.parseDate` 的兜底档 |
| **千分位容错** | 半角逗号、全角逗号、空格一律剥掉后再解析 | `ExcelValueParser.parseDecimal` |
| **extMap** | 共享 DTO 上装「没有固定字段的表头」的 Map，键是归一化后的中文表头 | `WorkOrderExcelDTO.extMap`；键名见各 `*ImportUtil` 里的 `COL_*` 常量 |

## 图据与图片

| 词 | 含义 | 代码对应 |
|---|---|---|
| **图据** | 单据行的图片凭证（Excel 内嵌图剥离后上传 MinIO） | 行级 `file_name` 列；`ImageUrlBuilder` |
| **原图 / 缩略图** | 同一对象的两条固定 URL（`/files/`、`/thumbs/`），**不用预签名 URL** | `ImageUrlBuilder.urlOf` / `thumbUrlOf`；见 ADR-0004 |
| **容器底图** | 台账设备的底图文件名（内置写文件名，上传写 `/files/xxx.png`） | `EquipmentLedger.imageFile` |
| **图据坐标 / imageBounds** | 按图量出来的坐标，**不开放人工编辑** | `EquipmentLedger.imageBounds`（刻意不带更新注解） |

## 台账与维护

| 词 | 含义 | 代码对应 |
|---|---|---|
| **可编辑字段** | 维护页表单能改的那 16 项；清单是**数据**不是语句 | `EquipmentLedgerServiceImpl.EDITABLE_FIELDS` |
| **整行覆写** | 更新路径写入全部可编辑列（含 NULL），语义是「清空某列」也生效 | 见 ADR-0002 |
| **停用 / 启用** | 只改 `enabled` 一列的定向 UPDATE | `EquipmentLedgerService.setEnabled` |

## 架构词（与 `codebase-design` 一致）

**module**（模块）、**interface**（接口）、**implementation**（实现）、**depth**（深度：接口薄、实现厚）、
**seam**（接缝）、**adapter**（适配器）、**leverage**（杠杆：一处改动多处受益）、**locality**（局部性：改动集中一处）。

两条判据：
- **deletion test**：删掉这个模块，复杂度是**集中**了（说明它在承担职责）还是只是**搬走**了（说明它是多余的间接层）。
- **接口就是测试面**：一个模块好不好测，取决于它的接口是不是能插进固件 —— 本仓实测规律是「越深的模块测得住」
  （`DocumentImportSaveService` 4.9:1，被 14 个用例罩着；而 821 行的导入控制器曾是 0 覆盖）。

**不要**用 component / service / unit 代替 module，用 API / signature 代替 interface，用 boundary 代替 seam。
