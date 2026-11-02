package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电表台账（2026-11-02，第一阶段：只记录）。
 *
 * <p>使用方口径：「先考虑记录，计算之后再处理」。
 * 所以这里只描述"厂里有哪几块表、归属谁、倍率多少"，不含任何电费字段。
 *
 * <p>关于倍率：截图里 1 号表写着「3000 倍」、14/15 号「5 倍 / 1 倍」——
 * 高压计量与带 CT 的表，读数要乘倍率才是真实电量。倍率放在台账上（而不是抄表记录里），
 * 因为它是表本身的属性；但**抄表时会把当时的倍率算进 usage_qty 并存下来**，
 * 以后有人改了倍率，历史用量不会被改写。
 */
@Data
@TableName("electricity_meter")
public class ElectricityMeter {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 顺序号：截图里从左到右 1~37，稳定不变（插新表不重排） */
    private Integer meterNo;

    /** 电表名称（位置 / 用途） */
    private String meterName;

    /** 柜号 / 变压器（TANJ1、LANE2 等），可为空 */
    private String meterCode;

    /** 归属车间或系统（使用方在页面上改） */
    private String section;

    /** 倍率（CT 变比）：真实电量 = (本次读数 − 上次读数) × 倍率 */
    private Integer multiplier;

    /** 1 启用 0 停用 */
    private Integer enabled;

    private Integer sort;

    private String remark;

    private LocalDateTime createdAt;
}
