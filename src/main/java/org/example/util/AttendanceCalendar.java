package org.example.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 考勤默认日历（2026-10-24，变更-171）。
 *
 * <p>使用方要的是「手机上一勾就完事」：默认规则**自动把整月填好**，他只改例外
 * （原文：「周一至周五，以及每个月最后一周的周六，默认正常工作日；周六周日，
 * 以及法定节假日默认休息日，都是默认给我填好，免得我手动去填」）。
 *
 * <h2>规则（按优先级从高到低）</h2>
 * <ol>
 *   <li><b>法定放假</b> → 休息日（{@link #HOLIDAY_2026}）</li>
 *   <li><b>法定调休上班</b> → 工作日（{@link #MAKEUP_WORKDAY_2026}）——这条必须压过"周末=休息"</li>
 *   <li>周一~周五 → 工作日</li>
 *   <li>每月<b>最后一周的周六</b> → 工作日</li>
 *   <li>其余 → 休息日</li>
 * </ol>
 *
 * <h2>数据来源（不猜）</h2>
 * 2026 年的放假与调休来自国务院办公厅《关于2026年部分节假日安排的通知》
 * （国办发明电〔2025〕7号，{@code https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm}）：
 * <pre>
 *   元旦   1/1(四)-1/3(六) 放假；1/4(日) 上班
 *   春节   2/15(日)-2/23(一) 放假；2/14(六)、2/28(六) 上班
 *   清明   4/4(六)-4/6(一) 放假
 *   劳动   5/1(五)-5/5(二) 放假；5/9(六) 上班
 *   端午   6/19(五)-6/21(日) 放假
 *   中秋   9/25(五)-9/27(日) 放假
 *   国庆   10/1(四)-10/7(三) 放假；9/20(日)、10/10(六) 上班
 * </pre>
 * ⚠️ 每年国务院会发新通知 —— 明年请**照这份格式补一组常量**，不要靠"猜节假日"。
 * 表里查不到年份时，只按"周末 + 最后一周周六"判，不会误报。
 */
public final class AttendanceCalendar {

    private AttendanceCalendar() {
    }

    /** 日期类型：工作日（默认 2 段） */
    public static final String TYPE_WORK = "work";
    /** 日期类型：休息日加班（4 段）——默认不会自动给，需要人手动改 */
    public static final String TYPE_OVERTIME = "overtime";
    /** 日期类型：休息（不录时间） */
    public static final String TYPE_REST = "rest";
    /** 日期类型：调休（工作日被公司安排休息，不录时间，但统计里单独算） */
    public static final String TYPE_COMP = "comp";

    /** 2026 年法定放假日（区间，含首尾） */
    private static final Map<Integer, Set<LocalDate>> HOLIDAY_2026 = Map.of(
            2026, ranges(
                    "2026-01-01", "2026-01-03",
                    "2026-02-15", "2026-02-23",
                    "2026-04-04", "2026-04-06",
                    "2026-05-01", "2026-05-05",
                    "2026-06-19", "2026-06-21",
                    "2026-09-25", "2026-09-27",
                    "2026-10-01", "2026-10-07"
            ));

    /** 2026 年法定调休上班日（周末但要上班） */
    private static final Map<Integer, Set<LocalDate>> MAKEUP_WORKDAY_2026 = Map.of(
            2026, dates(
                    "2026-01-04",
                    "2026-02-14", "2026-02-28",
                    "2026-05-09",
                    "2026-09-20", "2026-10-10"
            ));

    /**
     * 某天的默认类型。
     *
     * @param date 日期
     * @return {@link #TYPE_WORK} 或 {@link #TYPE_REST}
     */
    public static String defaultType(LocalDate date) {
        int year = date.getYear();
        Set<LocalDate> holidays = HOLIDAY_2026.getOrDefault(year, Set.of());
        Set<LocalDate> makeup = MAKEUP_WORKDAY_2026.getOrDefault(year, Set.of());

        if (holidays.contains(date)) {
            return TYPE_REST;
        }
        if (makeup.contains(date)) {
            return TYPE_WORK;
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
            return TYPE_WORK;
        }
        if (dow == DayOfWeek.SATURDAY && isLastSaturdayOfMonth(date)) {
            return TYPE_WORK;
        }
        return TYPE_REST;
    }

    /** 是不是当月**最后一个**周六（"每月最后一周的周六上班"这条） */
    public static boolean isLastSaturdayOfMonth(LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            return false;
        }
        LocalDate next = date.plusWeeks(1);
        return next.getMonthValue() != date.getMonthValue();
    }

    /** 该类型要不要录时间（工作日 / 加班要，休息 / 调休不要） */
    public static boolean needsTimes(String dayType) {
        return TYPE_WORK.equals(dayType) || TYPE_OVERTIME.equals(dayType);
    }

    /** 该类型默认几段（工作日 2 段：上班/下班；加班 4 段：上午上班/午休/下午上班/下午下班） */
    public static int segments(String dayType) {
        if (TYPE_WORK.equals(dayType)) {
            return 2;
        }
        if (TYPE_OVERTIME.equals(dayType)) {
            return 4;
        }
        return 0;
    }

    private static Set<LocalDate> ranges(String... pairs) {
        Set<LocalDate> out = new TreeSet<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            LocalDate from = LocalDate.parse(pairs[i]);
            LocalDate to = LocalDate.parse(pairs[i + 1]);
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                out.add(d);
            }
        }
        return out;
    }

    private static Set<LocalDate> dates(String... days) {
        Set<LocalDate> out = new TreeSet<>();
        for (String d : days) {
            out.add(LocalDate.parse(d));
        }
        return out;
    }

    /** 整月的天数（给 service 循环用；避免各处重复 YearMonth 逻辑） */
    public static int daysInMonth(YearMonth month) {
        return month.lengthOfMonth();
    }
}
