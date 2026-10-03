package org.example.service;

import org.example.dto.WeeklyStatsVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 周统计汇总（决策-004）：按物料编码 + 日期区间，把领料/入库明细求和后返回。
 *
 * <p>只返回聚合数，不返回明细 —— 明细接口对非 admin 已关闭（见
 * {@code SaTokenConfigure} 的只读闸门），周统计页改由这里取数。
 */
public interface WeeklyStatsService {

    /**
     * @param start            区间起（含）；null 表示不限下界
     * @param end              区间止（含）；null 表示不限上界
     * @param pickMaterials    要汇总领料数量的物料编码（结果里每个都会出现，查不到记 0）
     * @param inboundMaterials 要汇总入库数量的物料编码（同上）
     */
    WeeklyStatsVO summarize(LocalDate start, LocalDate end,
                            List<String> pickMaterials, List<String> inboundMaterials);
}
