package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资料台账列表的查询入参。
 *
 * <p>继承 {@link PageQueryDTO}（分页参数必传，缺参回 400 —— 它自己那段注释解释了为什么
 * 不给默认值），追加四个筛选字段。
 *
 * <p>为什么没有"按创建时间区间"：资料归档看的是**有效期**，不是录入时间
 * （开工方案 Q1/Q10）。{@code PageQueryDTO} 带来的 startDate/endDate 这里不用，保留即可。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveDocumentQueryDTO extends PageQueryDTO {

    /** 关键字：名称 / 编号文号 / 责任人 / 原件位置（四列 OR 模糊） */
    private String keyword;

    /** 分类 id（传大类时**自动含其子类**） */
    private Long categoryId;

    /**
     * 只看 N 天内到期的（含已过期）。
     *
     * <p>0 或空 = 不筛。页面上那个"只看 30 天内到期"开关就是传 30。
     */
    private Integer expireWithinDays;
}
