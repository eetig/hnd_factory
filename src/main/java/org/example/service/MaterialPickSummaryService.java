package org.example.service;

import org.example.dto.PickSummaryVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialPickSummary;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MaterialPickSummaryService {
    // 批量保存领料汇总（按单据号 upsert：存在则更新，不存在则新增）
    WorkOrderImportSaveVO saveImported(List<MaterialPickSummary> list);

    // 查询领料汇总列表（全量，日期筛选/排序/分页由前端本地完成）
    List<PickSummaryVO> listAll();

    // 按 (单据号+物料编码) 查已有记录的 id，用于导入时判断走新增还是更新。
    // 入参为待查的单据号集合，返回 Map<组合键, id>（同一单据号下的多条物料都会返回）
    Map<String, Long> findIdByDocAndMaterial(Collection<String> documentNos);
}
