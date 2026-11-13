package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.Result;
import org.example.electricity.ElectricityColumns;
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
 * 电费预提单（2026-11-04 重写：总电量为基数 + 4 组新定义）。
 *
 * <h2>业务（使用方原话）</h2>
 * <blockquote>
 * 电费预提，是指在月底要交本月电费，我们大概在 23-25 号的时候，会预估本月耗电量，
 * 然后计算要到走 OA 预提多少电费。所以只能比实际用电量多，不然不够交电费。
 * 目前的做法是预估总电量 × 1.05，然后万位向上取整。
 * </blockquote>
 *
 * <h2>2026-11-04 新分组口径（原话）</h2>
 * <blockquote>
 * 电费预提计算逻辑重新做：总电量就是图中的总电量。
 * 公用工程为图中的公用工程+中央控制室+污水区+办公楼+其它。
 * 一车间为 2#厂房+4#厂房。二车间为 1#厂房，乙炔车间为乙炔车间
 * </blockquote>
 *
 * <h2>与旧版的差别</h2>
 * <ul>
 *   <li><b>基数变了</b>：旧版拿"4 组之和"当预估电量；新版拿<b>总电量（1 号高压计量表）</b>。</li>
 *   <li><b>公用工程变宽</b>：把中央控制室、污水区、办公楼、其它一起并进公用工程。</li>
 *   <li><b>一车间</b>=2#+4#；<b>二车间</b>=1#；<b>乙炔</b>=乙炔。</li>
 * </ul>
 * <p>四组金额按"各组预估 ÷ 四组预估之和"归一分配（分母是四组之和，不是总电量 ——
 * 金额必须 100% 分掉），最后一项补差，保证合计正好等于预提总额。</p>
 */
@RestController
@RequestMapping("/api/electricity")
public class ElectricityBillController {

    @Autowired
    private ElectricityMeterMapper meterMapper;

    @Autowired
    private ElectricityReadingMapper readingMapper;

    /** 缓冲：预估总电量 × 1.05（"只能多，不能少"） */
    private static final BigDecimal BUFFER = new BigDecimal("1.05");

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    /*
     * 12 列定义、四组定义都移到 org.example.electricity.ElectricityColumns
     * （原来这里和 ElectricityMonthlyController 各复制一份，两处必须逐字一致 —— 抽成一份真相）。
     */
    private static final Map<String, List<Integer>> COLUMNS = ElectricityColumns.COLUMNS;
    private static final Map<String, List<String>> BILL_GROUPS = ElectricityColumns.BILL_GROUPS;

    @GetMapping("/bill")
    public Result<Map<String, Object>> bill(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }

        LocalDate start;
        LocalDate end;
        boolean customRange = from != null && !from.isBlank() && to != null && !to.isBlank();
        if (customRange) {
            start = LocalDate.parse(from);
            end = LocalDate.parse(to);
        } else {
            if (month == null || month.isBlank()) {
                throw new BusinessException("要么给 from/to，要么给 month");
            }
            start = LocalDate.parse(month + "-01");
            end = start.withDayOfMonth(start.lengthOfMonth());
        }

        // ---- 取数（往前 45 天：需要"区间起点之前最后一次抄表"作为第一期起点）----
        List<ElectricityMeter> meters = meterMapper.selectList(
                new LambdaQueryWrapper<ElectricityMeter>().orderByAsc(ElectricityMeter::getMeterNo));
        Map<Long, Integer> noById = new LinkedHashMap<>();
        for (ElectricityMeter meter : meters) {
            noById.put(meter.getId(), meter.getMeterNo());
        }

        List<ElectricityReading> readings = readingMapper.selectList(new LambdaQueryWrapper<ElectricityReading>()
                .ge(ElectricityReading::getReadDate, start.minusDays(45))
                .le(ElectricityReading::getReadDate, end)
                .orderByAsc(ElectricityReading::getReadDate));

        TreeSet<LocalDate> dates = new TreeSet<>();
        Map<LocalDate, Map<Integer, BigDecimal>> usageByDate = new LinkedHashMap<>();
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
        if (dateList.size() < 2) {
            throw new BusinessException("这段区间抄表期不足两期，无法预估（至少要有上月最后一次和本月一次）");
        }

        // ---- 逐期算 12 列，再折算进区间 ----
        Map<String, BigDecimal> doneByColumn = new LinkedHashMap<>();
        for (String column : COLUMNS.keySet()) {
            doneByColumn.put(column, BigDecimal.ZERO);
        }
        doneByColumn.put("其它", BigDecimal.ZERO);

        List<Map<String, Object>> periodRows = new ArrayList<>();
        Map<String, BigDecimal> lastPeriodColumns = null;
        int lastPeriodDays = 0;
        BigDecimal lastDailyTotal = BigDecimal.ZERO;
        // 第一期（dateList[0] → dateList[1]）的日量：月初缺口要按紧邻那期补，不能用更早的长周期
        Map<String, BigDecimal> firstPeriodColumns = null;
        int firstPeriodDays = 0;

        for (int i = 1; i < dateList.size(); i++) {
            LocalDate periodStart = dateList.get(i - 1);
            LocalDate periodEnd = dateList.get(i);
            long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
            if (periodDays <= 0) {
                continue;
            }
            /*
             * 日窗口折算（2026-11-04 使用方再次强调：「所有抄表都是早上 8:00，
             * 所以 7.31 日这天算上去的话就不对了」）。
             *
             * 一期 (periodStart 08:00 → periodEnd 08:00) 覆盖的日窗口是
             * periodStart、periodStart+1、…、periodEnd−1（每个窗口 = 当天 08:00 到次日 08:00）。
             * 与 [start, end] 求交：窗口数 = min(periodEnd, end+1) − max(periodStart, start)。
             *
             * 例：7/31 → 8/07 这一期，7/31 那个窗口属于 7 月，
             *     落在 8 月的只有 8/01~8/06 共 6 天 ⇒ 按 6/7 折算，不再是整期。
             */
            LocalDate windowStart = periodStart.isBefore(start) ? start : periodStart;
            LocalDate windowEnd = periodEnd.isAfter(end) ? end.plusDays(1) : periodEnd;
            long days = Math.max(0, ChronoUnit.DAYS.between(windowStart, windowEnd));
            BigDecimal factor = BigDecimal.valueOf(days)
                    .divide(BigDecimal.valueOf(periodDays), 6, RoundingMode.HALF_UP);

            Map<String, BigDecimal> columns = ElectricityColumns.compute(
                    usageByDate.getOrDefault(periodEnd, Map.of()));

            Map<String, BigDecimal> clipped = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> entry : columns.entrySet()) {
                BigDecimal part = entry.getValue().multiply(factor).setScale(2, RoundingMode.HALF_UP);
                clipped.put(entry.getKey(), part);
                doneByColumn.merge(entry.getKey(), part, BigDecimal::add);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", periodStart.toString());
            row.put("to", periodEnd.toString());
            row.put("periodDays", periodDays);
            row.put("inRangeDays", days);
            row.put("columns", clipped);
            periodRows.add(row);

            lastPeriodColumns = columns;
            lastPeriodDays = (int) periodDays;
            lastDailyTotal = columns.getOrDefault("总电量", BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(periodDays), 4, RoundingMode.HALF_UP);
            if (firstPeriodColumns == null) {
                firstPeriodColumns = columns;
                firstPeriodDays = (int) periodDays;
            }
        }

        /*
         * 月初补（2026-11-13 使用方：「算法链确定没问题吗，1-30 号都算了吗」）。
         *
         * 循环是从 dateList[1] 开始的，所以 dateList[0] 之前那一期永远不存在：
         * 只要本月第一次抄表不在 1 号，1 号到那之前就是空白。
         * 2026-11 就是这个情况 —— 1 号总表上一期抄在 09-04，程序只按区间里出现的
         * 抄表日配对，11-01~11-05 这 5 天既不在这张账单里、也没在 10 月账单里。
         *
         * 补法用**第一期（11-06 → 11-13）的日均**，不用 09-04 → 11-06 那 63 天的日均：
         * 那 63 天里工厂停过工（约 4074 度/天），拿它摊 5 天会把 11 月凭空抬高 2 万度。
         * 与月末补对称：头按紧邻那期，尾按最后一期。
         */
        LocalDate firstDate = dateList.get(0);
        long headDays = customRange ? 0 : Math.max(0, ChronoUnit.DAYS.between(start, firstDate));
        Map<String, BigDecimal> headByColumn = new LinkedHashMap<>();
        for (String column : doneByColumn.keySet()) {
            headByColumn.put(column, BigDecimal.ZERO);
        }
        if (headDays > 0 && firstPeriodColumns != null && firstPeriodDays > 0) {
            for (Map.Entry<String, BigDecimal> entry : firstPeriodColumns.entrySet()) {
                BigDecimal daily = entry.getValue()
                        .divide(BigDecimal.valueOf(firstPeriodDays), 6, RoundingMode.HALF_UP);
                headByColumn.put(entry.getKey(),
                        daily.multiply(BigDecimal.valueOf(headDays)).setScale(2, RoundingMode.HALF_UP));
            }
        }

        // ---- 月末补（只有自然月才补；自定义区间不补）----
        LocalDate lastDate = dateList.get(dateList.size() - 1);
        long tailDays = customRange ? 0 : Math.max(0, ChronoUnit.DAYS.between(lastDate, end.plusDays(1)));
        Map<String, BigDecimal> tailByColumn = new LinkedHashMap<>();
        for (String column : doneByColumn.keySet()) {
            tailByColumn.put(column, BigDecimal.ZERO);
        }
        if (tailDays > 0 && lastPeriodColumns != null && lastPeriodDays > 0) {
            for (Map.Entry<String, BigDecimal> entry : lastPeriodColumns.entrySet()) {
                BigDecimal daily = entry.getValue()
                        .divide(BigDecimal.valueOf(lastPeriodDays), 6, RoundingMode.HALF_UP);
                tailByColumn.put(entry.getKey(),
                        daily.multiply(BigDecimal.valueOf(tailDays)).setScale(2, RoundingMode.HALF_UP));
            }
        }

        // ---- 预估：基数 = 总电量（1 号表）----
        BigDecimal doneTotal = doneByColumn.getOrDefault("总电量", BigDecimal.ZERO);
        BigDecimal headTotal = headByColumn.getOrDefault("总电量", BigDecimal.ZERO);
        BigDecimal tailTotal = tailByColumn.getOrDefault("总电量", BigDecimal.ZERO);
        BigDecimal estimateTotal = doneTotal.add(headTotal).add(tailTotal)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal advanceKwh = estimateTotal.multiply(BUFFER).setScale(2, RoundingMode.HALF_UP);

        /*
         * 电价档位（2026-11-04 按 10 月真实账单校正）：
         * 账单 69,690 kWh / 64,479.78 元 ⇒ 综合含税 0.9252 元/kWh
         *   ≤ 7 万 → 0.9252 ｜ 7~20 万 → 0.9 ｜ > 20 万 → 0.72（历史较准，暂不动）
         */
        BigDecimal price = advanceKwh.compareTo(new BigDecimal("70000")) <= 0
                ? new BigDecimal("0.9252")
                : advanceKwh.compareTo(new BigDecimal("200000")) <= 0
                ? new BigDecimal("0.9")
                : new BigDecimal("0.72");

        BigDecimal amount = advanceKwh.multiply(price).setScale(2, RoundingMode.HALF_UP);
        BigDecimal advanceTotal = amount
                .divide(TEN_THOUSAND, 0, RoundingMode.CEILING)
                .multiply(TEN_THOUSAND)
                .setScale(2, RoundingMode.HALF_UP);

        // ---- 4 组（2026-11-04 新口径）----
        Map<String, BigDecimal> groupEstimate = new LinkedHashMap<>();
        BigDecimal groupSum = BigDecimal.ZERO;
        for (Map.Entry<String, List<String>> entry : BILL_GROUPS.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (String column : entry.getValue()) {
                sum = sum.add(doneByColumn.getOrDefault(column, BigDecimal.ZERO));
                sum = sum.add(headByColumn.getOrDefault(column, BigDecimal.ZERO));
                sum = sum.add(tailByColumn.getOrDefault(column, BigDecimal.ZERO));
            }
            BigDecimal value = sum.setScale(2, RoundingMode.HALF_UP);
            groupEstimate.put(entry.getKey(), value);
            groupSum = groupSum.add(value);
        }

        List<Map<String, Object>> shares = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        List<String> groupNames = new ArrayList<>(BILL_GROUPS.keySet());
        for (int i = 0; i < groupNames.size(); i++) {
            String group = groupNames.get(i);
            BigDecimal value = groupEstimate.get(group);
            BigDecimal ratio = groupSum.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : value.divide(groupSum, 6, RoundingMode.HALF_UP);
            BigDecimal groupAmount;
            if (i == groupNames.size() - 1) {
                groupAmount = advanceTotal.subtract(allocated); // 最后一项补差，保证合计正好
            } else {
                groupAmount = advanceTotal.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                allocated = allocated.add(groupAmount);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("section", group);
            row.put("estimateKwh", value);
            row.put("ratio", ratio);
            row.put("amount", groupAmount);
            shares.add(row);
        }

        /*
         * 本期分表明细（2026-11-13 使用方：「这个计算过程不够直观，我想查找这个算出
         * 负值的原因，毫无头绪」）。
         *
         * 负值的唯一来源是「其它 = 总电量 − 八项分表」这个倒挤项：某一块分表读数跳一下，
         * 八列之和就会超过总表，其它必然变负，再被月末补按日外推放大，最后整组都成负数。
         * 所以要把"哪块表把哪一列顶上去"这件事原样交给页面，不要让人自己反推。
         */
        Map<Integer, String> columnByMeter = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : COLUMNS.entrySet()) {
            if ("总电量".equals(entry.getKey())) {
                continue;
            }
            for (Integer no : entry.getValue()) {
                columnByMeter.putIfAbsent(no, entry.getKey());
            }
        }
        Map<Integer, ElectricityMeter> meterByNo = new LinkedHashMap<>();
        for (ElectricityMeter meter : meters) {
            meterByNo.put(meter.getMeterNo(), meter);
        }
        List<Map<String, Object>> lastPeriodMeters = new ArrayList<>();
        for (ElectricityReading reading : readings) {
            if (!lastDate.equals(reading.getReadDate())) {
                continue;
            }
            Integer no = noById.get(reading.getMeterId());
            if (no == null) {
                continue;
            }
            ElectricityMeter meter = meterByNo.get(no);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", reading.getId());
            row.put("meterNo", no);
            row.put("meterName", meter == null ? "" : meter.getMeterName());
            row.put("column", columnByMeter.get(no));
            row.put("multiplier", meter == null ? 1 : meter.getMultiplier());
            row.put("reading", reading.getReading());
            row.put("prevReading", reading.getPrevReading());
            row.put("usageQty", reading.getUsageQty());
            row.put("readDate", reading.getReadDate().toString());
            lastPeriodMeters.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lastPeriodMeters", lastPeriodMeters);
        data.put("groupColumns", BILL_GROUPS);
        data.put("from", start.toString());
        data.put("to", end.toString());
        data.put("customRange", customRange);
        data.put("lastDate", lastDate.toString());
        data.put("headDays", headDays);
        data.put("headByColumn", headByColumn);
        data.put("tailDays", tailDays);
        data.put("lastDailyKwh", lastDailyTotal);
        data.put("periods", periodRows);
        data.put("doneByColumn", doneByColumn);
        data.put("tailByColumn", tailByColumn);
        data.put("doneTotal", doneTotal);
        data.put("headTotal", headTotal);
        data.put("tailTotal", tailTotal);
        data.put("estimateKwh", estimateTotal);
        data.put("advanceKwh", advanceKwh);
        data.put("price", price);
        data.put("amount", amount);
        data.put("advanceTotal", advanceTotal);
        data.put("groupEstimate", groupEstimate);
        data.put("groupSum", groupSum.setScale(2, RoundingMode.HALF_UP));
        data.put("shares", shares);
        return Result.success(data);
    }
}
