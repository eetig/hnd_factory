package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工单导入结果：成功/新增/更新条数 + 错误行清单 + 类型识别汇总。
 */
@Data
public class WorkOrderImportResultVO {
    private Integer total;                      // 解析总行数
    private Integer successCount;               // 成功条数
    private Integer insertCount;                // 新增条数
    private Integer updateCount;                // 更新条数
    private Integer errorCount;                 // 错误行数
    private List<RowError> errorList;           // 错误行号 + 错误信息
    private Map<String, Integer> typeSummary;   // 工单类型识别结果汇总

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        private Integer row;    // 行号（从1开始）
        private String msg;     // 错误说明
    }
}
