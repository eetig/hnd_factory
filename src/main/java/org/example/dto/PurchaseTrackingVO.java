package org.example.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 「采购进度跟踪」列表项（页面上就是那一行）。
 *
 * <p>字段与实体一一对应 —— 这一页没有需要联查或派生的列（物料名称与编码在本表里是快照文本，
 * 见实体注释）。**之所以仍然单独建 VO 而不是直接回实体**：实体是库表形状，
 * VO 是页面形状，两者现在相等是巧合；将来页面要加一列派生值（比如"已到货/未到货"）
 * 时只改 VO，不必动实体，也不必让前端跟着库表变。
 */
@Data
public class PurchaseTrackingVO {

    /** 序号 */
    private Long id;

    /** 需求人 */
    private String requester;

    /** 提出需求日期 */
    private LocalDate requestDate;

    /** 物料名称（快照） */
    private String materialName;

    /** 物料编码（快照，可能为空 —— 编码后面才定） */
    private String materialCode;

    /** 规格型号 */
    private String spec;

    /** 要求到货日期 */
    private LocalDate requiredArrivalDate;

    /** 采购订单号（空串 = 还没下单） */
    private String purchaseOrderNo;

    /** 送货单号（空串 = 还没到货） */
    private String deliveryNote;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
