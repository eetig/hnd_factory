package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 月底储罐液位记录的查询条件。
 *
 * <p>这四个筛选项在这条业务线上**本来就是服务端做的**（改造前前端就按接口查询，
 * 不像领料/入库那样全量拉回来再本地过滤）—— 当时只差分页还在前端 {@code slice}。
 * 所以本类基本上就是把控制器上那四个 {@code @RequestParam} 收进一个对象，加上分页。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TankLevelQueryDTO extends PageQueryDTO {

    /** 属地（罐组）精确匹配，空白表示不限 */
    private String location;

    /** 所属（产品 / 原料）精确匹配，空白表示不限 */
    private String category;

    /** 关键字：模糊匹配 物料编码 / 物料名称 / 容器名称 / 容器编号，空白表示不限 */
    private String keyword;
}
