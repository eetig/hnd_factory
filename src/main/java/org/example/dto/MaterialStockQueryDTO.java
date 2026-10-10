package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「物料查询」页的查询条件：关键字 + 只看有库存 + 分页。
 *
 * <p>⚠️ 这两个筛选项原先都在**前端**做（{@code applyStockFilter}：把整表拉回来之后
 * 用 JS 过滤）。2026-10-10 的统一整改把「前端全量 + 本地筛选」否掉了，它们改由服务端执行。
 *
 * <p>关键字命中**展示值**（物料编码 / 物料名称 / 规格）—— 注意名称与规格是
 * 「主数据优先、回退库存表」的联查结果，所以过滤必须发生在**合并之后**，
 * 不能只筛 material_stock 自己那两列，否则「主数据里有名字、库存表里没名字」的行会搜不到。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialStockQueryDTO extends PageQueryDTO {

    /** 关键字：模糊匹配 物料编码 / 物料名称 / 规格，空白表示不限 */
    private String keyword;

    /**
     * 只看有库存（{@code stock_qty > 0}）。
     *
     * <p>默认 **false**（列出全部，含数量为 0 的物料）—— 这一页按使用方的要求是
     * 「查物料信息」，不是「看还有多少货」：快照语义下文件里消失的物料会被清零但保留行，
     * 默认藏起来就查不到它们了。
     */
    private Boolean onlyInStock;
}
