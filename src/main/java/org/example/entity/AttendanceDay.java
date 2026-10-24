package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 个人考勤一天一条（2026-10-24，变更-171）。
 *
 * <p>使用方的用法是**事后录入 + 和人事对账**（原文：「我不在这打卡，我是要记录打卡信息，
 * 以便于和人事对账」），所以这张表是"考勤台账"，不是打卡流水：
 * <ul>
 *   <li>一人一天<b>唯一</b>一条（{@code uk_user_date}）—— 一天多段用同一行的四个时间列表达；</li>
 *   <li>四个时间列对应两种形态：工作日只用 {@code amIn/amOut}（上班/下班），
 *       休息日加班用全部四列（上午上班/午休/下午上班/下午下班）；</li>
 *   <li>忘打卡按<b>每段独立</b>标记（使用方 2026-10-24 确认：迟到早退不单独算，
 *       记成"忘打卡"即可）—— 对账时人事就是按段问的；</li>
 *   <li>日期类型 {@code dayType}：work 工作日 / overtime 休息日加班 / rest 休息 / comp 调休。</li>
 * </ul>
 */
@Data
@TableName("attendance_day")
public class AttendanceDay {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户（Sa-Token 的 loginId）。目前只记自己，预留字段以便将来代录 */
    private Long userId;

    private LocalDate workDate;

    /** work / overtime / rest / comp，取值见 AttendanceCalendar */
    private String dayType;

    /** 工作日：上班；加班：上午上班 */
    private LocalTime amIn;

    /** 工作日：下班；加班：午休（这一段记的是"上午下班离开"的时刻） */
    private LocalTime amOut;

    /** 加班：下午上班 */
    private LocalTime pmIn;

    /** 加班：下午下班 */
    private LocalTime pmOut;

    private Boolean missAmIn;
    private Boolean missAmOut;
    private Boolean missPmIn;
    private Boolean missPmOut;

    /**
     * 当天是否值班（夜班 20:00-次日 08:00）。
     *
     * <p>使用方 2026-10-24：「化工厂有值班要求……按次数算，值一次班 50 块钱」，
     * 并且值班可以和当天的白班/加班**同时存在**（白天上班、晚上再值班），
     * 所以它是同一条记录上的独立标记，不是第五种日期类型，也不记小时。
     */
    private Boolean hasDuty;

    private String note;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
