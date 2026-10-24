package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 保存一天的考勤（2026-10-24，变更-171）。
 *
 * <p>按 {@code (userId, workDate)} upsert：同一天再保存就覆盖，不新增行
 * —— 使用方的场景是"事后补录 + 改"，一条"改"不应该变成两条记录。
 */
@Data
public class AttendanceDaySaveDTO {

    @NotNull(message = "日期不能为空")
    private LocalDate workDate;

    /** work / overtime / rest / comp */
    @NotBlank(message = "类型不能为空")
    private String dayType;

    private LocalTime amIn;
    private LocalTime amOut;
    private LocalTime pmIn;
    private LocalTime pmOut;

    private Boolean missAmIn;
    private Boolean missAmOut;
    private Boolean missPmIn;
    private Boolean missPmOut;

    /** 当天是否值班（与白班/加班可并存） */
    private Boolean hasDuty;

    private String note;
}
