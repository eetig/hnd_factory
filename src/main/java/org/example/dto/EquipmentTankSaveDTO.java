package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 储罐新增/编辑（2026-11-09）。
 *
 * <p>几何字段**不在这里**：它们属于图纸（equipment_ledger），罐上只留"选择哪张图纸"。
 * 这是使用方原话「容器我来指定哪张图纸」的直接体现。
 */
@Data
public class EquipmentTankSaveDTO {

    /** null = 新增 */
    private Long id;

    @NotBlank(message = "请填写罐号")
    @Size(max = 64, message = "罐号最多 64 字")
    private String tankCode;

    @NotBlank(message = "请填写罐名")
    @Size(max = 128, message = "罐名最多 128 字")
    private String tankName;

    @NotNull(message = "请选择图纸")
    private Long blueprintId;

    @Size(max = 32, message = "车间最多 32 字")
    private String workshop;

    @Size(max = 32, message = "属地最多 32 字")
    private String location;

    @Size(max = 16, message = "所属最多 16 字")
    private String category;

    @Size(max = 64, message = "物料编码最多 64 字")
    private String materialCode;

    @Size(max = 128, message = "物料名称最多 128 字")
    private String materialName;

    /** t/m3；空表示只算体积 */
    private BigDecimal density;

    private Integer enabled;

    @Size(max = 255, message = "备注最多 255 字")
    private String remark;
}
