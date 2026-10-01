package org.example.dto;

import org.example.entity.MaterialMovement;
import org.example.entity.MaterialPickSummary;
import org.example.entity.MaterialStock;
import org.example.entity.ProductionInbound;
import org.example.entity.WorkOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 导入缓存载体的行数统计。
 *
 * <p>{@code size()} 是「这次有没有数据可存」的唯一判据 —— doSave 里
 * {@code payload.size() == 0} 直接拒绝。每接一个新类型都必须能被算进去：
 * 库存汇总第一次接进来时就漏在这里，前端预览 605 条一切正常、一点「确认导入」就
 * 500「没有可保存的数据」。这条测试把五种类型全钉住。
 */
class WorkOrderImportPayloadTest {

    @Test
    @DisplayName("五种单据类型的 payload 都要被 size() 算到")
    void countsEveryBillType() {
        assertEquals(2, WorkOrderImportPayload
                .ofWorkOrders("工单汇总", List.of(new WorkOrder(), new WorkOrder())).size());

        assertEquals(1, WorkOrderImportPayload
                .ofMovements("货物移动", List.of(new MaterialMovement())).size());

        assertEquals(1, WorkOrderImportPayload
                .ofInbounds("生产入库单", List.of(new ProductionInbound()), List.of(), null).size());

        assertEquals(1, WorkOrderImportPayload
                .ofPickSummaries("领料汇总", List.of(new MaterialPickSummary()), List.of(), null).size());

        assertEquals(3, WorkOrderImportPayload
                .ofStocks("库存汇总", List.of(new MaterialStock(), new MaterialStock(), new MaterialStock()))
                .size());
    }

    @Test
    @DisplayName("空 payload 仍然是 0 —— doSave 要靠它拒绝空导入")
    void emptyPayloadIsZero() {
        assertEquals(0, WorkOrderImportPayload.ofStocks("库存汇总", List.of()).size());
        assertEquals(0, new WorkOrderImportPayload().size());
    }
}
