package org.example.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 导入预览结果：taskId + 单据大类 + 总行数 + 预览列表 + 错误行。
 * list 的行结构随 workOrderType 变化：
 *   - 工单汇总：orderNo / materialCode / materialDesc / orderQty / planStartDate / confirmedQty
 *   - 货物移动：订单文件的全部 14 列
 */
@Data
public class WorkOrderImportPreviewVO {
    private String taskId;                                   // 缓存任务ID
    private String workOrderType;                            // 单据大类：工单汇总 / 货物移动
    private int total;                                       // 解析总行数
    private List<Map<String, Object>> list;                  // 预览列表
    private List<WorkOrderImportResultVO.RowError> errorRows; // 错误行
}
