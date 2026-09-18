package org.example.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * V150 报工生产情况：一行代表一个工单，materialMap 为 物料名称 -> 汇总数量。
 */
@Data
public class V150ProductionVO {
    private LocalDate workStartDate;               // 需求日期（工单开始日期）
    private String workOrderNo;                    // 工单号
    private Map<String, BigDecimal> materialMap;   // key=物料名称, value=该物料汇总数量
}
