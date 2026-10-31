package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一张工单的投料情况（2026-10-31，变更-172）。
 *
 * <p>数据源是 `material_movement`（SAP 货物移动凭证），**不是**领料单 ——
 * 领料单（material_pick / material_pick_summary）里没有工单号，关联不上。
 * 移动凭证 3851 行**全部带 order_no**，所以能精确按工单聚合。
 *
 * <p>口径（使用方 2026-10-31 确认）：
 * <ul>
 *   <li>只取 <b>261 投料</b> 与 <b>262 冲销</b>，两者相抵得净投料；</li>
 *   <li>261 在库里是**负数**（如 -1835），所以净投料 = −(Σquantity)，结果为正；
 *       冲销是正数，相抵后自然扣掉；</li>
 *   <li>按物料汇总（同一物料多笔合并），单位各自保留；</li>
 *   <li>混合单位时 {@code mixedUnit=true}，前端不做跨单位合计。</li>
 * </ul>
 */
@Data
public class WorkOrderMaterialVO {

    /** 工单号 */
    private String orderNo;

    /** 净投料数量合计（各物料之和；仅在单位一致时有意义） */
    private BigDecimal net;

    /** 单位（一致时给出；混合时为 null） */
    private String unit;

    /** 是否混合单位（true 时 net 无意义，只按 items 看） */
    private boolean mixedUnit;

    /** 该工单涉及的物料数 */
    private int materialCount;

    /** 按物料汇总后的明细 */
    private List<Item> items;

    @Data
    public static class Item {
        private String materialCode;
        private String materialDesc;
        /** 净数量（正数） */
        private BigDecimal qty;
        private String unit;
        /** 261 笔数（便于判断是"一次投完"还是"分批投"） */
        private int issueRows;
        /** 是否有冲销（262） */
        private boolean reversed;
    }
}
