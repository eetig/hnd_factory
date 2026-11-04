package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.Result;
import org.example.entity.ElectricityMeter;
import org.example.entity.ElectricityReading;
import org.example.exception.BusinessException;
import org.example.mapper.ElectricityMeterMapper;
import org.example.mapper.ElectricityReadingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 月度汇总表（2026-11-04，照使用方 Excel 的 12 列）。
 *
 * <h2>使用方给的列定义（原话）</h2>
 * <blockquote>
 * 总电量为 1，公用工程为 3+20+28+33，乙炔是 25+26+27，1# 是 16+17+18+19+29，
 * 2# 是 6+7+8+9+10+11+1AN28+30+31，4# 是 12+13+14+15+32，中央控制室是 35，
 * 污水区是 36，办公楼是 37，
 * 其他是（总电量-公用工程-乙炔-1#-2#-4#-中央控制室-污水区-办公楼）。
 * 2#空压机（备电）是 33。1#空压机是 5 。2#空压机和 1#空压机是用于查看运转情况
 * </blockquote>
 *
 * <h2>三个要点</h2>
 * <ol>
 *   <li><b>其它是倒挤的</b>：不是某几块表相加，而是"总电量减去其余各列"。
 *       所以这一列天然会把没点名的表（冷冻机、罐区泵、仓库配电、机柜间、自控消防、
 *       变压器…）都吸进去 —— 这是使用方的口径，不是遗漏。</li>
 *   <li><b>备电与 1#空压机是"查看"列</b>：只显示，不进上面任何一项，
 *       否则会重复计量（33 号已经在公用工程里算过一次）。</li>
 *   <li><b>占比的分母是总电量（1 号表）</b>，不是各列之和 ——
 *       因为"其它"就是按总电量倒挤的，这样各列占比加起来正好 100%。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/electricity")
public class ElectricityMonthlyController {

    @Autowired
    private ElectricityMeterMapper meterMapper;

    @Autowired
    private ElectricityReadingMapper readingMapper;

    /** 列定义：名称 → 参与该列的 meterNo（顺序即显示顺序） */
    private static final Map<String, List<Integer>> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put("总电量", List.of(1));
        COLUMNS.put("公用工程", List.of(3, 20, 28, 33));
        COLUMNS.put("乙炔车间", List.of(25, 26, 27));
        COLUMNS.put("1#厂房", List.of(16, 17, 18, 19, 29));
        /*
         * 2#厂房 = 6~11 + 150车间 + 30 + 31
         * 150车间在表里**不编号**（1AN28 是柜号不是编号），所以这里用它的内部顺序 38 定位。
         * 2026-11-04 重排编号后：28=低压循环泵 · 29=1#厂房(1ANE3) · 30=2#厂房(1ANE3) ·
         * 31=2#厂房(1ANE4) · 32=4#厂房(1ANE5) · 33=空压制氮 · 34=自控消防 · 35=中控 · 36=污水 · 37=办公楼
         */
        COLUMNS.put("2#厂房", List.of(6, 7, 8, 9, 10, 11, 38, 30, 31));
        COLUMNS.put("4#厂房", List.of(12, 13, 14, 15, 32));
        COLUMNS.put("中央控制室", List.of(35));
        COLUMNS.put("污水区", List.of(36));
        COLUMNS.put("办公楼", List.of(37));
        // 以下两列只用于"查看运转情况"，不参与合计
        COLUMNS.put("2#空压机(备电)", List.of(33));
        COLUMNS.put("1#空压机", List.of(5));
    }

    /** 参与"其它"倒挤的列（不含备电、1#空压机、总电量本身） */
    private static final List<String> BALANCE_COLUMNS = List.of(
            "公用工程", "乙炔车间", "1#厂房", "2#厂房", "4#厂房", "中央控制室", "污水区", "办公楼");

    @GetMapping("/monthly")
    public Result<Map<String, Object>> monthly(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String month) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }

        LocalDate start;
        LocalDate end;
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            start = LocalDate.parse(from);
            end = LocalDate.parse(to);
        } else if (month != null && !month.isBlank()) {
            start = LocalDate.parse(month + "-01");
            end = start.withDayOfMonth(start.lengthOfMonth());
        } else {
            throw new BusinessException("要么给 from/to，要么给 month");
        }

        List<ElectricityMeter> meters = meterMapper.selectList(
                new LambdaQueryWrapper<ElectricityMeter>().orderByAsc(ElectricityMeter::getMeterNo));
        Map<Integer, Long> idByNo = new LinkedHashMap<>();
        for (ElectricityMeter meter : meters) {
            idByNo.put(meter.getMeterNo(), meter.getId());
        }

        // 取数：往前 45 天，为了拿到"区间起点之前最后一次抄表"作为第一期的起点
        List<ElectricityReading> readings = readingMapper.selectList(new LambdaQueryWrapper<ElectricityReading>()
                .ge(ElectricityReading::getReadDate, start.minusDays(45))
                .le(ElectricityReading::getReadDate, end)
                .orderByAsc(ElectricityReading::getReadDate));

        // 按期按表聚合用量（期用量 × 落在区间内的日窗口比例，抄表统一按 08:00）
        TreeSet<LocalDate> dates = new TreeSet<>();
        Map<LocalDate, Map<Integer, BigDecimal>> usageByDate = new LinkedHashMap<>();
        Map<Long, Integer> noById = new LinkedHashMap<>();
        for (Map.Entry<Integer, Long> entry : idByNo.entrySet()) {
            noById.put(entry.getValue(), entry.getKey());
        }
        for (ElectricityReading reading : readings) {
            Integer no = noById.get(reading.getMeterId());
            if (no == null) {
                continue;
            }
            dates.add(reading.getReadDate());
            usageByDate
                    .computeIfAbsent(reading.getReadDate(), k -> new LinkedHashMap<>())
                    .merge(no, reading.getUsageQty() == null ? BigDecimal.ZERO : reading.getUsageQty(),
                            BigDecimal::add);
        }

        List<LocalDate> dateList = new ArrayList<>(dates);
        Map<Integer, BigDecimal> byMeter = new LinkedHashMap<>();
        List<Map<String, Object>> periods = new ArrayList<>();
        for (int i = 1; i < dateList.size(); i++) {
            LocalDate periodStart = dateList.get(i - 1);
            LocalDate periodEnd = dateList.get(i);
            long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
            if (periodDays <= 0) {
                continue;
            }
            /*
             * 不做日窗口裁剪，直接整期计（2026-11-04 使用方：「187,326 先按这个来」）。
             *
             * 他的 Excel 合计就是四周原值相加：48,862.5 + 48,321.3 + 38,910 + 51,231.9 = 187,325.7 ✓
             * 如果按 8/01~8/31 裁剪日窗口，会把 7/31 那天（属于 7 月）扣掉，得到 180,345 ✗ 对不上他的表。
             * 所以这张表的口径 = **区间内各期原值之和**，与他的 Excel 完全一致。
             */
            Map<Integer, BigDecimal> usage = usageByDate.getOrDefault(periodEnd, Map.of());
            for (Map.Entry<Integer, BigDecimal> entry : usage.entrySet()) {
                byMeter.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", periodStart.toString());
            row.put("to", periodEnd.toString());
            row.put("periodDays", periodDays);
            periods.add(row);
        }

        // 按列汇总
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : COLUMNS.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Integer no : entry.getValue()) {
                sum = sum.add(byMeter.getOrDefault(no, BigDecimal.ZERO));
            }
            values.put(entry.getKey(), sum.setScale(2, RoundingMode.HALF_UP));
        }

        // 其它 = 总电量 − 其余各列（使用方的倒挤口径）
        BigDecimal total = values.getOrDefault("总电量", BigDecimal.ZERO);
        BigDecimal others = total;
        for (String column : BALANCE_COLUMNS) {
            others = others.subtract(values.getOrDefault(column, BigDecimal.ZERO));
        }
        values.put("其它", others.setScale(2, RoundingMode.HALF_UP));

        // 占比：分母是总电量；两个"查看"列不算占比
        BigDecimal denominator = total.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : total;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", entry.getKey());
            row.put("kwh", entry.getValue());
            boolean viewOnly = entry.getKey().startsWith("2#空压机") || "1#空压机".equals(entry.getKey());
            row.put("viewOnly", viewOnly);
            row.put("ratio", viewOnly ? null
                    : entry.getValue().divide(denominator, 6, RoundingMode.HALF_UP).doubleValue());
            rows.add(row);
        }

        // 各列占比之和（验证用：应等于 100%）
        BigDecimal ratioSum = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            if (row.get("ratio") != null) {
                ratioSum = ratioSum.add(BigDecimal.valueOf((Double) row.get("ratio")));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", start.toString());
        data.put("to", end.toString());
        data.put("periods", periods);
        data.put("rows", rows);
        data.put("total", total.setScale(2, RoundingMode.HALF_UP));
        data.put("ratioSum", ratioSum.setScale(6, RoundingMode.HALF_UP));
        return Result.success(data);
    }
}
