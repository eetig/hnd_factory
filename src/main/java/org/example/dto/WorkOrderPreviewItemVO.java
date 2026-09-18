package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 导入预览行：工单号、物料编码、物料描述、订单数量、基本开始日期、确认的产量。
 */
@Data
public class WorkOrderPreviewItemVO {
    private String orderNo;             // 工单号
    private String materialCode;        // 物料编码
    private String materialDesc;        // 物料描述（产成品）
    private BigDecimal orderQty;        // 订单数量
    private LocalDate planStartDate;    // 基本开始日期
    private BigDecimal confirmedQty;    // 确认的产量
}
