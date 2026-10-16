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
    private String nickname;         // 设备昵称（短名，可空）
    private String workshop;         // 车间/装置
    private String spec;             // 设备规格（公称直径×筒体长度）
    private Integer containerType;   // 容器类型 1卧式 2平底 3其他
    // 容器几何参数与底图（变更-025）：供「压力容器体积计算」页按几何精算体积。
    // 该页走的是精简的 /api/equipment/vessels（见 VesselVO），这里一并带上是为了
    // 本 VO「与库表一一对应、不做裁剪」的既有约定（少给一个字段就要再补一次接口）。
    private BigDecimal innerDiameter;    // 容器内径(mm)
    private BigDecimal shellLength;      // 筒体直段长(mm)，不含封头直边
    private BigDecimal straightFlange;   // 封头直边(mm)
    private BigDecimal topHeadDepth;     // 上/左封头曲面深度(mm)
    private BigDecimal bottomHeadDepth;  // 下/右封头曲面深度(mm)；null = 平底
    private String imageFile;            // 底图文件名
    private String imageBounds;          // 底图坐标 JSON（原图像素：图宽高 + 罐体框 + 立式上下切线；见 VesselBounds）
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
    // 「设备数据维护」页要用：列表上显示启停状态、并切换它。
    // 该 VO 的约定是与库表一一对应、不做裁剪，这一列本来就该在（此前漏了）。
    private Integer enabled;         // 是否启用 1启用 0停用

    /**
     * 「不能启用」的原因（**派生字段，不是表里的列**）；可以启用时为 {@code null}。
     *
     * <p>由 {@link org.example.util.VesselEnableGate} 算出。存在它的理由：体积计算页的下拉
     * 只收「有底图 + 有坐标 + 几何齐全」的行，而维护页原先允许把不合规的行点成「启用」——
     * 状态显示成功、下拉里却永远不出现，使用者只会以为页面坏了（变更-081）。
     * 维护页据此把【启用】按钮置灰并原样显示这句话，**规则只有服务端这一份**，前端不重复实现。
     */
    private String enableBlockReason;
}
