package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.ProductionInboundVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.ProductionInbound;
import org.example.mapper.ProductionInboundMapper;
import org.example.service.ProductionInboundService;
import org.example.util.DocMaterialKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductionInboundServiceImpl extends ServiceImpl<ProductionInboundMapper, ProductionInbound>
        implements ProductionInboundService {

    /** 图片对外基础路径：同源部署留空，前端拿到相对路径（契约 2.3） */
    @Value("${image.base-url:}")
    private String imageBaseUrl;

    /**
     * 按 (单据号 + 物料编码) upsert：存在则更新，不存在则新增。
     * 同一张单据下可以有多条不同物料的明细，因此单据号单独不唯一。
     * 同批内组合键重复时后者覆盖前者（避免批内自冲突触发唯一键异常）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderImportSaveVO saveImported(List<ProductionInbound> list) {
        WorkOrderImportSaveVO vo = new WorkOrderImportSaveVO();
        if (list == null || list.isEmpty()) {
            return vo;
        }

        // 批内按组合键去重，后者覆盖前者
        Map<String, ProductionInbound> unique = new LinkedHashMap<>();
        for (ProductionInbound p : list) {
            unique.put(DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode()), p);
        }
        List<ProductionInbound> rows = new ArrayList<>(unique.values());

        // 按单据号批量查已有记录，再以组合键比对
        Set<String> docs = rows.stream()
                .map(ProductionInbound::getDocumentNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, Long> idMap = findIdByDocAndMaterial(docs);

        List<ProductionInbound> toInsert = new ArrayList<>();
        List<ProductionInbound> toUpdate = new ArrayList<>();
        for (ProductionInbound p : rows) {
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
                        new QueryWrapper<ProductionInbound>()
                                .select("id", "document_no", "material_code")
                                .in("document_no", documentNos))
                .stream()
                .filter(p -> StringUtils.hasText(p.getDocumentNo()))
                .collect(Collectors.toMap(
                        p -> DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode()),
                        ProductionInbound::getId,
                        (a, b) -> a));
    }

    /**
     * 查已有记录的入库数量，供导入预检判断重复行（单据号+物料编码+数量 三者全同 -> 跳过）。
     *
     * 这里用 HashMap 手工装填而不是 Collectors.toMap：inbound_qty 列可为 null，
     * 而 toMap 底层走 map.merge，value 为 null 会直接抛 NPE。
     * 而且必须让「数量为 null 的已有记录」也留在 map 里（value 为 null），
     * 否则该记录会被误判成「库里不存在」，保存时走 insert 撞唯一键。
     */
    @Override
    public Map<String, BigDecimal> findQtyByDocAndMaterial(Collection<String> documentNos) {
        if (documentNos == null || documentNos.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> qtyMap = new HashMap<>();
        for (ProductionInbound p : baseMapper.selectList(
                new QueryWrapper<ProductionInbound>()
                        .select("document_no", "material_code", "inbound_qty")
                        .in("document_no", documentNos))) {
            if (!StringUtils.hasText(p.getDocumentNo())) {
                continue;
            }
            qtyMap.put(DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode()), p.getInboundQty());
        }
        return qtyMap;
    }

    @Override
    public List<ProductionInboundVO> listAll() {
        return baseMapper.selectList(
                        new QueryWrapper<ProductionInbound>().orderByDesc("inbound_date").orderByAsc("seq_no"))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    private ProductionInboundVO toVO(ProductionInbound p) {
        ProductionInboundVO vo = new ProductionInboundVO();
        vo.setDocumentNo(p.getDocumentNo());
        vo.setMaterialName(p.getMaterialName());
        vo.setMaterialCode(p.getMaterialCode());
        vo.setInboundDate(p.getInboundDate());
        vo.setInboundQty(p.getInboundQty());
        vo.setUnit(p.getUnit());
        String fileName = p.getFileName();
        vo.setImageUrl(toUrl(fileName));
        vo.setThumbnailUrl(toThumbUrl(fileName));
        return vo;
    }

    /**
     * 原图：固定 URL，纯字符串拼接。
     *
     * 不再调用 Feign：预签名 URL 每次都带新的 X-Amz-Date，浏览器按 URL 缓存，
     * URL 一变就全部重下；且列表组装时会逐张远程调用（变更-001 根因 2、3）。
     */
    private String toUrl(String fileName) {
        return StringUtils.hasText(fileName) ? imageBaseUrl + "/files/" + fileName : null;
    }

    /** 缩略图：固定 URL */
    private String toThumbUrl(String fileName) {
        return StringUtils.hasText(fileName) ? imageBaseUrl + "/thumbs/" + fileName : null;
    }
}
