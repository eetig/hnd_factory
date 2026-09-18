package org.example.dto;

import lombok.Data;
import org.example.entity.WorkOrder;

/**
 * 导入预览行：行号 + 工单号 + 识别出的类型 + 解析后的完整数据。
 */
@Data
public class WorkOrderPreviewRowVO {
    private Integer rowNum;          // 行号（从1开始）
    private String orderNo;          // 工单号
    private String workOrderType;    // 识别出的工单类型（操作工单/包装工单...）
    private WorkOrder workOrder;     // 解析后的完整数据
}
