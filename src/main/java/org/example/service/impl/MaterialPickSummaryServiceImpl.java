package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.dto.ImgResult;
import org.example.dto.PickSummaryVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialPickSummary;
import org.example.feign.ImgFeignClient;
import org.example.mapper.MaterialPickSummaryMapper;
import org.example.service.MaterialPickSummaryService;
import org.example.util.DocMaterialKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MaterialPickSummaryServiceImpl extends ServiceImpl<MaterialPickSummaryMapper, MaterialPickSummary>
        implements MaterialPickSummaryService {

    @Autowired
    private ImgFeignClient imgFeignClient;

    /**
     * 按 (单据号 + 物料编码) upsert：存在则更新，不存在则新增。
     * 同一张单据下可以有多条不同物料的明细，因此单据号单独不唯一。
     * 同批内组合键重复时后者覆盖前者（避免批内自冲突触发唯一键异常）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderImportSaveVO saveImported(List<MaterialPickSummary> list) {
        WorkOrderImportSaveVO vo = new WorkOrderImportSaveVO();
        if (list == null || list.isEmpty()) {
            return vo;
        }

        // 批内按组合键去重，后者覆盖前者
        Map<String, MaterialPickSummary> unique = new LinkedHashMap<>();
        for (MaterialPickSummary p : list) {
            unique.put(DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode()), p);
        }
        List<MaterialPickSummary> rows = new ArrayList<>(unique.values());

        // 按单据号批量查已有记录，再以组合键比对
        Set<String> docs = rows.stream()
                .map(MaterialPickSummary::getDocumentNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, Long> idMap = findIdByDocAndMaterial(docs);

        List<MaterialPickSummary> toInsert = new ArrayList<>();
        List<MaterialPickSummary> toUpdate = new ArrayList<>();
        for (MaterialPickSummary p : rows) {
            Long id = idMap.get(DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode()));
            if (id == null) {
                toInsert.add(p);
            } else {
                p.setId(id);
                toUpdate.add(p);
            }
        }
        if (!toInsert.isEmpty()) {
            saveBatch(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            updateBatchById(toUpdate);
        }

        vo.setInsertCount(toInsert.size());
        vo.setUpdateCount(toUpdate.size());
        vo.setTotal(rows.size());
        return vo;
    }

    @Override
    public Map<String, Long> findIdByDocAndMaterial(Collection<String> documentNos) {
        if (documentNos == null || documentNos.isEmpty()) {
            return Map.of();
        }
        return baseMapper.selectList(
                        new QueryWrapper<MaterialPickSummary>()
                                .select("id", "document_no", "material_code")
                                .in("document_no", documentNos))
                .stream()
                .filter(p -> StringUtils.hasText(p.getDocumentNo()))
                .collect(Collectors.toMap(
                        p -> DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode()),
                        MaterialPickSummary::getId,
                        (a, b) -> a));
    }

    @Override
    public List<PickSummaryVO> listAll() {
        return baseMapper.selectList(
                        new QueryWrapper<MaterialPickSummary>().orderByDesc("pick_date").orderByAsc("seq_no"))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    private PickSummaryVO toVO(MaterialPickSummary p) {
        PickSummaryVO vo = new PickSummaryVO();
        vo.setDocumentNo(p.getDocumentNo());
        vo.setMaterialName(p.getMaterialName());
        vo.setMaterialCode(p.getMaterialCode());
        vo.setPickDate(p.getPickDate());
        vo.setPickQty(p.getPickQty());
        vo.setUnit(p.getUnit());
        vo.setImageUrl(StringUtils.hasText(p.getFileName()) ? toUrl(p.getFileName()) : null);
        return vo;
    }

    /** 根据 fileName 实时生成 MinIO 预签名访问 url */
    private String toUrl(String fileName) {
        ImgResult<String> r = imgFeignClient.getPresignedUrl(fileName);
        return r != null && r.getCode() == 200 ? r.getData() : null;
    }
}
