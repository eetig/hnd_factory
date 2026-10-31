package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 原辅料核算用：某个物料在一段期间的**净投料**（2026-10-31，变更-172 续）。
 *
 * <p>使用方 2026-10-31 报的现象：「乙炔工单的报工项里有次氯酸钠和无水氯化钙，
 * 但核算里没有」。查库确认：这两个物料 10 月**有 261 投料**（-3012 / -513 kg），
 * 但**没有领料单**（领料只到 9 月）⇒ 而原辅料核算表是**由领料汇总驱动**的，
 * 所以它们根本不出现。
 *
 * <p>结论：这张表要改成「领料 ∪ 投料」的并集，并把投料数单独列出来 ——
 * 让"投了没领"在表上直接可见（这正是对账要抓的账外料）。
 */
@Data
public class MaterialIssueSummaryVO {

    private String materialCode;

    private String materialDesc;

    /** 净投料（正数）：−(Σ261 + Σ262) */
    private BigDecimal qty;

    private String unit;

    /** 261 笔数 */
    private int issueRows;
}
