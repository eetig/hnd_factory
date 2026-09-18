package org.example.dto;

import lombok.Data;

/**
 * 导入预览结果：taskId + 识别出的工单类型 + 总行数 + 预览列表 + 错误行。
 */
@Data
public class WorkOrderImportPreviewVO {
    private String taskId;                                  // 缓存任务ID
    private String workOrderType;                           // 识别出的工单类型（多种为"混合类型"，未识别为""）
    private int total;                                      // 解析总行数
    private java.util.List<WorkOrderPreviewItemVO> list;    // 预览列表
    private java.util.List<WorkOrderImportResultVO.RowError> errorRows; // 错误行
}
