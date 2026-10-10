package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 入库明细「按物料汇总」的一行 —— 工单核算页的「入库数」来源。
 *
 * <p>为什么需要它：那一页不是列表，是**拿全量明细做 group by 的聚合表**
 * （页面注释：「以已入库产成品（入库汇总当前筛选后的物料名称去重）为行，入库数与已报工数汇总」）。
 * 列表改成后端分页之后，分页数据喂不了它。
 *
 * <p>⚠️ 字段名（{@code materialCode} / {@code materialName} / {@code inboundQty}）**刻意与
 * {@link ProductionInboundVO} 同名**，理由见 {@link PickSummaryGroupVO}：
 * 前端那段 group by 循环与它的单测因此一行都不用改。
 */
@Data
public class InboundSummaryGroupVO {

    /** 物料编码 */
    private String materialCode;

    /** 物料名称（产成品名） */
    private String materialName;

    /** 入库数合计 */
    private BigDecimal inboundQty;
}
