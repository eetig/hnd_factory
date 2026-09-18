package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 导入分页预览结果。
 */
@Data
public class WorkOrderPreviewVO {
    private long page;                        // 当前页
    private long size;                        // 每页条数
    private long total;                       // 总行数
    private long totalPage;                   // 总页数
    private List<WorkOrderPreviewRowVO> rows; // 当前页数据
}
