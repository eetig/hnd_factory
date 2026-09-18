package org.example.dto;

import lombok.Data;
import org.example.entity.WorkOrder;

/**
 * 新增/编辑工单入参：工单主数据。
 */
@Data
public class WorkOrderSaveDTO {
    private WorkOrder workOrder;   // 工单主数据（新增时 id 为空，编辑时带 id）
}
