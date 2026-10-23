package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料的「评估类型」价格行（罐体状态/等级）。
 *
 * <p>只有罐类物料有：一个编码 8 行 = 主档 + 新/A/B/C/D/翻新/内衬。
 * 价格挂在评估类型上，所以详情页要把它单独列成一张价格表。
 */
@Data
public class MaterialValuationVO {
    private String type;            // '' / A / B / C / D / 翻新 / 内衬 / 新
    private String label;           // 主档 / 一级新桶 / …
    private String meaning;         // 质量最优 / …
    private BigDecimal price;       // 该状态的价格
    private String priceControl;    // V / S
}
