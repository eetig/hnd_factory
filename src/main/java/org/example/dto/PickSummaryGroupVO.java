package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 领料明细「按物料汇总」的一行 —— 原辅料核算页的数据来源。
 *
 * <p>为什么需要它：那一页不是列表，是**拿全量明细做 group by 的聚合表**
 * （页面注释：「以领料汇总当前筛选后的物料名称去重为行，领料数按物料累加」）。
 * 列表改成后端分页之后，分页数据喂不了它 —— 拿第 1 页做 group by 只会得到前 10 条的合计。
 * 所以聚合必须由服务端按**同一套筛选条件**算完再给。
 *
 * <p>⚠️ 字段名（{@code materialCode} / {@code materialName} / {@code pickQty}）**刻意与
 * {@link PickSummaryVO} 同名**：两个前端里那段 group by 循环读的就是这几个名字，
 * 同名意味着核算逻辑与它的单测一行都不用改，只是数据来源从「全量明细」换成「服务端汇总」。
 * 改名会把一份已经验证过的口径重新实现一遍。
 *
 * <p>同理，**报表口径的换算与派生留在前端**（原辅料核算的换算系数、派生行、差额与排序）——
 * 那些是展示规则不是数据源。
 */
@Data
public class PickSummaryGroupVO {

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 领料数合计 */
    private BigDecimal pickQty;
}
