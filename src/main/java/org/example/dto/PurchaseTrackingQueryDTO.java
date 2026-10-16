package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「采购进度跟踪」列表的查询条件：关键字 + 提出需求日期区间 + 分页。
 *
 * <p>日期区间复用 {@link DateRangeQueryDTO} 的 {@code startDate} / {@code endDate}，
 * 在服务端作用于**提出需求日期**（{@code request_date}）—— 页面上的日期筛选框就标着这个含义。
 * 想按「要求到货日期」筛是另一个需求，真要加时再加一个独立的字段，不要把这个的含义改掉。
 *
 * <p>关键字是**模糊**匹配，命中页面上看得见的那些文本列：需求人 / 物料名称 / 物料编码 /
 * 规格型号 / 采购订单号 / 送货单号。理由：这一页的用法是「找那条记录」，
 * 而使用者手里可能只有订单号、可能只有料名 —— 只让按编码搜等于逼他们换一种问法。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseTrackingQueryDTO extends PageQueryDTO {

    /** 关键字：模糊匹配 需求人 / 物料名称 / 物料编码 / 规格型号 / 采购订单号 / 送货单号，空白表示不限 */
    private String keyword;
}
