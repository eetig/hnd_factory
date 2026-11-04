package org.example.electricity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电费月度 12 列定义与计算（2026-11-04 抽出）。
 *
 * <h2>为什么抽出来</h2>
 * {@code ElectricityBillController}（预提单）与 {@code ElectricityMonthlyController}（分类汇总）
 * 原本各复制了一份"哪几块表 → 哪一列"的映射。两份映射必须逐字一致，否则同一个月的两张表会打架，
 * 而这种不一致在测试里很难被发现（两处都"自洽"）。抽成一个类，只留一份真相。
 *
 * <h2>列定义（使用方 2026-11-04 给的）</h2>
 * <ul>
 *   <li>总电量 = 1 号高压计量表</li>
 *   <li>公用工程 = 3 + 20 + 28 + 33</li>
 *   <li>乙炔 = 25 + 26 + 27</li>
 *   <li>1# = 16 + 17 + 18 + 19 + 29</li>
 *   <li>2# = 6~11 + 150车间 + 30 + 31（150车间不编号，用内部顺序 38 定位）</li>
 *   <li>4# = 12 + 13 + 14 + 15 + 32</li>
 *   <li>中控 35 · 污水 36 · 办公 37</li>
 *   <li><b>其它 = 总电量 − 上面八项</b>（倒挤，所以没点名的表都在这一列）</li>
 * </ul>
 */
public final class ElectricityColumns {

    private ElectricityColumns() {
    }

    /** 列名 → 参与该列的 meterNo（顺序即显示顺序；不含"其它"） */
    public static final Map<String, List<Integer>> COLUMNS = new LinkedHashMap<>();

    /** 参与"其它"倒挤的八列 */
    public static final List<String> BALANCE_COLUMNS = List.of(
            "公用工程", "乙炔车间", "1#厂房", "2#厂房", "4#厂房", "中央控制室", "污水区", "办公楼");

    /** 预提的 4 组（2026-11-04）：组名 → 由哪些月度列相加 */
    public static final Map<String, List<String>> BILL_GROUPS = new LinkedHashMap<>();

    static {
        COLUMNS.put("总电量", List.of(1));
        COLUMNS.put("公用工程", List.of(3, 20, 28, 33));
        COLUMNS.put("乙炔车间", List.of(25, 26, 27));
        COLUMNS.put("1#厂房", List.of(16, 17, 18, 19, 29));
        // 38 = 150车间（它不编号，1AN28 是柜号）
        COLUMNS.put("2#厂房", List.of(6, 7, 8, 9, 10, 11, 38, 30, 31));
        COLUMNS.put("4#厂房", List.of(12, 13, 14, 15, 32));
        COLUMNS.put("中央控制室", List.of(35));
        COLUMNS.put("污水区", List.of(36));
        COLUMNS.put("办公楼", List.of(37));

        BILL_GROUPS.put("公用工程", List.of("公用工程", "中央控制室", "污水区", "办公楼", "其它"));
        BILL_GROUPS.put("一车间", List.of("2#厂房", "4#厂房"));
        BILL_GROUPS.put("二车间", List.of("1#厂房"));
        BILL_GROUPS.put("乙炔车间", List.of("乙炔车间"));
    }

    /**
     * 一期（或任意一段汇总）的 12 列数值，含倒挤出来的"其它"。
     *
     * @param usage meterNo → 用量
     */
    public static Map<String, BigDecimal> compute(Map<Integer, BigDecimal> usage) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : COLUMNS.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Integer no : entry.getValue()) {
                sum = sum.add(usage.getOrDefault(no, BigDecimal.ZERO));
            }
            values.put(entry.getKey(), sum.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal others = values.getOrDefault("总电量", BigDecimal.ZERO);
        for (String column : BALANCE_COLUMNS) {
            others = others.subtract(values.getOrDefault(column, BigDecimal.ZERO));
        }
        values.put("其它", others.setScale(2, RoundingMode.HALF_UP));
        return values;
    }
}
