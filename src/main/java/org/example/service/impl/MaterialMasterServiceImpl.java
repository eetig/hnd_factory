package org.example.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.MaterialMatchVO;
import org.example.dto.MaterialVO;
import org.example.entity.MaterialMaster;
import org.example.mapper.MaterialMasterMapper;
import org.example.service.MaterialMasterService;
import org.example.util.MaterialMatcher;
import org.example.util.MaterialNameNormalizer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 物料主数据查询实现。
 *
 * <p>本类只负责「取数 + 组装」，匹配与排序规则全在 {@link MaterialMatcher}（纯函数、可单测）。
 *
 * <p>每次全量取回启用的行在内存里匹配：表仅 244 行，查询开销可忽略，
 * 换来的是「归一化与匹配只有一份实现」—— 不在库里存归一化列，避免 SQL 里的值与代码里的
 * 实现各存一份、日后不一致。
 */
@Service
public class MaterialMasterServiceImpl extends ServiceImpl<MaterialMasterMapper, MaterialMaster>
        implements MaterialMasterService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    @Override
    public MaterialMatchVO match(String name) {
        MaterialMatchVO vo = new MaterialMatchVO();
        String keyword = MaterialNameNormalizer.normalize(name);
        if (keyword.isEmpty()) {
            return vo;
        }

        List<MaterialMaster> all = listEnabled();

        // 只有唯一命中才自动填。多条说明该名称确实对应多个物料（源数据里有 6 组），交人工判断；
        // 0 条则按约定留空，由前端展示 candidates 供选择。
        List<MaterialMaster> exact = MaterialMatcher.findExact(all, keyword);
        if (exact.size() == 1) {
            vo.setMatched(toVO(exact.get(0)));
        }

        vo.setCandidates(MaterialMatcher.rank(all, keyword, DEFAULT_LIMIT, false).stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    public List<MaterialVO> search(String keyword, Integer limit) {
        String normalized = MaterialNameNormalizer.normalize(keyword);
        if (normalized.isEmpty()) {
            return List.of();
        }
        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return MaterialMatcher.rank(listEnabled(), normalized, size, true).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /** 停用的行保留在库里但不再参与匹配（便于排查「为什么这条查不到」） */
    private List<MaterialMaster> listEnabled() {
        return lambdaQuery().eq(MaterialMaster::getEnabled, 1).list();
    }

    @Override
    public Map<String, MaterialMaster> mapFirstByCode() {
        Map<String, MaterialMaster> byCode = new HashMap<>();
        // 全量取回（表仅数百行）：与 match/search 同一套思路 —— 不在 SQL 里做归一化/取舍，
        // 规则只留一份 Java 实现
        for (MaterialMaster material : baseMapper.selectList(null)) {
            String code = material.getMaterialCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            MaterialMaster exist = byCode.get(code);
            if (exist == null || prefer(material, exist)) {
                byCode.put(code, material);
            }
        }
        return byCode;
    }

    /** 同一编码多个名称时取哪条：先看启用，再比 id（取小的）—— 只为确定性 */
    private boolean prefer(MaterialMaster candidate, MaterialMaster current) {
        boolean candidateEnabled = isEnabled(candidate);
        if (candidateEnabled != isEnabled(current)) {
            return candidateEnabled;
        }
        Long candidateId = candidate.getId();
        Long currentId = current.getId();
        return candidateId != null && (currentId == null || candidateId < currentId);
    }

    private boolean isEnabled(MaterialMaster material) {
        return material.getEnabled() != null && material.getEnabled() == 1;
    }

    private MaterialVO toVO(MaterialMaster material) {
        MaterialVO vo = new MaterialVO();
        vo.setCode(material.getMaterialCode());
        vo.setName(material.getMaterialName());
        vo.setSpec(material.getSpec());
        vo.setUnit(material.getUnit());
        return vo;
    }
}
