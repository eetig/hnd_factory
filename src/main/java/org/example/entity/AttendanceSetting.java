package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤默认时间设置（一人一行）（2026-10-24，变更-171）。
 *
 * <p>使用方 2026-10-24 给的标准作息：
 * <pre>
 *   上班 08:00   午休 12:00-13:30   下班 17:30   ⇒ 一个标准工作日 **8 小时**
 *   休息日加班 08:00-12:00 / 13:30-17:30（和平常一样，只是把午休卡也录进来）
 * </pre>
 *
 * <p>为什么要有这张表：默认值要能改（他厂里作息会变），又不能写死在代码里等发版。
 * 单个用户一行，{@code user_id} 唯一。
 */
@Data
@TableName("attendance_setting")
public class AttendanceSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 上班 */
    private LocalTime workStart;

    /** 下班 */
    private LocalTime workEnd;

    /** 午休开始 */
    private LocalTime lunchStart;

    /** 午休结束 */
    private LocalTime lunchEnd;

    /** 值班单价（元/次），使用方口径：值一次班 50 元 */
    private java.math.BigDecimal dutyRate;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
