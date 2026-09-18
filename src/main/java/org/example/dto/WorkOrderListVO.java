package org.example.dto;

import lombok.Data;
import org.example.entity.WorkOrder;

import java.util.List;

/**
 * 工单列表项：工单信息 + 图片列表（前端取第一张做缩略图）。
 */
@Data
public class WorkOrderListVO {
    private WorkOrder workOrder;              // 工单信息
    private List<WorkOrderImageVO> imageList; // 图片列表
}
