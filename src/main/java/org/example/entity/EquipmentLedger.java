package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备台账（静设备）—— 库表 equipment_ledger，见 docs/schema.sql。
 *
 * <p>把线下《设备台账》里的「设备位号 / 设备名称 / 设备规格 / 封头容积 / 每 mm 液位对应量」
 * 落库，供两处使用：① 液位换算质量（月底抄表，见 tank_level_record 的理论质量）；
 * ② 按关键字模糊检索设备（如输入「三甲」，见 {@code GET /api/equipment/search}）。
 *
 * <p>几个刻意的取舍：
 * <ul>
 *   <li>位号、车间取「NOT NULL DEFAULT ''」而不是可空：台账里有整页没有位号的容器
 *       （罐区、水封类），留空串便于统一处理（同 tank_level_record.tank_code）。</li>
 *   <li>规格 / 厚度 / 温度 / 压力一律 varchar：台账里是
 *       「DN1500/DN1400x1200(夹套)」「8mm/10mm」「150/120」「-0.096」这类多工况、带前缀的写法，
 *       拆成数值列反而把信息丢了。</li>
 *   <li>容积(volume) 与换热面积(heatArea) 分两列，不共用一列：源台账用 V= / F= 前缀区分，
 *       合成一列后就分不清单位是 m3 还是 m2。</li>
 *   <li>体积/质量按 mm 计的量用 8 位小数（如 0.000385 m3/mm）：沿用 decimal(18,4) 会被抹成
 *       0.0004，液位换算质量时误差按千分之一放大。</li>
 *   <li>{@code containerType} 是从 spec 派生的**输出**列（含「卧式」为 1 / 含「平底」为 2 /
 *       都不含为 3）：型式在源台账里只是规格括号里的两个字，落成整型后调用方不必各自
 *       写一遍字符串匹配。它由 schema.sql 第 2 部分的回填语句统一算，Java 侧不重算 ——
 *       避免同一规则两处实现后走样。需要改某行的型式，改 spec，不要只改本列。</li>
 * </ul>
 *
 * <p>本表只读（一次性导入后手工维护），不提供管理接口 —— 与 material_master 的约定一致。
 */
@Data
@TableName("equipment_ledger")
public class EquipmentLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String equipmentCode;    // 设备位号（产品编号），源台账缺号时为空串
    private String equipmentName;    // 设备名称（如 三甲粗品罐）
    private String workshop;         // 车间/装置（一车间/三甲车间/四甲车间/罐区/公用工程）
    private String spec;             // 设备规格（公称直径×筒体长度，如 DN1600x2000）
    private Integer containerType;   // 容器类型 1卧式 2平底 3其他（由 spec 派生，见 schema.sql 第 2 部分回填）
    private String thickness;        // 筒体厚度(mm)
    private BigDecimal volume;       // 容积(m3)
    private BigDecimal heatArea;     // 换热面积(m2)
    private BigDecimal headVolume;   // 封头容积(m3)
    private BigDecimal volumePerMm;  // 每mm液位对应体积(m3/mm)
    private BigDecimal massPerMm;    // 每mm液位对应质量(kg/mm)
    private BigDecimal density;      // 介质密度(t/m3)，用于把体积换算成质量
    private String medium;           // 介质（物料名称）
    private String designTemp;       // 操作温度(℃)
    private String designPressure;   // 操作压力(MPa)
    private String sourceSheet;      // 来源台账工作表（可追溯）
    private Integer sourceRow;       // 来源行号（可追溯，也是导入幂等键的一部分）
    private String remark;           // 备注（如「需软件计算」「平底，无封头容积」）
    private Integer enabled;         // 是否启用 1启用 0停用（停用不参与检索）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
