package org.example.dto;

import lombok.Data;

/**
 * 导入保存入参（前端以 JSON body 提交）：{ taskId }
 */
@Data
public class WorkOrderImportSaveDTO {
    private String taskId;
}
