package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 一个月的考勤（2026-10-24，变更-171）。
 *
 * <p>{@code days} 一定是**整月每一天**（含默认值），手机端与将来的 PC 月历都直接渲染，
 * 不再各自补空白天 —— 两边口径天然一致。
 *
 * <p>统计口径（使用方 2026-10-24 确认）：
 * <ul>
 *   <li>{@code workDays} 出勤天数 —— 工作日且有完整两段的日期数；</li>
 *   <li>{@code overtimeDays} 加班天数 —— 类型=加班且四段都有；</li>
 *   <li>{@code restDays} 休息 / {@code compDays} 调休；</li>
 *   <li>{@code totalMinutes} 总工时 —— 每段时长之和，漏段的那段不计；</li>
 *   <li>{@code missCount} 忘打卡段数；</li>
 *   <li>{@code incompleteDays} 缺段的天数（该录却没录全，提醒去补）。</li>
 * </ul>
 */
@Data
public class AttendanceMonthVO {

    /** 形如 2026-10 */
    private String month;

    private List<AttendanceDayVO> days;

    private int workDays;
    private int overtimeDays;
    private int restDays;
    private int compDays;

    private int totalMinutes;

    private int missCount;

    private int incompleteDays;

    /** 值班次数 */
    private int dutyCount;

    /** 值班费合计（元）= dutyCount × dutyRate */
    private java.math.BigDecimal dutyAmount;

    /** 当前生效的默认时间（手机端「默认时间」弹层直接用它，省一次请求） */
    private AttendanceSettingVO settings;
}
