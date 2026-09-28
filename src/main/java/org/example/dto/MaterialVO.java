package org.example.dto;

import lombok.Data;

/**
 * 物料主数据项（前端只需这四项：显示、回填编码、带出单位）。
 */
@Data
public class MaterialVO {
    private String code;   // 物料编码
    private String name;   // 物料名称
    private String spec;   // 规格
    private String unit;   // 单位
}
