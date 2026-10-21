package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 货物移动明细的查询条件（2026-10-21 变更-140）。
 *
 * <p>为什么新加一份而不是继续用 {@link PageQueryDTO}：原辅料核算要做钻取 ——
 * 点一行"已报工数"要看这一行由哪些货物移动组成，就必须能按**物料编码**过滤。
 * PageQueryDTO 只有分页 + 日期，没有物料维度。
 *
 * <p>日期沿用 DateRangeQueryDTO（父类的父类），口径是**过账日期** posting_date ——
 * 与 /summary-by-material 完全一致，这样钻取明细的合计能对上表里的数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GoodsMoveQueryDTO extends PageQueryDTO {

    /** 物料编码（精确匹配） */
    private String materialCode;

    /** 物料名称（精确匹配，给排查用；正常按编码查） */
    private String materialDesc;
}
