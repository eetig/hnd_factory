package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 月底储罐液位记录 —— 新增/编辑的入参。
 *
 * <p>{@code id} 为空表示新增，有值表示编辑该行：两种操作在界面上是同一个行内表单的
 * 两种状态，故共用一个 DTO 与一个接口，不拆成 add / update 两个。
 *
 * <p>数值列用 BigDecimal 而不是 String：液位与理论质量是要参与后续换算的量，
 * 在入口处就定成十进制类型，免得后面每处再解析一遍、各自处理一次解析失败。
 *
 * <p>不含 {@code fileName}：图据走独立的上传/删除接口，不随表单提交 ——
 * 表单里放文件名的话，一次「保存」就会把刚传上去的图覆盖成表单里的旧值。
 */
@Data
public class TankLevelSaveDTO {
    private Long id;                       // 空=新增，有值=编辑
    private LocalDate recordDate;          // 记录日期（每月月底）
    private String location;               // 属地
    private String category;               // 所属：产品 / 原料
    private String materialCode;           // 物料编码（可空）
    private String materialName;           // 物料名称
    private String tankName;               // 容器名称（储罐号）
    private String tankCode;               // 容器编号（设备位号，与记录日期联合唯一）
    private BigDecimal levelValue;         // 容器液位(mm)
    private BigDecimal theoreticalWeight;  // 理论质量(kg)
}
