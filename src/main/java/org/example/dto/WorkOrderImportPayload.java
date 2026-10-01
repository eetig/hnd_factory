package org.example.dto;

import lombok.Data;
import org.example.entity.MaterialMovement;
import org.example.entity.MaterialPickSummary;
import org.example.entity.MaterialStock;
import org.example.entity.ProductionInbound;
import org.example.entity.WorkOrder;

import java.util.List;

/**
 * 导入缓存载体：单据大类 + 解析后的实体。
 * 工单汇总 -> workOrders；货物移动 -> movements；
 * 生产入库单 -> inbounds；领料汇总 -> pickSummaries。
 * 含内嵌图片的类型另存原始文件字节，保存阶段提取图片上传 MinIO。
 */
@Data
public class WorkOrderImportPayload {
    private String billType;                        // 工单汇总 / 货物移动 / 生产入库单 / 领料汇总 / 库存汇总
    private List<WorkOrder> workOrders;             // 工单汇总数据
    private List<MaterialMovement> movements;       // 货物移动数据
    private List<ProductionInbound> inbounds;       // 生产入库单数据
    private List<MaterialPickSummary> pickSummaries;// 领料汇总数据
    private List<MaterialStock> stocks;             // 库存汇总数据（已按「工厂+物料+存储地点」合并相加）
    private List<String> documentIds;               // 每行单据图片的 DISPIMG ID（与 inbounds/pickSummaries 对齐）
    private byte[] sourceBytes;                     // 原始文件字节（用于提取内嵌图片）

    public static WorkOrderImportPayload ofStocks(String billType, List<MaterialStock> stocks) {
        WorkOrderImportPayload p = new WorkOrderImportPayload();
        p.setBillType(billType);
        p.setStocks(stocks);
        return p;
    }

    public static WorkOrderImportPayload ofWorkOrders(String billType, List<WorkOrder> workOrders) {
        WorkOrderImportPayload p = new WorkOrderImportPayload();
        p.setBillType(billType);
        p.setWorkOrders(workOrders);
        return p;
    }

    public static WorkOrderImportPayload ofMovements(String billType, List<MaterialMovement> movements) {
        WorkOrderImportPayload p = new WorkOrderImportPayload();
        p.setBillType(billType);
        p.setMovements(movements);
        return p;
    }

    public static WorkOrderImportPayload ofInbounds(String billType, List<ProductionInbound> inbounds,
                                                    List<String> documentIds, byte[] sourceBytes) {
        WorkOrderImportPayload p = new WorkOrderImportPayload();
        p.setBillType(billType);
        p.setInbounds(inbounds);
        p.setDocumentIds(documentIds);
        p.setSourceBytes(sourceBytes);
        return p;
    }

    public static WorkOrderImportPayload ofPickSummaries(String billType, List<MaterialPickSummary> pickSummaries,
                                                         List<String> documentIds, byte[] sourceBytes) {
        WorkOrderImportPayload p = new WorkOrderImportPayload();
        p.setBillType(billType);
        p.setPickSummaries(pickSummaries);
        p.setDocumentIds(documentIds);
        p.setSourceBytes(sourceBytes);
        return p;
    }

    public int size() {
        // ⚠️ 这里必须「所有列表求和」，不能写成 if 链。
        // 起初是 if 链（workOrders → movements → inbounds → pickSummaries），
        // 加「库存汇总」时漏补一支，于是 doSave 里的 `payload.size() == 0` 直接判成
        // 「没有可保存的数据」，前端预览好好的、一点确认导入就 500。
        // 求和写法加新类型时不用再动这里，漏不了。
        int total = 0;
        if (workOrders != null) {
            total += workOrders.size();
        }
        if (movements != null) {
            total += movements.size();
        }
        if (inbounds != null) {
            total += inbounds.size();
        }
        if (pickSummaries != null) {
            total += pickSummaries.size();
        }
        if (stocks != null) {
            total += stocks.size();
        }
        return total;
    }
}
