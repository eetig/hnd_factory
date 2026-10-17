package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「采购需求详情」列表的查询条件：关键字 + 提出需求日期区间 + 状态/类别 + 分页。
 *
 * <p>日期区间复用 {@link DateRangeQueryDTO} 的 {@code startDate} / {@code endDate}，
 * 在服务端作用于**提出需求日期**（{@code request_date}）—— 页面上的日期筛选框就标着这个含义。
 * 想按「要求到货日期」筛是另一个需求，真要加时再加一个独立的字段，不要把这个的含义改掉。
 *
 * <p>关键字是**模糊**匹配，命中页面上看得见的那些文本列：需求人 / 物料名称 / 物料编码 /
 * 规格型号 / OA 单号 / 送货单。理由：这一页的用法是「找那条记录」，
 * 而使用者手里可能只有单号、可能只有料名 —— 只让按编码搜等于逼他们换一种问法。
 *
 * <p>变更-096 加了 {@code status} 与 {@code category} 两个筛选：
 * <ul>
 *   <li>{@code category} 是**真列**，直接在 SQL 里等值过滤；</li>
 *   <li>{@code status} 是**派生状态**（待提单/等货/临期/超期/已到货/部分到货），库里没有这一列，
 *       处理方式见 {@code PurchaseTrackingServiceImpl#listPage} 的说明。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseTrackingQueryDTO extends PageQueryDTO {

    /** 关键字：模糊匹配 需求人 / 物料名称 / 物料编码 / 规格型号 / OA 单号 / 送货单，空白表示不限 */
    private String keyword;

    /** 派生状态筛选（PENDING_OA / WAITING / DUE_SOON / OVERDUE / ARRIVED / PARTIAL），空白表示不限 */
    private String status;

    /** 类别筛选（备件/五金 / 原料 / 设备 / 其他），空白表示不限 */
    private String category;
}
