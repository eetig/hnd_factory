package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 周统计的聚合结果（决策-004）。
 *
 * <p>只吐**汇总数**，不吐原始记录 —— 周统计页对所有人可见，而它依赖的领料/入库明细
 * 是 admin 专属的（{@code /api/pick/list}、{@code /api/inbound/list} 已被闸门拦下）。
 * 把这一页真正需要的两个和放到一个专门的只读接口里，就不必为了它把明细放出去。
 *
 * <p>两个 Map 的 key 是**请求里点名的物料编码**，请求了却没查到记录的记 {@code 0}
 * （而不是缺席）—— 前端拿到就能直接渲染，不必判空。
 */
@Data
public class WeeklyStatsVO {

    /** 物料编码 → 区间内领料数量合计 */
    private Map<String, BigDecimal> pickQty = new LinkedHashMap<>();

    /** 物料编码 → 区间内入库数量合计 */
    private Map<String, BigDecimal> inboundQty = new LinkedHashMap<>();
}
