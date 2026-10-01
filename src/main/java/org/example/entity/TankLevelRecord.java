package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 月底车间各储罐液位记录（库表 tank_level_record，见 docs/schema.sql）。
 *
 * <p>每月月底下午 3 点抄录一次，一个时点一行（容器 × 物料）。
 * 「理论质量」由液位换算，与实测重量有差异，以实测为准。
 *
 * <p>图据（可多张）在子表 {@link TankLevelImage} 里，本表不再存文件名 ——
 * 变更-008 时是这里的 file_name 单列（一条记录一张），变更-011 换成子表。
 */
@Data
@TableName("tank_level_record")
public class TankLevelRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate recordDate;       // 记录日期（每月月底）
    private String location;            // 属地
    private String category;            // 所属：产品 / 原料
    private String materialCode;        // 物料编码
    private String materialName;        // 物料名称
    private String tankName;            // 容器名称（储罐号）
    private String tankCode;            // 容器编号（设备位号，与记录日期构成唯一键 uk_date_tank）
    private BigDecimal levelValue;      // 容器液位
    private BigDecimal theoreticalWeight; // 理论质量(KG)
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
