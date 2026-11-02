package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电表抄表记录（2026-11-02，第一阶段：只记录）。
 *
 * <h2>为什么同时存 reading 和 usage_qty</h2>
 * 抄表日期**不固定**（使用方：「基本是每周五抄，但由于工作原因，有时候会在别的时间抄」）。
 * 用量 = (本次读数 − 上次读数) × 倍率，而"上次读数"会随着补录旧记录而改变。
 * 把算好的 usage_qty 存下来有两个好处：
 * <ol>
 *   <li>补录一条旧记录时，只需要重算**它到下一次之间的那一个区间**，不用全表重跑；</li>
 *   <li>留痕：prev_reading 也存，事后能解释"这个用量是怎么来的"。</li>
 * </ol>
 *
 * <h2>唯一键</h2>
 * (meter_id, read_date) 唯一：同一块表同一天只能有一条，重复保存就是覆盖。
 * 抄表现场最容易出的错就是"这块表我今天是不是已经抄过了"，用数据库兜住比界面提示可靠。
 */
@Data
@TableName("electricity_reading")
public class ElectricityReading {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long meterId;

    /** 抄表日期 */
    private LocalDate readDate;

    /** 本次原始读数 */
    private BigDecimal reading;

    /** 抄表时用的上次读数（留痕） */
    private BigDecimal prevReading;

    /** (本次读数 − 上次读数) × 倍率 */
    private BigDecimal usageQty;

    /** 抄表照片 */
    private String imagePath;

    private String note;

    private String createdBy;

    private LocalDateTime createdAt;
}
