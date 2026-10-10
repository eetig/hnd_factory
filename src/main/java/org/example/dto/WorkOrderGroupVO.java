package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工单「按 工单类型 + 产成品 汇总」的一行 —— 同时喂两个聚合页：
 *
 * <ul>
 *   <li><b>工单报工</b>：它以「工单类型 + 产成品」为行，把订单数量与确认产量按组求和
 *       （原实现见前端的 {@code reportRows}，拿的是全量工单）；</li>
 *   <li><b>工单核算</b>：它只要「按产成品汇总的确认产量」（{@code reportedQtyMap}），
 *       由前端把本结果里同名产成品的 confirmedQty 再累一次即可 —— 几十行数据，
 *       不值得为它多开一个接口。</li>
 * </ul>
 *
 * <p>为什么分组键里要带类型：工单报工那一页的行主键就是「类型 + 产成品」。
 * 类型由**工单号前缀**识别（服务端 WorkOrderTypeEnum / 前端 REPORT_ORDER_TYPES 同一套字典），
 * SQL 里按 {@code LEFT(order_no, 4)} 分组，再在 Java 里映射成中文名与 typeKey
 * —— 前缀→类型的映射只留一份实现，不要把前缀当成类型直接吐给前端。
 *
 * <p>⚠️ 字段名（{@code materialName} / {@code orderQty} / {@code confirmedQty}）与
 * {@code WorkOrder} 实体对应字段同名，前端那两段求和逻辑因此不用改写法。
 */
@Data
public class WorkOrderGroupVO {

    /** 工单类型标识（operate / package / transfer / rework），识别不出为空串 */
    private String typeKey;

    /** 工单类型中文名（操作工单 / 包装工单 …），识别不出为空串 */
    private String orderType;

    /** 产成品名称（工单的 material_desc） */
    private String materialName;

    /** 订单数量合计 */
    private BigDecimal orderQty;

    /** 确认产量合计 */
    private BigDecimal confirmedQty;
}
