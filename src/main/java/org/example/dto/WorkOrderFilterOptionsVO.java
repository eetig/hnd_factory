package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 工单汇总三个筛选弹窗的可选项。
 *
 * <p>改造前这三份候选都是前端从「全量工单」里现算的（{@code allWorkOrders.value.filter(...)}
 * 之后 {@code new Set(...)}）。列表改成分页后前端不再持有全量数据，候选必须由服务端给。
 *
 * <p>候选只按**日期区间**取，不带另外三个筛选项 —— 否则选中「产成品 A」之后，
 * 工单号候选里就只剩 A 的工单，想改选别的得先清空筛选。
 *
 * <p>工单类型**不在这里**：它是工单号前缀的固定字典（{@code WorkOrderTypeEnum} /
 * 前端的 {@code REPORT_ORDER_TYPES}，操作→包装→转桶→返工四类），
 * 哪几类当前有数据可以从 {@link #getOrderNos()} 的前缀推出来，
 * 不必为此多一个「全项目第一份由服务端下发的固定字典」。
 */
@Data
public class WorkOrderFilterOptionsVO {

    /** 该区间内出现过的工单号（倒序，与列表同序） */
    private List<String> orderNos;

    /** 该区间内出现过的产成品名称（物料描述），按中文排序 */
    private List<String> productNames;
}
