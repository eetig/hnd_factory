package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 货物移动「按物料 + 来源库位汇总」的一行 —— 原辅料核算页的「已报工数」来源。
 *
 * <p>为什么必须带上来源库位分组：原辅料核算里那两行派生数据是按
 * 「物料编码 **+ 来源库位**」取数的（如「报工数仅统计来源库位 5003 的氯铂酸」）。
 * 只按物料汇总的话，派生行就没法从汇总结果里算出来。
 *
 * <p>⚠️ 字段名（{@code materialCode} / {@code fromLocation} / {@code moveQty}）**刻意与
 * {@link GoodsMoveVO} 同名**：前端那段「按物料编码累计数量 + 按来源库位筛派生行」的逻辑
 * 与它的单测因此一行都不用改，只是数据来源从「全量明细」换成「服务端汇总」。
 *
 * <p>行数极少（物料数 × 库位数），全量返回即可 —— 这是聚合结果，不是记录列表。
 */
@Data
public class GoodsMoveSummaryVO {

    /** 货物编码（物料编码） */
    private String materialCode;

    /** 来源库位（货物移动表里的 storage_location） */
    private String fromLocation;

    /** 该「物料 + 来源库位」组合下的移动数量合计（**带符号**，SAP 原样：正数入库、负数出库） */
    private BigDecimal moveQty;
}
