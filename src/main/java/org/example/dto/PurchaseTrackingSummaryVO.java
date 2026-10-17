package org.example.dto;

import lombok.Data;

/**
 * 「采购需求详情」的汇总计数（页面顶部那条汇总栏）。
 *
 * <p>⚠️ **为什么要有这个接口**：汇总栏显示的是「超期 N 条」这类**全量**数字，
 * 不是"当前页里有多少条"。ADR-0003 明确写了「聚合必须由服务端按同一套筛选条件算」，
 * 用当前页 group by 只会得到前 10 条的合计（那个坑在 {@code useStatsData.js} 的注释里记过）。
 *
 * <p>计数按**派生状态**分组，与列表行上的标签一一对应，前端不需要再做加减：
 * {@code pendingOa + waiting + dueSoon + overdue + arrived + partial = total}。
 */
@Data
public class PurchaseTrackingSummaryVO {

    /** 当前筛选条件下的总条数（按状态求和应等于它，便于前端自检） */
    private int total;

    /** 待提单：登记了但还没提单 */
    private int pendingOa;

    /** 等货：已提单、未到货，且还不到临期 */
    private int waiting;

    /** 临期：距要求到货日 ≤ {@code PurchaseTrackingStatus#DUE_SOON_DAYS} 天 */
    private int dueSoon;

    /** 超期：已过要求到货日仍未到货 */
    private int overdue;

    /** 已到货（全到） */
    private int arrived;

    /** 部分到货：到货数量 < 需求数量 */
    private int partial;
}
