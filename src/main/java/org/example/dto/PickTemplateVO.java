package org.example.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领料模板（列表展示用，不含文件字节）。
 *
 * <p>{@code columns} 是给人看的：前端要显示「已认出：物料→第7列、单据数量→第10列」，
 * 让使用方一眼确认认列对不对，而不是等到 SAP 里才发现数量写进了别的列。
 */
@Data
public class PickTemplateVO {

    private Long id;

    private String name;

    private String docType;

    private String dept;

    /** 表头行（1 基，给人看） */
    private Integer headerRow;

    /** 列映射说明，如「物料 第7列 · 单据数量 第10列 · 单位 第11列」 */
    private String columns;

    /** 示例行里的工厂/仓库（导出时新行沿用这两个值） */
    private String factory;

    private String warehouse;

    private Integer enabled;

    private LocalDateTime createTime;
}
