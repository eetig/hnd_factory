package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料主数据项。
 *
 * <p>2026-10-23（变更-159）从「四个字段」扩到「一屏够用的一组」：物料主数据页要展示
 * 类型徽标、物料组、采购组、MRP、状态与价格，而图片解析只用 code/name/spec/unit。
 * 扩字段是向后兼容的（老调用方多收字段不影响），所以没有拆成两个 VO。
 */
@Data
public class MaterialVO {
    private String code;                // 物料编码
    private String name;                // 物料名称
    private String spec;                // 规格（SAP 导出无此列，保留兼容）
    private String unit;                // 基本计量单位

    private String typeCode;            // Z001–Z010
    private String typeName;            // 原材料 / 辅料包 / …
    private String typeColor;           // 徽标色（来自字典，前端直接用）
    private String group;               // 物料组（代码，暂无中文名）
    private String procurementGroup;    // 采购组代码
    private String procurementGroupName;// 采购组名称
    private String mrpType;             // PD / M0 / ND
    private String mrpLabel;            // 按计划驱动 / …
    private Integer status;             // 1 正常 / 2 待停用 / 0 停用
    private BigDecimal price;           // 价格（按 priceUnit）
    private Integer priceUnit;          // 1 / 1000
    private String currency;            // CNY
    private String priceControl;        // V / S
    /** 该编码的评估类型条数（>0 时前端显示「N 种状态价」） */
    private Integer valuationCount;
}
