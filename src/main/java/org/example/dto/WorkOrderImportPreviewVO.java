package org.example.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 导入预览结果：taskId + 单据大类 + 总行数 + 预览列表 + 错误行 + 待上传图片清单。
 *
 * list 的行结构随 workOrderType 变化：
 *   - 工单汇总：orderNo / materialCode / materialDesc / orderQty / planStartDate / confirmedQty
 *   - 货物移动：订单文件的全部 14 列
 *   - 生产入库单 / 领料汇总：额外含 dispimgId（该行单据图 ID）、isDuplicate、isUpdate
 */
@Data
public class WorkOrderImportPreviewVO {
    private String taskId;                                   // 缓存任务ID
    private String workOrderType;                            // 单据大类
    private int total;                                       // 解析总行数
    private List<Map<String, Object>> list;                  // 预览列表
    private List<WorkOrderImportResultVO.RowError> errorRows; // 错误行

    /**
     * 本次导入【需要上传的单据图片】ID 列表（已去重）。
     *
     * 前端可在解析阶段剥离 xl/media/* 后只传 0.13MB 的数据部分（见《前后端改动统筹》变更-002），
     * 确认导入时再按本字段从本地原文件里取出对应图片一并提交到 /import/save。
     * 全部行为重复行时该列表为空 —— 此时无需上传任何图片。
     */
    private List<String> needImageIds;
}
