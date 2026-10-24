package org.example.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 考勤一天（返回给手机端 / 将来的 PC 看板）（2026-10-24，变更-171）。
 *
 * <p>{@code dayType} 是"**生效**的类型"：库里没存这一天时，由
 * {@link org.example.util.AttendanceCalendar#defaultType} 按"周一到周五 + 每月最后一个周六
 * = 工作日；其余周末与法定节假日 = 休息"算出来 —— 手机端不用自己判规则，
 * 也不会出现"App 和小程序算得不一样"。
 *
 * <p>{@code persisted=false} 表示这天还没有人为它落过库，手机端据此只做展示、
 * 不显示"已记录"。
 */
@Data
public class AttendanceDayVO {

    private LocalDate workDate;

    private String dayType;

    /** 这天是否已经在库里（false = 只是默认值） */
    private Boolean persisted;

    private LocalTime amIn;
    private LocalTime amOut;
    private LocalTime pmIn;
    private LocalTime pmOut;

    private Boolean missAmIn;
    private Boolean missAmOut;
    private Boolean missPmIn;
    private Boolean missPmOut;

    /** 当天是否值班（按次计费，与白班/加班可并存） */
    private Boolean hasDuty;

    private String note;
}
