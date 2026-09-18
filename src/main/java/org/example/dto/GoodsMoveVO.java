package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 货物移动列表项（前端表格列）。
 */
@Data
public class GoodsMoveVO {
    private Long moveNo;           // 移动单号（取数据库主键 id）
    private String materialCode;   // 货物编码
    private String materialDesc;   // 货物名称
    private String moveType;       // 移动类型（中文名）
    private BigDecimal moveQty;    // 移动数量
    private String fromLocation;   // 来源库位（存储地点）
    private String toLocation;     // 目标库位（表无此概念，返回 null）
    private LocalDate moveDate;    // 移动日期
}
