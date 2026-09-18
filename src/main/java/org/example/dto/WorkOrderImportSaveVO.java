package org.example.dto;

import lombok.Data;

/**
 * 工单保存结果：新增/更新条数。
 */
@Data
public class WorkOrderImportSaveVO {
    private int insertCount;   // 新增条数
    private int updateCount;   // 更新条数
    private int total;         // 总条数
}
