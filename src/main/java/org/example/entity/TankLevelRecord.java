package org.example.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    /**
     * 容器编号（设备位号）。界面自 2026-10-02 起不再让人填，因此**绝大多数行是 null**。
     *
     * <p>⚠️ {@code insertStrategy = ALWAYS} 是 2026-10-20（变更-127）特意加的，别删：
     * MyBatis-Plus 默认 NOT_NULL 会**跳过 null 字段**，于是 {@code tank_code} 根本不进
     * INSERT 语句、落库取的是**列默认值**。生产库的列默认值当时是 {@code ''}（本地是
     * NULL），于是新记录全成了空串；而唯一键 {@code uk_date_tank(record_date, tank_code)}
     * 对 NULL 不约束、对空串约束 —— 结果就是「同一天只能存一条」，第二条起全被拒
     * （2026-10-20 手机端实测：一天记第二台容器必报"该记录日期已存在…"）。
     * 现在显式写入 null，就不再依赖任何库默认值，DDL 没跑过也不会再现这个坑。
     */
    @TableField(value = "tank_code", insertStrategy = FieldStrategy.ALWAYS)
    private String tankCode;
    private BigDecimal levelValue;      // 容器液位
    private BigDecimal theoreticalWeight; // 理论质量(KG)
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
