package org.example.util;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Excel 单元格取值的**唯一口径**。
 *
 * <p>这些规则原先在 5 个 {@code *ImportUtil}、{@code MaterialMovementServiceImpl} 与
 * {@link DocumentDiffPlanner} 里各抄一份（{@code trim} 6 份、{@code parseDecimal} 6 份、
 * {@code parseDate} 6 份、{@code parseInt} 5 份），并且已经开始**静默分叉**——同一份
 * Excel 在不同单据类型里得到不同结果：
 *
 * <ul>
 *   <li>{@code parseDecimal}：只有库存汇总那份剥全角逗号「，」，其余只剥半角。于是
 *       {@code "1，000.5"} 在库存汇总解析成 1000.5，在领料汇总报「领料数量无法解析」整行失败。</li>
 *   <li>{@code parseInt}：工单 / 货物移动用 {@code Integer.parseInt}（{@code "5.0"} 直接失败），
 *       领料 / 入库用 {@code (int) Double.parseDouble}（{@code "5.0"} → 5）。同一个序号列，
 *       两类单据行为不同。</li>
 * </ul>
 *
 * <p>此处统一取两者中**更宽容**的一侧：能解析出来的数字不再因为写法差异被当成脏数据。
 * 口径由 {@code ExcelValueParserTest} 钉住。
 */
public final class ExcelValueParser {

    private ExcelValueParser() {
    }

    /** SAP 导出与本系统模板出现过的日期写法；最后一档兜 Excel 日期序列号（如 46202） */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    /**
     * 纯去首尾空白；null 仍返回 null。
     *
     * <p>空串**不**转成 null —— 交给调用方的 {@code StringUtils.hasText} 判断，
     * 这条语义与原先 6 份拷贝完全一致，迁移时不改变任何既有行为。
     */
    public static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /**
     * 整数列（序号、项目行号…）。
     *
     * <p>按 double 解析后取整：Excel 经常把整数列写成 {@code 5.0}，原先用
     * {@code Integer.parseInt} 的两份会把它当脏数据。解析不出返回 null，由调用方的
     * 校验给出「xxx无法解析」的业务文案。
     */
    public static Integer parseInt(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 数量 / 金额列。
     *
     * <p>半角逗号、全角逗号、空格一律剥掉（千分位在不同来源里写法不同）。
     */
    public static BigDecimal parseDecimal(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim().replace(",", "").replace("，", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 日期列。
     *
     * <p>先按 {@link #DATE_FORMATTERS} 逐档尝试，全不匹配时再把纯数字当 Excel 序列号
     * （以 1899-12-30 为起点，与 Excel 自身的闰年兼容处理一致）。解析不出返回 null。
     */
    public static LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String v = s.trim();
        for (DateTimeFormatter f : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // 换下一档格式
            }
        }
        try {
            long serial = (long) Double.parseDouble(v);
            return LocalDate.of(1899, 12, 30).plusDays(serial);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
