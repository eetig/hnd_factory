package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.MaterialDocQueryDTO;
import org.example.dto.PickSummaryGroupVO;
import org.example.dto.PickSummaryVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialPickSummary;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MaterialPickSummaryService {
    // 批量保存领料汇总（按单据号 upsert：存在则更新，不存在则新增）
    WorkOrderImportSaveVO saveImported(List<MaterialPickSummary> list);

    /**
     * 领料汇总列表：后端分页 + 后端条件查询（整改 2026-10-10）。
     *
     * <p>改造前是 {@code listAll()} 把全表返回、筛选分页由前端内存数组完成 ——
     * 前端因此持有一份「页面打开那一刻」的快照，库里的新增在筛选时永远看不到。
     */
    IPage<PickSummaryVO> listPage(MaterialDocQueryDTO query);

    /**
     * 物料名称候选（筛选弹窗的下拉项）：当前**日期区间**内出现过的名称，去重后按中文排序。
     *
     * <p>刻意不带物料名称条件 —— 否则选中某个物料后弹窗里就只剩它自己，没法改选。
     * 也刻意只收日期区间（{@link DateRangeQueryDTO}）：候选不是记录页，不该带分页参数。
     */
    List<String> listMaterialNames(DateRangeQueryDTO query);

    /**
     * 按物料汇总领料数 —— 原辅料核算页用（那一页是「按物料 group by 的聚合表」，不是列表）。
     *
     * <p>筛选条件与 {@link #listPage} 完全一致，保证两边看到的是同一批数据。
     * 入参里的 {@code pageNum} / {@code pageSize} 会被忽略 —— 聚合返回的是全部分组行，不分页。
     */
    List<PickSummaryGroupVO> summarizeByMaterial(MaterialDocQueryDTO query);

    /**
     * 全量领料明细（**仅服务端内部使用**）。
     *
     * <p>现在只剩周统计在用（{@code WeeklyStatsServiceImpl}）：它要的是一段日期区间内、
     * 点名几个物料编码的求和，且有一条「日期为空的记录照样计入」的历史口径，
     * 用 SQL 改写有改错数字的风险（那次改造的红线就是数字不变）。
     *
     * <p>⚠️ **不要**再用它去支撑任何列表接口 —— 列表一律走 {@link #listPage}。
     */
    List<PickSummaryVO> listAll();

    // 按 (单据号+物料编码) 查已有记录的 id，用于导入时判断走新增还是更新。
    // 入参为待查的单据号集合，返回 Map<组合键, id>（同一单据号下的多条物料都会返回）
    Map<String, Long> findIdByDocAndMaterial(Collection<String> documentNos);

    // 按 (单据号+物料编码) 查已有记录的领料数量。
    // 用于导入预检：单据号、物料编码、数量三者全同即为重复行，整行跳过（不落库也不上传图片）。
    // 返回 Map<组合键, 领料数量>（同一单据号下的多条物料都会返回）
    Map<String, BigDecimal> findQtyByDocAndMaterial(Collection<String> documentNos);
}
