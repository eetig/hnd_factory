package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.entity.ElectricityMeter;
import org.example.entity.ElectricityReading;
import org.example.exception.BusinessException;
import org.example.mapper.ElectricityMeterMapper;
import org.example.mapper.ElectricityReadingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抄表批量粘贴导入 + 按车间汇总（2026-11-03）。
 *
 * <h2>为什么要"粘贴导入"</h2>
 * 使用方的月度抄表在 Excel 里，但**文件带锁、导出来是乱码**（他 2026-11-03 说的）。
 * 而"复制单元格 → 粘贴"通常还能用。所以入口做成：在 Excel 里选中那几行 → 复制 →
 * 粘到页面的文本框 → 系统自己解析。
 *
 * <p>这比让 AI 对着截图手抄 152 个数字靠谱得多（手抄一定会错几个，而且查不出来）。
 *
 * <h2>粘贴格式</h2>
 * 每行一期，制表符分隔（Excel 复制的默认格式）：
 * <pre>
 * 2026-08-07  10782264  2477.5  1047.5 ...
 * 2026-08-14  ...       ...     ...
 * </pre>
 * 第一列是日期，后面依次是**表内编号 1~37 对应的读数**（150车间那列也要占一位，顺序不能跳）。
 *
 * <h2>按车间汇总</h2>
 * 逐条记录累加 usage_qty，按 section 分组；月末给出一张"车间 × 用量"的表 ——
 * 这就是使用方要的"各个车间求和"。**不含任何电费**（他说过计算之后再处理）。
 */
@Service
public class ElectricityImportService {

    @Autowired
    private ElectricityMeterMapper meterMapper;

    @Autowired
    private ElectricityReadingMapper readingMapper;

    /**
     * 批量导入。
     *
     * @param dates  与 values 行数一致
     * @param values 每行一串读数，按**列序**（第 i 个数字对应第 i 块表，按 meter_no 排）
     * @param operator 操作人
     */
    @Transactional
    public Map<String, Object> importRows(List<String> dates, List<List<String>> values, String operator) {
        List<ElectricityMeter> meters = meterMapper.selectList(
                new LambdaQueryWrapper<ElectricityMeter>().orderByAsc(ElectricityMeter::getMeterNo));
        if (meters.isEmpty()) {
            throw new BusinessException("还没有电表台账");
        }

        int saved = 0;
        List<String> warnings = new ArrayList<>();

        for (int r = 0; r < dates.size(); r++) {
            LocalDate date;
            try {
                date = LocalDate.parse(dates.get(r).trim());
            } catch (Exception e) {
                warnings.add("第 " + (r + 1) + " 行日期看不懂：" + dates.get(r));
                continue;
            }
            List<String> row = values.get(r);
            if (row.size() != meters.size()) {
                warnings.add(
                        "第 " + (r + 1) + " 行有 " + row.size() + " 个数，台账是 " + meters.size()
                                + " 块表 —— 这一行跳过（列数必须一致，否则会整体错位）");
                continue;
            }

            for (int i = 0; i < meters.size(); i++) {
                ElectricityMeter meter = meters.get(i);
                BigDecimal reading = toDecimal(row.get(i));
                if (reading == null) {
                    continue; // 空格 = 这块表这期没抄，跳过（不是 0）
                }

                // 上次读数：这一期之前最近的一条
                ElectricityReading prev = readingMapper.selectOne(new LambdaQueryWrapper<ElectricityReading>()
                        .eq(ElectricityReading::getMeterId, meter.getId())
                        .lt(ElectricityReading::getReadDate, date)
                        .orderByDesc(ElectricityReading::getReadDate)
                        .last("limit 1"));
                BigDecimal prevValue = prev == null ? null : prev.getReading();

                BigDecimal usage = BigDecimal.ZERO;
                if (prevValue != null) {
                    int mult = meter.getMultiplier() == null ? 1 : meter.getMultiplier();
                    usage = reading.subtract(prevValue)
                            .multiply(BigDecimal.valueOf(mult))
                            .setScale(2, RoundingMode.HALF_UP);
                }

                ElectricityReading existing = readingMapper.selectOne(new LambdaQueryWrapper<ElectricityReading>()
                        .eq(ElectricityReading::getMeterId, meter.getId())
                        .eq(ElectricityReading::getReadDate, date)
                        .last("limit 1"));

                if (existing == null) {
                    ElectricityReading entity = new ElectricityReading();
                    entity.setMeterId(meter.getId());
                    entity.setReadDate(date);
                    entity.setReading(reading);
                    entity.setPrevReading(prevValue);
                    entity.setUsageQty(usage);
                    entity.setImagePath("");
                    entity.setNote("粘贴导入");
                    entity.setCreatedBy(operator == null ? "" : operator);
                    readingMapper.insert(entity);
                } else {
                    existing.setReading(reading);
                    existing.setPrevReading(prevValue);
                    existing.setUsageQty(usage);
                    existing.setNote("粘贴导入（覆盖）");
                    readingMapper.updateById(existing);
                }
                saved += 1;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("saved", saved);
        result.put("warnings", warnings);
        return result;
    }

    /**
     * 按车间汇总某个月的用量。
     *
     * @param from 起始日（含）
     * @param to   结束日（含）
     */
    public List<Map<String, Object>> summaryBySection(LocalDate from, LocalDate to) {
        List<ElectricityReading> readings = readingMapper.selectList(new LambdaQueryWrapper<ElectricityReading>()
                .ge(from != null, ElectricityReading::getReadDate, from)
                .le(to != null, ElectricityReading::getReadDate, to));
        Map<Long, ElectricityMeter> meterById = new HashMap<>();
        for (ElectricityMeter meter : meterMapper.selectList(null)) {
            meterById.put(meter.getId(), meter);
        }

        Map<String, BigDecimal> total = new LinkedHashMap<>();
        Map<String, Integer> meterCount = new LinkedHashMap<>();
        for (ElectricityReading reading : readings) {
            ElectricityMeter meter = meterById.get(reading.getMeterId());
            if (meter == null) {
                continue;
            }
            String section = meter.getSection() == null || meter.getSection().isBlank()
                    ? "未分组"
                    : meter.getSection();
            total.merge(section, reading.getUsageQty() == null ? BigDecimal.ZERO : reading.getUsageQty(), BigDecimal::add);
            meterCount.merge(section, 0, Integer::sum);
        }

        BigDecimal grand = total.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : total.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("section", entry.getKey());
            row.put("usageQty", entry.getValue().setScale(2, RoundingMode.HALF_UP));
            row.put("share", grand.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : entry.getValue().divide(grand, 4, RoundingMode.HALF_UP));
            rows.add(row);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("usageQty")).compareTo((BigDecimal) a.get("usageQty")));

        Map<String, Object> totalRow = new LinkedHashMap<>();
        totalRow.put("section", "合计");
        totalRow.put("usageQty", grand.setScale(2, RoundingMode.HALF_UP));
        totalRow.put("share", BigDecimal.ONE);
        rows.add(totalRow);
        return rows;
    }

    private static BigDecimal toDecimal(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().replace(",", "");
        if (text.isEmpty() || "-".equals(text) || "—".equals(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
