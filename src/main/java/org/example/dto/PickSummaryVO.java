package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 领料汇总列表项（前端表格列：物料名称/物料编码/领料时间/领料数量/单位/单据图片）。
 */
@Data
public class PickSummaryVO {
    private String documentNo;       // 单据号（唯一）
    private String materialName;     // 物料名称
    private String materialCode;     // 物料编码
    private LocalDate pickDate;      // 领料时间
    private BigDecimal pickQty;      // 领料数量
    private String unit;             // 单位
    private String imageUrl;         // 单据图片预签名访问 url（由 fileName 实时生成）
}
