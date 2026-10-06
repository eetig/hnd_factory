package org.example.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 *   <li>{@code containerType} 是从 spec 派生的**输出**列（含「管束」为 4 / 含「卧式」为 1 /
 *       含「平底」为 2（平底立式）/ 都不含为 3（立式））：型式在源台账里只是规格括号里的两个字，
 *       落成整型后调用方不必各自写一遍字符串匹配。它由 schema.sql 第 2 部分的回填语句算，
 *       Java 侧不重算 —— 避免同一规则两处实现后走样。
 *       <p>⚠️ 2026-10-06 起**该列可空、且回填只填 NULL 的行**：它是「设备数据维护」页可人工改的，
 *       若仍按「每次执行都重算全表」写，维护页刚改的值会被 schema.sql 刷回去。所以人工判定优先于
 *       文本派生 —— 在页面上改过之后，改不改 spec 都不会被覆盖。</li>
 * </ul>
 *
 * <p><b>2026-10-06 起不再只读</b>：原先的约定是「一次性导入后手工维护，不提供管理接口，
 * 与 material_master 一致」，现在多了「设备数据维护」页（使用方要逐个设备对着图纸核对参数）。
 * 写接口一律带 {@code @SaCheckPermission("equipment:edit")}，且 {@code SaTokenConfigure}
 * 那道「写操作仅 admin」的全局闸门也照样管着；**删除仍不提供**（用 enabled 停用）。
 */
@Data
@TableName("equipment_ledger")
public class EquipmentLedger {
    @TableId(type = IdType.AUTO)
    private Long id;

    // ===== 「设备数据维护」页可改的字段（2026-10-06 起本表不再只读）=====
    // ⚠️ 这些字段都标了 `updateStrategy = ALWAYS`，因为默认策略**只写非 null 的字段** ——
    //    于是「把下封头深度清空（表示平底）」「清掉昵称」这类操作会静默不生效：
    //    页面上看着改成功了、库里没动。标上之后 updateById 才会把 NULL 一起写进去。
    //    加/减可维护字段时，这里与 dto/EquipmentSaveDTO、service 的 applyEditableFields
    //    三处要一起改。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String equipmentCode;    // 设备位号（产品编号），源台账缺号时为空串
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String equipmentName;    // 设备名称（如 三甲粗品罐）
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nickname;         // 设备昵称（短名，供下拉与搜索用；可空）
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String workshop;         // 车间/装置（一车间/三甲车间/四甲车间/罐区/公用工程）
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spec;             // 设备规格（公称直径×筒体长度，如 DN1600x2000）
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer containerType;   // 容器类型 1卧式 2平底立式 3立式 4再沸器（可人工改；回填只填 NULL，见 schema.sql）

    // ===== 以下 7 列专供「压力容器体积计算」页（变更-025），详见 schema.sql 的列注释 =====
    // 该页**自己按几何算**体积，不用 head_volume / volume_per_mm 那套线性近似
    // （那套在卧式罐、以及液面落在封头里时不成立）。填齐且 enabled=1 的行才进该页下拉。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal innerDiameter;    // 容器内径(mm)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal shellLength;      // 筒体直段长(mm)，**不含封头直边**
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal straightFlange;   // 封头直边(mm)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal topHeadDepth;     // 上/左封头曲面深度(mm)，标准椭圆 = 内径/4
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal bottomHeadDepth;  // 下/右封头曲面深度(mm)；null = 平底
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String imageFile;            // 底图：内置底图写文件名（vessel.png）、上传的写 /files/xxx.png
    private String imageBounds;          // 底图坐标 JSON（原图像素坐标）；
                                         // **不开放编辑**（它是按底图量的，手填没意义且极易错位）

    private String thickness;        // 筒体厚度(mm)
    private BigDecimal volume;       // 容积(m3)
    private BigDecimal heatArea;     // 换热面积(m2)
    private BigDecimal headVolume;   // 封头容积(m3)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal volumePerMm;  // 每mm液位对应体积(m3/mm)
    private BigDecimal massPerMm;    // 每mm液位对应质量(kg/mm)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal density;      // 介质密度(t/m3)，用于把体积换算成质量
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String medium;           // 介质（物料名称）
    private String designTemp;       // 操作温度(℃)
    private String designPressure;   // 操作压力(MPa)
    private String sourceSheet;      // 来源台账工作表（可追溯）
    private Integer sourceRow;       // 来源行号（可追溯，也是导入幂等键的一部分）
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;           // 备注（如「需软件计算」「平底，无封头容积」）
    private Integer enabled;         // 是否启用 1启用 0停用（停用不参与检索）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
