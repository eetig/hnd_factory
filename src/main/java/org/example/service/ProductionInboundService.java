package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.MaterialDocQueryDTO;
import org.example.dto.InboundSummaryGroupVO;
import org.example.dto.ProductionInboundVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.ProductionInbound;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProductionInboundService {
    // 批量保存生产入库单（按 (单据号+物料编码) upsert：存在则更新，不存在则新增）
    WorkOrderImportSaveVO saveImported(List<ProductionInbound> list);

    // 按 (单据号+物料编码) 查已有记录的 id，用于导入时判断走新增还是更新。
    // 入参为待查的单据号集合，返回 Map<组合键, id>（同一单据号下的多条物料都会返回）
    Map<String, Long> findIdByDocAndMaterial(Collection<String> documentNos);

    // 按 (单据号+物料编码) 查已有记录的入库数量。
    // 用于导入预检：单据号、物料编码、数量三者全同即为重复行，整行跳过（不落库也不上传图片）。
    // 返回 Map<组合键, 入库数量>（同一单据号下的多条物料都会返回）
    Map<String, BigDecimal> findQtyByDocAndMaterial(Collection<String> documentNos);

    /**
     * 入库汇总列表：后端分页 + 后端条件查询（整改 2026-10-10）。
     *
     * <p>改造前是 {@code listAll()} 把全表返回、筛选分页由前端内存数组完成 ——
     * 前端因此持有一份「页面打开那一刻」的快照，库里的新增在筛选时永远看不到。
     */
    IPage<ProductionInboundVO> listPage(MaterialDocQueryDTO query);

    /**
     * 物料名称候选（筛选弹窗的下拉项）：当前**日期区间**内出现过的名称，去重后按中文排序。
     *
     * <p>刻意不带物料名称条件 —— 否则选中某个物料后弹窗里就只剩它自己，没法改选。
     */
    List<String> listMaterialNames(DateRangeQueryDTO query);

    /**
     * 按物料汇总入库数 —— 工单核算页用（那一页是「按物料 group by 的聚合表」，不是列表）。
     *
     * <p>筛选条件与 {@link #listPage} 完全一致。入参里的 {@code pageNum} / {@code pageSize}
     * 会被忽略 —— 聚合返回的是全部分组行，不分页。
     */
    List<InboundSummaryGroupVO> summarizeByMaterial(MaterialDocQueryDTO query);

    /**
     * 全量入库明细（**仅服务端内部使用**）。
     *
     * <p>现在只剩周统计在用（{@code WeeklyStatsServiceImpl}），理由见
     * {@code MaterialPickSummaryService#listAll} 的同名注释。
     *
     * <p>⚠️ **不要**再用它去支撑任何列表接口 —— 列表一律走 {@link #listPage}。
     */
    List<ProductionInboundVO> listAll();
}
