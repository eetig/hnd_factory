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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 按「抄表期」汇总（2026-11-03）。
 *
 * <h2>为什么要这个</h2>
 * 使用方的月度表是"每周抄一次"，所以他的周报表每一行是**两块表相邻两次读数之间**的差，
 * 不是自然周的日历天数和。抄表日期不固定（他说过"基本每周五，但有时别的时间"），
 * 所以"期"只能由**实际抄表日期**来切，不能按周一~周日切。
 *
 * <p>接口返回：期间列表（每个期间的结束日期）+ 每块表每期的用量 + 每期合计，
 * 前端直接画成"期 × 电表"的表 —— 与他 Excel 的形状一一对应。
 */
@RestController
@RequestMapping("/api/electricity")
public class ElectricityPeriodController {

    @Autowired
    private ElectricityMeterMapper meterMapper;

    @Autowired
    private ElectricityReadingMapper readingMapper;

    @GetMapping("/periods")
    public Result<Map<String, Object>> periods(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }

        List<ElectricityMeter> meters = meterMapper.selectList(
                new LambdaQueryWrapper<ElectricityMeter>().orderByAsc(ElectricityMeter::getMeterNo));

        LambdaQueryWrapper<ElectricityReading> wrapper = new LambdaQueryWrapper<ElectricityReading>()
                .orderByAsc(ElectricityReading::getReadDate);
        if (from != null && !from.isBlank()) {
            wrapper.ge(ElectricityReading::getReadDate, LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            wrapper.le(ElectricityReading::getReadDate, LocalDate.parse(to));
        }
        List<ElectricityReading> readings = readingMapper.selectList(wrapper);

        // 期间 = 所有出现过的抄表日期（升序）；第一个日期之前没有"期"
        TreeSet<String> dates = new TreeSet<>();
        for (ElectricityReading reading : readings) {
            dates.add(reading.getReadDate().toString());
        }
        /*
         * 每个抄表日期本身就是"一期的结束"（usage_qty 已经是相对上一次读数的差），
         * 所以**不能**丢掉第一个日期 —— 丢了第一期（例如 8/07）就从表里消失了。
         * 只有第一个日期的记录本身没有"上次读数"时，它的用量才是 0，那不是错，是基线。
         */
        List<String> periods = new ArrayList<>(dates);

        // 记录表只存 meterId，换算成 meterNo（界面/对账都用表内编号）
        Map<Long, Integer> meterNoById = new LinkedHashMap<>();
        for (ElectricityMeter meter : meters) {
            meterNoById.put(meter.getId(), meter.getMeterNo());
        }

        Map<String, Map<Integer, BigDecimal>> cells = new LinkedHashMap<>();
        Map<String, BigDecimal> periodTotal = new LinkedHashMap<>();
        for (String date : periods) {
            Map<Integer, BigDecimal> row = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (ElectricityReading reading : readings) {
                if (!reading.getReadDate().toString().equals(date)) {
                    continue;
                }
                BigDecimal usage = reading.getUsageQty() == null ? BigDecimal.ZERO : reading.getUsageQty();
                row.put(meterNoById.getOrDefault(reading.getMeterId(), 0), usage);
                total = total.add(usage);
            }
            cells.put(date, row);
            periodTotal.put(date, total);
        }

        List<Map<String, Object>> meterRows = new ArrayList<>();
        for (ElectricityMeter meter : meters) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("meterNo", meter.getMeterNo());
            row.put("excelNo", meter.getExcelNo());
            row.put("meterName", meter.getMeterName());
            row.put("meterCode", meter.getMeterCode());
            row.put("section", meter.getSection());
            row.put("multiplier", meter.getMultiplier());
            meterRows.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("periods", periods);
        data.put("meters", meterRows);
        data.put("cells", cells);
        data.put("periodTotal", periodTotal);
        return Result.success(data);
    }
}
