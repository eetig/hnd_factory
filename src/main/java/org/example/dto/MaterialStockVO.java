package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料库存列表项（页面「物料查询」）。
 *
 * <p>页面的用法是「按 物料编码 / 物料名称 / 规格 查物料信息」，而这份信息分在两个表里：
 * material_master（规范名称与规格）与 material_stock（SAP 导出的库存，带一份描述与规格型号）。
 * 所以 物料名称 / 规格 **先查主数据，主数据没有就回退库存表那份** ——
 * 主数据目前只覆盖库存编码里的一小部分，纯按主数据来会让绝大多数行没名字。
 *
 * <p>两边都没有时为 **null**（不是空串）：显示成什么由前端定（当前是「/」）。
 */
@Data
public class MaterialStockVO {
    private Long id;
    private String plantCode;        // 工厂（页面当前不展示，留着备用）
    private String materialCode;     // 物料编码（联查键）
    private String materialName;     // 物料名称：主数据优先，回退库存表的「物料描述」
    private String spec;             // 规格：主数据优先，回退库存表的「规格型号」
    private String storageLocation;  // 存储地点
    private String storageDesc;      // 存储地点描述
    private String unit;             // 基本计量单位
    private BigDecimal stockQty;     // 非限制使用的库存
}
