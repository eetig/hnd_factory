package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 单据明细列表的查询条件：日期区间 + 物料名称 / 物料编码 / 单据号 + 分页。
 *
 * <p>领料汇总与入库汇总的筛选条件本来就一模一样（两个页面的筛选项、饼图口径都是同一套），
 * 所以共用这一份而不是各写一份：将来加一个筛选项，两边同时生效，不会只改了一边。
 *
 * <p>字段全部可选，不传即不加该条件；分页两个参数必传（见 {@link PageQueryDTO}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialDocQueryDTO extends PageQueryDTO {

    /** 物料名称（精确匹配 —— 与前端筛选弹窗的语义一致，那不是模糊搜索框） */
    private String materialName;

    /** 物料编码（精确匹配） */
    private String materialCode;

    /** 单据号（精确匹配） */
    private String documentNo;
}
