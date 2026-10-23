package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料导入的一行（SAP 导出的一行 = 一个「物料编码 × 评估类型」）。
 *
 * <p>字段与 {@code material_master} 一一对应；{@code status} 不由文件给出，而是从
 * 名称里的停用标记推导（SAP 导出没有状态列，见 docs/schema.sql 第 25 步）。
 */
@Data
public class MaterialImportRow {
    private String code;
    private String valuationType = "";
    private String name;
    private String type;
    private String group;
    private String unit;
    private String procurementGroup;
    private String mrpType;
    private String valuationClass;
    private String priceControl;
    private BigDecimal price;
    private Integer priceUnit = 1;
    private String currency = "CNY";
    private String plant;
    private String creator;
    private String changedAt;
    private Integer status = 1;
    private Integer sourceRow;

    /** 差异比对用的自然键：编码 + 评估类型（库里唯一） */
    public String key() {
        return code + "|" + (valuationType == null ? "" : valuationType);
    }
}
