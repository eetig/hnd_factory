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
 * 电费预提单（2026-11-04）。
 *
 * <h2>业务（使用方原话）</h2>
 * <blockquote>
 * 电费预提，是指在月底要交本月电费，我们大概在 23-25 号的时候，会预估本月耗电量，
 * 然后计算要到走 OA 预提多少电费。所以只能比实际用电量多，不然不够交电费。
 * 目前的做法是预估总电量 × 1.05，然后**万位向上取整**。
 * </blockquote>
 *
 * <h2>预估电量的算法（他给的例子）</h2>
 * 「7.31 有抄表、8.7 有抄表，相减得到这段时间用电量，除以相隔天数，再乘以 8.1-8.7 相隔天数，
 * 就能估算电量。8.7-8.28 有抄表数据，算出电量，再同理可以估算 8.28-8.31 的用电量。」
 *
 * <p>展开就是两条规则：
 * <ol>
 *   <li><b>月初按日均拆分</b>：抄表期可能跨月（例如 7/25 → 8/07），
 *       落在本月的天数按该期日均折算，只把属于本月的部分算进来。</li>
 *   <li><b>月末按最后一期日均补</b>：最后一期之后到月末的天数，用最后一期的日均乘上去。</li>
 * </ol>
 *
 * <h2>为什么"只能多"</h2>
 * 少了要自己垫钱或走二次流程。所以中间有 1.05 的缓冲，最后金额再万位向上取整。
 * 这两步都会让预提 > 实际，这是**设计意图**，不是误差。
 */
@RestController
@RequestMapping("/api/electricity")
public class ElectricityBillController {

    @Autowired
    private ElectricityMeterMapper meterMapper;

    @Autowired
    private ElectricityReadingMapper readingMapper;

    /** 只统计这 4 项（使用方 2026-11-04 定稿）；总表/变压器只作校验，不进预提 */
    private static final List<String> FOUR_GROUPS = List.of("公用工程", "一车间", "二车间", "乙炔车间");

    /** 缓冲比例：预估电量 × 1.05（他说"只能比实际多"） */
    private static final BigDecimal BUFFER = new BigDecimal("1.05");

    /** 万位向上取整 */
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    @GetMapping("/bill")
    public Result<Map<String, Object>> bill(@RequestParam("month") String month) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }
        LocalDate monthStart;
        try {
            monthStart = LocalDate.parse(month + "-01");
        } catch (Exception e) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        // 往前多取 45 天：需要"上月最后一次抄表"作为第一期的起点
        LocalDate fetchFrom = monthStart.minusDays(45);

        List<ElectricityMeter> meters = meterMapper.selectList(
                new LambdaQueryWrapper<ElectricityMeter>().orderByAsc(ElectricityMeter::getMeterNo));
        Map<Long, ElectricityMeter> meterById = new LinkedHashMap<>();
        for (ElectricityMeter meter : meters) {
            meterById.put(meter.getId(), meter);
        }

        List<ElectricityReading> readings = readingMapper.selectList(new LambdaQueryWrapper<ElectricityReading>()
                .ge(ElectricityReading::getReadDate, fetchFrom)
                .le(ElectricityReading::getReadDate, monthEnd)
                .orderByAsc(ElectricityReading::getReadDate));

        // 按日期聚合：每期每组的用量 + 每期的结束日期
        TreeSet<LocalDate> dates = new TreeSet<>();
        Map<LocalDate, Map<String, BigDecimal>> usageByDate = new LinkedHashMap<>();
        for (ElectricityReading reading : readings) {
            dates.add(reading.getReadDate());
            ElectricityMeter meter = meterById.get(reading.getMeterId());
            if (meter == null) {
                continue;
            }
            String section = meter.getSection() == null ? "" : meter.getSection();
            if (!FOUR_GROUPS.contains(section)) {
                continue; // 总表/变压器不进预提
            }
            usageByDate
                    .computeIfAbsent(reading.getReadDate(), k -> new LinkedHashMap<>())
                    .merge(section, reading.getUsageQty() == null ? BigDecimal.ZERO : reading.getUsageQty(),
                            BigDecimal::add);
        }

        List<LocalDate> dateList = new ArrayList<>(dates);
        if (dateList.size() < 2) {
            throw new BusinessException("这个月抄表期不足两期，无法预估（至少要有上月最后一次和本月一次）");
        }

        // 逐期折算到本月
        Map<String, BigDecimal> doneByGroup = new LinkedHashMap<>();
        for (String group : FOUR_GROUPS) {
            doneByGroup.put(group, BigDecimal.ZERO);
        }
        BigDecimal lastDailyTotal = BigDecimal.ZERO;
        LocalDate lastDate = null;
        int lastPeriodDays = 0;
        List<Map<String, Object>> periodRows = new ArrayList<>();

        for (int i = 1; i < dateList.size(); i++) {
            LocalDate start = dateList.get(i - 1);
            LocalDate end = dateList.get(i);
            long periodDays = ChronoUnit.DAYS.between(start, end);
            if (periodDays <= 0) {
                continue;
            }
            /*
             * 这一期落在本月的"日窗口"数。
             *
             * 使用方 2026-11-04：**所有抄表都在当天 08:00**。
             * 所以一期 (start 08:00 → end 08:00) 覆盖的日窗口是 start、start+1、…、end−1
             * （每个窗口 = 当天 08:00 到次日 08:00）。
             * 与 [monthStart, monthEnd] 求交后：窗口数 = min(end, monthEnd+1) − max(start, monthStart)。
             *
             * 修正前用的是 between(max(start, monthStart−1), min(end, monthEnd))，
             * 对 7/31→8/07 会算成 7 天（其实 7/31 属于 7 月，只有 8/01~8/06 共 6 天）。
             */
            LocalDate windowStart = start.isBefore(monthStart) ? monthStart : start;
            LocalDate windowEndExclusive = end.isAfter(monthEnd) ? monthEnd.plusDays(1) : end;
            long overlapDays = Math.max(0, ChronoUnit.DAYS.between(windowStart, windowEndExclusive));

            Map<String, BigDecimal> usage = usageByDate.getOrDefault(end, Map.of());
            BigDecimal factor = BigDecimal.valueOf(overlapDays)
                    .divide(BigDecimal.valueOf(periodDays), 6, RoundingMode.HALF_UP);

            Map<String, BigDecimal> clipped = new LinkedHashMap<>();
            for (String group : FOUR_GROUPS) {
                BigDecimal raw = usage.getOrDefault(group, BigDecimal.ZERO);
                BigDecimal part = raw.multiply(factor).setScale(2, RoundingMode.HALF_UP);
                clipped.put(group, part);
                doneByGroup.merge(group, part, BigDecimal::add);
            }

            // 记下最后一期的日均（B①：只用最后一期）
            if (i == dateList.size() - 1) {
                lastDate = end;
                lastPeriodDays = (int) periodDays;
                BigDecimal sum = BigDecimal.ZERO;
                for (String group : FOUR_GROUPS) {
                    sum = sum.add(usage.getOrDefault(group, BigDecimal.ZERO));
                }
                lastDailyTotal = sum.divide(BigDecimal.valueOf(periodDays), 4, RoundingMode.HALF_UP);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", start.toString());
            row.put("to", end.toString());
            row.put("periodDays", periodDays);
            row.put("inMonthDays", overlapDays);
            row.put("usage", clipped);
            periodRows.add(row);
        }

        /*
         * 月末补：最后一期之后还要补的"日窗口"数。
         * 最后一期是 8/28 08:00 ⇒ 还缺 8/28、8/29、8/30、8/31 四个窗口
         * ⇒ 窗口数 = monthEnd+1 − lastDate（修正前用的是 monthEnd − lastDate，少补一天）。
         */
        long remainDays = lastDate == null ? 0 : ChronoUnit.DAYS.between(lastDate, monthEnd.plusDays(1));
        Map<String, BigDecimal> tailByGroup = new LinkedHashMap<>();
        for (String group : FOUR_GROUPS) {
            tailByGroup.put(group, BigDecimal.ZERO);
        }
        if (remainDays > 0 && lastDate != null) {
            // 用最后一期各组日均
            Map<String, BigDecimal> lastUsage = usageByDate.getOrDefault(lastDate, Map.of());
            for (String group : FOUR_GROUPS) {
                BigDecimal daily = lastUsage.getOrDefault(group, BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(lastPeriodDays), 6, RoundingMode.HALF_UP);
                tailByGroup.put(group, daily.multiply(BigDecimal.valueOf(remainDays)).setScale(2, RoundingMode.HALF_UP));
            }
        }

        // 预估总电量 = 已抄（折算到本月）+ 月末补
        Map<String, BigDecimal> estimateByGroup = new LinkedHashMap<>();
        BigDecimal estimateTotal = BigDecimal.ZERO;
        for (String group : FOUR_GROUPS) {
            BigDecimal value = doneByGroup.get(group).add(tailByGroup.get(group));
            estimateByGroup.put(group, value);
            estimateTotal = estimateTotal.add(value);
        }

        // 预提电量 = 预估 × 1.05
        BigDecimal advanceKwh = estimateTotal.multiply(BUFFER).setScale(2, RoundingMode.HALF_UP);

        // 单价：按"预提电量"命中档（Q2 ①）
        BigDecimal price = advanceKwh.compareTo(new BigDecimal("100000")) <= 0
                ? new BigDecimal("1.1")
                : advanceKwh.compareTo(new BigDecimal("200000")) <= 0
                ? new BigDecimal("0.9")
                : new BigDecimal("0.72");

        BigDecimal amount = advanceKwh.multiply(price).setScale(2, RoundingMode.HALF_UP);
        // 万位向上取整（Q3：他更正为"万位"、整金额）
        BigDecimal rounded = amount
                .divide(TEN_THOUSAND, 0, RoundingMode.CEILING)
                .multiply(TEN_THOUSAND)
                .setScale(2, RoundingMode.HALF_UP);

        // 4 项分摊：按预估用量占比（C①），最后一项用差额补齐，保证加起来正好等于预提总额
        List<Map<String, Object>> shares = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < FOUR_GROUPS.size(); i++) {
            String group = FOUR_GROUPS.get(i);
            BigDecimal ratio = estimateTotal.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : estimateByGroup.get(group).divide(estimateTotal, 6, RoundingMode.HALF_UP);
            BigDecimal value;
            if (i == FOUR_GROUPS.size() - 1) {
                value = rounded.subtract(allocated);
            } else {
                value = rounded.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                allocated = allocated.add(value);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("section", group);
            row.put("estimateKwh", estimateByGroup.get(group).setScale(2, RoundingMode.HALF_UP));
            row.put("ratio", ratio);
            row.put("amount", value);
            shares.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("month", month);
        data.put("periods", periodRows);
        data.put("doneInMonth", doneByGroup);
        data.put("tailDays", remainDays);
        data.put("tailByGroup", tailByGroup);
        data.put("estimateByGroup", estimateByGroup);
        data.put("estimateKwh", estimateTotal.setScale(2, RoundingMode.HALF_UP));
        data.put("advanceKwh", advanceKwh);
        data.put("price", price);
        data.put("amount", amount);
        data.put("advanceTotal", rounded);
        data.put("shares", shares);
        data.put("lastDate", lastDate == null ? "" : lastDate.toString());
        data.put("lastDailyKwh", lastDailyTotal);
        return Result.success(data);
    }
}
