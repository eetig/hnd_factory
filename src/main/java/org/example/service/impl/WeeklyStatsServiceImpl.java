package org.example.service.impl;

import org.example.dto.PickSummaryVO;
import org.example.dto.ProductionInboundVO;
import org.example.dto.WeeklyStatsVO;
import org.example.service.MaterialPickSummaryService;
import org.example.service.ProductionInboundService;
import org.example.service.WeeklyStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 周统计汇总实现。
 *
 * <p>直接复用两个既有的 listAll()（各自一条全表查询，量级是车间台账，可接受），
 * 在内存里求和 —— 与前端改造前的算法同构，只是把这段循环从浏览器搬到了服务端。
 * 刻意不新写 SQL：口径要与改造前逐字一致，多一种实现就多一处走样的机会。
 */
@Service
public class WeeklyStatsServiceImpl implements WeeklyStatsService {

    @Autowired
    private MaterialPickSummaryService materialPickSummaryService;

    @Autowired
    private ProductionInboundService productionInboundService;

    @Override
    public WeeklyStatsVO summarize(LocalDate start, LocalDate end,
                                   List<String> pickMaterials, List<String> inboundMaterials) {

        WeeklyStatsVO vo = new WeeklyStatsVO();

        // 先把点名的物料填成 0：结果为 0 与「结果里没有这一项」对前端是两回事，
        // 统一成前者，前端可以直接取数不用兜底。
        for (String code : pickMaterials) {
            vo.getPickQty().put(code, BigDecimal.ZERO);
        }
        for (String code : inboundMaterials) {
            vo.getInboundQty().put(code, BigDecimal.ZERO);
        }

        if (!pickMaterials.isEmpty()) {
            for (PickSummaryVO record : materialPickSummaryService.listAll()) {
                if (!inRange(record.getPickDate(), start, end)) {
                    continue;
                }
                String code = trimToEmpty(record.getMaterialCode());
                if (!vo.getPickQty().containsKey(code)) {
                    continue;   // 只汇总点名的物料，其余一概不进结果
                }
                vo.getPickQty().merge(code, nullToZero(record.getPickQty()), BigDecimal::add);
            }
        }

        if (!inboundMaterials.isEmpty()) {
            for (ProductionInboundVO record : productionInboundService.listAll()) {
                if (!inRange(record.getInboundDate(), start, end)) {
                    continue;
                }
                String code = trimToEmpty(record.getMaterialCode());
                if (!vo.getInboundQty().containsKey(code)) {
                    continue;
                }
                vo.getInboundQty().merge(code, nullToZero(record.getInboundQty()), BigDecimal::add);
            }
        }

        return vo;
    }

    /**
     * 区间判定（闭区间）。
     *
     * <p>★ 日期为 null 的记录**计入** —— 这不是笔误：改造前前端就是
     * {@code if (!pickDate) return true}（日期列没值的行照样算进合计）。
     * 改成「排除」会让数字与改造前对不上，而这次改造的红线就是数字不变。
     */
    private boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null) {
            return true;
        }
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return end == null || !date.isAfter(end);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
