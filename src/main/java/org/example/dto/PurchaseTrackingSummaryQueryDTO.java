package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「采购进度跟踪」汇总接口的查询条件。
 *
 * <p>继承 {@link DateRangeQueryDTO} 而不是 {@code PurchaseTrackingQueryDTO}：
 * 汇总不是记录页，**不该被迫接收一对用不上的 pageNum / pageSize**
 * （{@code DateRangeQueryDTO} 的注释就是为此写的）。
 *
 * <p>筛选口径必须与列表**完全一致**（关键字 + 提出需求日期区间），
 * 否则会出现「顶栏说超期 3 条、列表筛出来 5 条」这种对不上的数字 ——
 * 两边共用 {@code PurchaseTrackingServiceImpl} 里的同一个 filter 构造。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseTrackingSummaryQueryDTO extends DateRangeQueryDTO {

    /** 关键字（与列表同一个口径：需求人/物料名称/物料编码/规格/OA单号/送货单） */
    private String keyword;

    /** 类别筛选；与列表一致（左上角汇总条要跟着类别变，才说得通） */
    private String category;
}
