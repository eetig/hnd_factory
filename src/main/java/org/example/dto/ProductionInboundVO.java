package org.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** 线下单据图片：带签名的完整 url；没有则不返回该字段 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String imageUrl;
}
