package org.example.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 储罐列表行（2026-11-09）。
 *
 * <p>罐自己的字段 + 从图纸只读带出的几何 —— 前端不用再查一次图纸，
 * 也保证"几何只有一处来源"（图纸）。
 */
@Data
public class EquipmentTankVO {

    private Long id;
    private String tankCode;
    private String tankName;
    private String workshop;
    private String location;
    private String category;
    private String materialCode;
    private String materialName;
    private BigDecimal density;
    private Integer enabled;
    private String remark;

    /** 绑定的图纸 */
    private Long blueprintId;
    private String blueprintName;
    private String blueprintSpec;
    private Integer containerType;

    /** 以下几何来自图纸，只读 */
    private BigDecimal innerDiameter;
    private BigDecimal shellLength;
    private BigDecimal topHeadDepth;
    private BigDecimal bottomHeadDepth;
    private BigDecimal straightFlange;
    private BigDecimal volume;

    /** 图纸几何是否齐全（不齐就参与不了体积计算，界面上要说清楚） */
    private Boolean geometryReady;
}
