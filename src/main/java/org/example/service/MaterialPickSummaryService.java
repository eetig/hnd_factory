package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.DocumentRowVO;
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

    // 按 (单据号+物料编码+日期) 查已有记录的 id，用于导入时判断走新增还是更新。
    // 入参为待查的单据号集合，返回 Map<三键, id>（同一单据号下的多条物料都会返回）。
    // ⚠️ 键必须用 DocMaterialDateKey.of 生成（变更-047：日期是行身份的一部分，NULL 日期归一空串）
    Map<String, Long> findIdByDocMaterialDate(Collection<String> documentNos);

    // 按 (单据号+物料编码+日期) 查已有记录的领料数量。
    // 用于导入预检：单据号、物料编码、日期、数量四者全同即为重复行，整行跳过（不落库也不上传图片）。
    // 返回 Map<三键, 领料数量>（同一单据号下的多条物料都会返回）
    Map<String, BigDecimal> findQtyByDocMaterialDate(Collection<String> documentNos);

    /**
     * 一张单据的全部明细行（变更-047）—— {@code GET /api/work-order/document/detail}
     * 与 document 模式落库的差量比对共用。
     *
     * <p>排序 {@code seq_no ASC, id ASC}（确定序，前端表格按此展示）。
     * 返回的 VO <b>带行 id 与日期</b>，这是它与 {@link #listPage} 系列的本质区别：
     * 列表 VO 没有行身份，撑不起「载入编辑后按行差量保存」。
     */
    List<DocumentRowVO> listByDocument(String documentNo);

    /**
     * document 模式落库的写入口（变更-047）：三件事必须在<b>同一个事务</b>里做，
     * 且顺序固定 —— <b>先删、再更、后插</b>。
     *
     * <p>先删的理由：用户「改掉某行的物料编码」= 删旧行 + 插新行，
     * 若先插后删会在旧键（现在含日期）上撞唯一索引，事务直接失败。
     * 删除走物理删除（本轮口径，见变更-047 §风险与取舍）。
     *
     * @param toInsert    待新增行（id 必须已清空，seqNo 已按行序重排）
     * @param toUpdate    待更新行（id 为库中 id，seqNo 已按行序重排）
     * @param idsToDelete 待删除的库中行 id（可为空集合，不可为 null）
     */
    void applyDocumentChanges(List<MaterialPickSummary> toInsert,
                              List<MaterialPickSummary> toUpdate,
                              Collection<Long> idsToDelete);
}
