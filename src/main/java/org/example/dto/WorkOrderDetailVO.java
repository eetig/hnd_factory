package org.example.dto;

import lombok.Data;
import org.example.entity.WorkOrder;

import java.util.List;

/**
 * 工单详情：工单信息 + 图片列表。
 */
@Data
public class WorkOrderDetailVO {
    private WorkOrder workOrder;              // 工单信息
    private List<WorkOrderImageVO> imageList; // 图片列表
}
