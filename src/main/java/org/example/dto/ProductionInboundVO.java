package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产入库单列表项（字段与领料汇总 PickSummaryVO 对齐，便于前端复用同一套表格逻辑）。
 */
@Data
public class ProductionInboundVO {
    private String documentNo;       // 单据号
    private String materialName;     // 物料名称
    private String materialCode;     // 物料编码
    private LocalDate inboundDate;   // 入库时间
    private BigDecimal inboundQty;   // 入库数量
    private String unit;             // 单位

    /** 列表缩略图：相对路径（如 /thumbs/xxx.jpg）；无图片为 null */
    private String thumbnailUrl;

    /** 原图：相对路径（如 /files/xxx.jpg）；无图片为 null。字段名沿用旧契约，语义已由「预签名URL」变为「同源路径」 */
    private String imageUrl;
}
