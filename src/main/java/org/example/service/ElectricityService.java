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
 * 电表与抄表（2026-11-02，第一阶段：只记录，不算钱）。
 *
 * <h2>这一版只做三件事</h2>
 * <ol>
 *   <li>台账：37 块表的名称 / 柜号 / 归属车间 / 倍率，可改</li>
 *   <li>抄表：任意日期录读数，服务端自动找"上次读数"并算出用量</li>
 *   <li>查询：按日期区间 / 车间 / 表查历史</li>
 * </ol>
 *
 * <h2>不做的事（使用方明确「计算之后再处理」）</h2>
 * 不预提、不分摊、不算钱。所以这里没有任何电价、金额、占比字段 ——
 * 等记录稳了再单独做一层，避免"边记边算"把记录弄脏。
 */
@Service
public class ElectricityService {

    @Autowired
    private ElectricityMeterMapper meterMapper;

    @Autowired
    private ElectricityReadingMapper readingMapper;

    // ==================== 台账 ====================

    /** 全部电表，按 sort / meter_no 排序 */
    public List<ElectricityMeter> listMeters() {
        return meterMapper.selectList(new LambdaQueryWrapper<ElectricityMeter>()
                .orderByAsc(ElectricityMeter::getSort)
                .orderByAsc(ElectricityMeter::getMeterNo));
    }

    /** 改一块表（名称 / 柜号 / 归属 / 倍率 / 启用 / 备注） */
    public ElectricityMeter updateMeter(Long id, ElectricityMeter patch) {
        ElectricityMeter row = meterMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("这块表不存在");
        }
        if (patch.getMeterName() != null && !patch.getMeterName().isBlank()) {
            row.setMeterName(patch.getMeterName().trim());
        }
        if (patch.getMeterCode() != null) {
            row.setMeterCode(patch.getMeterCode().trim());
        }
        if (patch.getSection() != null) {
            row.setSection(patch.getSection().trim());
        }
        if (patch.getMultiplier() != null) {
            if (patch.getMultiplier() < 1) {
                throw new BusinessException("倍率至少是 1");
            }
            row.setMultiplier(patch.getMultiplier());
        }
        if (patch.getEnabled() != null) {
            row.setEnabled(patch.getEnabled() == 1 ? 1 : 0);
        }
        if (patch.getRemark() != null) {
            row.setRemark(patch.getRemark().trim());
        }
        meterMapper.updateById(row);
        return row;
    }

    // ==================== 抄表 ====================

    /**
     * 抄表板：某一天每块表的填写状态。
     *
     * <p>每行给：表信息 + **上次读数**（抄表人最需要的参照）+ 当天已填的读数。
     * 抄表现场要的就是"上次多少、今天多少"，别的一概不要。
     */
    public List<Map<String, Object>> sheet(LocalDate date) {
        List<ElectricityMeter> meters = listMeters();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ElectricityMeter meter : meters) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("meterId", meter.getId());
            row.put("meterNo", meter.getMeterNo());
            row.put("meterName", meter.getMeterName());
            row.put("meterCode", meter.getMeterCode());
            row.put("section", meter.getSection());
            row.put("multiplier", meter.getMultiplier());
            row.put("enabled", meter.getEnabled());
            row.put("remark", meter.getRemark());

            // 当天已填？（唯一键保证最多一条）
            ElectricityReading today = readingMapper.selectOne(new LambdaQueryWrapper<ElectricityReading>()
                    .eq(ElectricityReading::getMeterId, meter.getId())
                    .eq(ElectricityReading::getReadDate, date)
                    .last("limit 1"));
            row.put("reading", today == null ? null : today.getReading());
            row.put("usageQty", today == null ? null : today.getUsageQty());
            row.put("imagePath", today == null ? "" : today.getImagePath());

            // 该表在 date 之前最近的一条（用来算用量、也给抄表人看"上次多少"）
            ElectricityReading prev = readingMapper.selectOne(new LambdaQueryWrapper<ElectricityReading>()
                    .eq(ElectricityReading::getMeterId, meter.getId())
                    .lt(ElectricityReading::getReadDate, date)
                    .orderByDesc(ElectricityReading::getReadDate)
                    .last("limit 1"));
            row.put("prevReading", prev == null ? null : prev.getReading());
            row.put("prevDate", prev == null ? null : prev.getReadDate());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 批量保存某一天的抄表。
     *
     * <p>用量由**服务端**算：(本次 − 上次) × 倍率。前端算的只用来即时显示，
     * 存库以服务端为准 —— 否则两个客户端算法不同就会出现两种用量。
     *
     * @param date   统一抄表日期（现场一般同一天抄完）
     * @param items  每项：meterId / reading / imagePath / note
     */
    @Transactional
    public Map<String, Object> saveReadings(LocalDate date, String operator, List<Map<String, Object>> items) {
        if (date == null) {
            throw new BusinessException("请先选择抄表日期");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有要保存的读数");
        }

        int saved = 0;
        List<String> skipped = new ArrayList<>();

        for (Map<String, Object> item : items) {
            Long meterId = toLong(item.get("meterId"));
            BigDecimal reading = toDecimal(item.get("reading"));
            if (meterId == null || reading == null) {
                continue;
            }
            ElectricityMeter meter = meterMapper.selectById(meterId);
            if (meter == null) {
                skipped.add("表 id=" + meterId + " 不存在");
                continue;
            }

            // 上次读数：date 之前最近一条
            ElectricityReading prev = readingMapper.selectOne(new LambdaQueryWrapper<ElectricityReading>()
                    .eq(ElectricityReading::getMeterId, meterId)
                    .lt(ElectricityReading::getReadDate, date)
                    .orderByDesc(ElectricityReading::getReadDate)
                    .last("limit 1"));

            BigDecimal prevValue = prev == null ? null : prev.getReading();
            BigDecimal usage = BigDecimal.ZERO;
            if (prevValue != null) {
                int mult = meter.getMultiplier() == null ? 1 : meter.getMultiplier();
                usage = reading.subtract(prevValue).multiply(BigDecimal.valueOf(mult))
                        .setScale(2, RoundingMode.HALF_UP);
                // 读数回退（换表、清表、抄错）时不做负数处理，只提示；记录本身照存
                if (usage.compareTo(BigDecimal.ZERO) < 0) {
                    skipped.add(meter.getMeterName() + "：本次读数小于上次，" + usage + "（请确认是否换表）");
                }
            }

            ElectricityReading existing = readingMapper.selectOne(new LambdaQueryWrapper<ElectricityReading>()
                    .eq(ElectricityReading::getMeterId, meterId)
                    .eq(ElectricityReading::getReadDate, date)
                    .last("limit 1"));

            if (existing == null) {
                ElectricityReading row = new ElectricityReading();
                row.setMeterId(meterId);
                row.setReadDate(date);
                row.setReading(reading);
                row.setPrevReading(prevValue);
                row.setUsageQty(usage);
                row.setImagePath(str(item.get("imagePath")));
                row.setNote(str(item.get("note")));
                row.setCreatedBy(operator == null ? "" : operator);
                readingMapper.insert(row);
            } else {
                existing.setReading(reading);
                existing.setPrevReading(prevValue);
                existing.setUsageQty(usage);
                if (item.get("imagePath") != null) {
                    existing.setImagePath(str(item.get("imagePath")));
                }
                if (item.get("note") != null) {
                    existing.setNote(str(item.get("note")));
                }
                readingMapper.updateById(existing);
            }
            saved++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("saved", saved);
        result.put("warnings", skipped);
        return result;
    }

    /** 历史查询：按日期区间 + 可选车间/表 */
    public List<Map<String, Object>> history(LocalDate from, LocalDate to, String section, Long meterId) {
        LambdaQueryWrapper<ElectricityReading> wrapper = new LambdaQueryWrapper<ElectricityReading>()
                .ge(from != null, ElectricityReading::getReadDate, from)
                .le(to != null, ElectricityReading::getReadDate, to)
                .eq(meterId != null, ElectricityReading::getMeterId, meterId)
                .orderByDesc(ElectricityReading::getReadDate)
                .orderByDesc(ElectricityReading::getId);
        List<ElectricityReading> readings = readingMapper.selectList(wrapper);

        Map<Long, ElectricityMeter> meterById = new HashMap<>();
        for (ElectricityMeter meter : meterMapper.selectList(null)) {
            meterById.put(meter.getId(), meter);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ElectricityReading reading : readings) {
            ElectricityMeter meter = meterById.get(reading.getMeterId());
            if (meter == null) {
                continue;
            }
            if (section != null && !section.isBlank() && !section.equals(meter.getSection())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", reading.getId());
            row.put("readDate", reading.getReadDate());
            row.put("meterNo", meter.getMeterNo());
            row.put("meterName", meter.getMeterName());
            row.put("section", meter.getSection());
            row.put("multiplier", meter.getMultiplier());
            row.put("reading", reading.getReading());
            row.put("prevReading", reading.getPrevReading());
            row.put("usageQty", reading.getUsageQty());
            row.put("imagePath", reading.getImagePath());
            row.put("note", reading.getNote());
            rows.add(row);
        }
        return rows;
    }

    /** 已用到的车间清单（给筛选下拉） */
    public List<String> sections() {
        List<String> out = new ArrayList<>();
        for (ElectricityMeter meter : listMeters()) {
            String section = meter.getSection();
            if (section != null && !section.isBlank() && !out.contains(section)) {
                out.add(section);
            }
        }
        return out;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
