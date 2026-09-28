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

import java.util.List;
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

    private MaterialVO toVO(MaterialMaster material) {
        MaterialVO vo = new MaterialVO();
        vo.setCode(material.getMaterialCode());
        vo.setName(material.getMaterialName());
        vo.setSpec(material.getSpec());
        vo.setUnit(material.getUnit());
        return vo;
    }
}
