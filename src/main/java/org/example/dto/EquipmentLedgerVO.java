package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备台账项（模糊检索一行）。
 *
 * <p>字段与库表 equipment_ledger 一一对应，不做裁剪：调用方既有「按名字找位号」的场景，
 * 也有「拿封头容积 / 每 mm 液位对应量去换算液位」的场景，少给一个字段就要再补一次接口。
 *
 * <p>{@code massPerMm} 是派生量：库里填了就用库里的值；库里没填但密度已知时，
 * 由 {@code volumePerMm × density × 1000} 现算（见 org.example.util.MassPerMmCalculator），
 * 免得同一个量在「有密度」和「没密度」的行上口径不一致。
 */
@Data
public class EquipmentLedgerVO {
    private Long id;
    private String equipmentCode;    // 设备位号（产品编号）
    private String equipmentName;    // 设备名称
    private String workshop;         // 车间/装置
    private String spec;             // 设备规格（公称直径×筒体长度）
    private Integer containerType;   // 容器类型 1卧式 2平底 3其他
    private String thickness;        // 筒体厚度(mm)
    private BigDecimal volume;       // 容积(m3)
    private BigDecimal heatArea;     // 换热面积(m2)
    private BigDecimal headVolume;   // 封头容积(m3)
    private BigDecimal volumePerMm;  // 每mm液位对应体积(m3/mm)
    private BigDecimal massPerMm;    // 每mm液位对应质量(kg/mm)
    private BigDecimal density;      // 介质密度(t/m3)
    private String medium;           // 介质
    private String designTemp;       // 操作温度(℃)
    private String designPressure;   // 操作压力(MPa)
    private String remark;           // 备注
}
