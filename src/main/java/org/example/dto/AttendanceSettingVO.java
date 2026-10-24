package org.example.dto;

import lombok.Data;

import java.time.LocalTime;

/**
 * 考勤默认时间（2026-10-24，变更-171）。
 *
 * <p>默认值 = 使用方给的标准作息：08:00 上班、12:00–13:30 午休、17:30 下班。
 * 一个标准工作日按"下班−上班−午休"算 **8 小时**。
 */
@Data
public class AttendanceSettingVO {

    private LocalTime workStart;
    private LocalTime workEnd;
    private LocalTime lunchStart;
    private LocalTime lunchEnd;

    /** 值班单价（元/次），默认 50 */
    private java.math.BigDecimal dutyRate;

    /** 一个标准工作日的有效工时（分钟）：下班−上班−午休，默认 480 */
    private int standardMinutes;

    public static AttendanceSettingVO defaults() {
        AttendanceSettingVO vo = new AttendanceSettingVO();
        vo.setWorkStart(LocalTime.of(8, 0));
        vo.setWorkEnd(LocalTime.of(17, 30));
        vo.setLunchStart(LocalTime.of(12, 0));
        vo.setLunchEnd(LocalTime.of(13, 30));
        vo.setDutyRate(new java.math.BigDecimal("50.00"));
        vo.setStandardMinutes(480);
        return vo;
    }
}
