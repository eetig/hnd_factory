package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单列表 / 工单派生的聚合查询条件。
 *
 * <p>三个筛选项与前端「工单汇总」面板表头上的三个筛选弹窗一一对应：
 * 产成品（{@code materialDesc}）、工单类型（{@code type}）、工单号（{@code orderNo}）。
 *
 * <p>日期筛的是**基本开始日期**（{@code plan_start_date}）—— 与改造前前端
 * {@code getOrderDate()} 取 {@code planStartDate} 的口径一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderQueryDTO extends PageQueryDTO {

    /**
     * 工单类型标识（typeKey）：{@code operate} / {@code package} / {@code transfer} / {@code rework}。
     *
     * <p>沿用服务端既有约定（{@code WorkOrderTypeEnum.prefixOf}）——**不是**中文标签。
     * 前端要显示「操作工单」这类中文，请在拿到类型候选时一并取标签（见
     * {@link WorkOrderFilterOptionsVO#getOrderTypes()}），别把中文当参数传回来。
     */
    private String type;

    /** 产成品名称（物料描述），精确匹配 —— 与筛选弹窗语义一致 */
    private String materialDesc;

    /** 工单号，精确匹配 */
    private String orderNo;
}
