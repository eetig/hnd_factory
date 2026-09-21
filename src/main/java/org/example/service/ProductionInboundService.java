package org.example.service;

import org.example.dto.ProductionInboundVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.ProductionInbound;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProductionInboundService {
    // 批量保存生产入库单（按 (单据号+物料编码) upsert：存在则更新，不存在则新增）
    WorkOrderImportSaveVO saveImported(List<ProductionInbound> list);

    // 按 (单据号+物料编码) 查已有记录的 id，用于导入时判断走新增还是更新。
    // 入参为待查的单据号集合，返回 Map<组合键, id>（同一单据号下的多条物料都会返回）
    Map<String, Long> findIdByDocAndMaterial(Collection<String> documentNos);

    // 查询生产入库单列表（全量，日期筛选/排序/分页由前端本地完成）
    List<ProductionInboundVO> listAll();
}
