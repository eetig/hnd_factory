package org.example.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 日期区间查询条件（列表、选项、聚合三类接口的公共部分）。
 *
 * <p>单独抽出来是为了让「不需要分页」的接口（筛选候选、按物料汇总）不必被迫
 * 接收一对用不上的 pageNum / pageSize —— 那些接口返回的不是记录页，
 * 带上分页参数只会让人以为它们会分页。
 */
@Data
public class DateRangeQueryDTO {

    /** 起始日期 yyyy-MM-dd，空 = 不限 */
    private String startDate;

    /** 结束日期 yyyy-MM-dd，空 = 不限 */
    private String endDate;

    /** 起始日期；缺省或格式不对当「不限」 */
    public LocalDate parsedStartDate() {
        return parseDate(startDate);
    }

    /** 结束日期；缺省或格式不对当「不限」 */
    public LocalDate parsedEndDate() {
        return parseDate(endDate);
    }

    /**
     * 日期容错：缺失或格式不对一律当「不限制」，而不是抛异常。
     *
     * <p>与 {@code TankLevelController#parseDate}、{@code StatsController#parseDate} 同口径 ——
     * 前端已按 yyyy-MM-dd 传值，这里兜的是手工调接口与历史脏值。
     * 注意**不要**改成抛异常：日期筛选框被清空时前端会传空串，
     * 那种情况预期就是「不限」，报 400 会让「清空日期」直接变成报错。
     */
    private static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
