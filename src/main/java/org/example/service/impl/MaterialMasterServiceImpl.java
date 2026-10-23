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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * SQL 先筛出来的候选上限（交给 MaterialMatcher 排序的那一批）。
     *
     * <p>为什么要有这个数（2026-10-23，变更-159）：主数据从 245 行涨到 30,434 行，
     * 原来是「全表读进内存再排序」——实测一次 /match 从 ~30ms 涨到 **2.1 秒**，
     * 而图片解析是**每识别出一行就调一次**，整张单子会卡到不可用。
     * 200 是「够排序选出前 20」与「不把 Java 侧变成第二个全表扫描」的折中。
     */
    private static final int CANDIDATE_LIMIT = 200;

    /** 兜底档（共同字符）的候选上限：只在正常候选为空时才查一次 */
    private static final int FALLBACK_LIMIT = 50;

    @Override
    public MaterialMatchVO match(String name) {
        MaterialMatchVO vo = new MaterialMatchVO();
        String keyword = MaterialNameNormalizer.normalize(name);
        if (keyword.isEmpty()) {
            return vo;
        }

        // 1) 归一化全等：走 idx_name_norm 等值查询，不再扫全表
        //    ⚠️ 判"唯一"要按**物料编码**去重，不能按行数：罐类物料一个编码有 8 行
        //    （主档 + 7 个评估类型），按行数算会永远"不唯一"，导致图片解析永远填不上编码
        List<MaterialMaster> exact = dedupeByCode(findByNormalizedName(keyword));
        if (exact.size() == 1) {
            vo.setMatched(toVO(exact.get(0)));
        }

        // 2) 候选：SQL 先收窄到 CANDIDATE_LIMIT 条，再由 MaterialMatcher 排序
        //    （分档规则一行没改，改的只是喂给它的数据量）
        List<MaterialMaster> candidates = exact.isEmpty()
                ? dedupeByCode(searchCandidates(keyword, CANDIDATE_LIMIT))
                : exact;
        vo.setCandidates(MaterialMatcher.rank(candidates, keyword, DEFAULT_LIMIT, false).stream()
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
        // 候选先由 SQL 收窄；真的一条都没命中时，才用前两个字做一次兜底查询 ——
        // 对应 MaterialMatcher 第 4 档「共同字符」的语义（它本来就只在 1–3 档全空时才返回）
        List<MaterialMaster> candidates = searchCandidates(normalized, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            candidates = searchCandidates(normalized.substring(0, Math.min(2, normalized.length())), FALLBACK_LIMIT);
        }
        return MaterialMatcher.rank(dedupeByCode(candidates), normalized, size, true).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 按物料编码去重，同编码优先保留**主档**那一行（评估类型为空）。
     *
     * <p>为什么要在服务层去重：库里一个编码可能有多行（评估类型维度），而
     * {@link MaterialVO} 只承载"一个物料"的信息（编码/名称/单位）。不去重的话，
     * 搜「吨桶」会返回 8 条一模一样的候选，20 个候选位被一个物料吃掉 8 个。
     * 评估类型的价格是详情页的事，不参与列表候选。
     */
    // 包级可见 + 静态：便于单测直接钉「8 行同编码只出一个物料」这条规则
    static List<MaterialMaster> dedupeByCode(List<MaterialMaster> rows) {
        Map<String, MaterialMaster> byCode = new LinkedHashMap<>();
        for (MaterialMaster row : rows) {
            String code = row.getMaterialCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            MaterialMaster exist = byCode.get(code);
            if (exist == null || (isMainRow(row) && !isMainRow(exist))) {
                byCode.put(code, row);
            }
        }
        return new ArrayList<>(byCode.values());
    }

    static boolean isMainRow(MaterialMaster row) {
        return row.getValuationType() == null || row.getValuationType().isBlank();
    }

    /** 归一化名全等：走 idx_name_norm；只认启用行（与 match 的语义一致） */
    private List<MaterialMaster> findByNormalizedName(String normalized) {
        return lambdaQuery()
                .eq(MaterialMaster::getNameNorm, normalized)
                .eq(MaterialMaster::getEnabled, 1)
                .list();
    }

    /**
     * SQL 侧候选：名称包含 或 编码前缀 命中，只取启用行，最多 {@code limit} 条。
     *
     * <p>为什么是"名称包含 + 编码前缀"：名字与编码是用户唯一会输入的两样东西。
     * 名称走 `LIKE '%kw%'`（30k 行实测 ~0.09ms，见变更-159 的基准），编码走前缀以吃到索引。
     * <b>不做</b>规格匹配 —— 新主数据里没有规格列，写了也是永远匹配不中。
     */
    private List<MaterialMaster> searchCandidates(String keyword, int limit) {
        return lambdaQuery()
                .eq(MaterialMaster::getEnabled, 1)
                .and(w -> w.like(MaterialMaster::getNameNorm, keyword)
                        .or()
                        .likeRight(MaterialMaster::getMaterialCode, keyword))
                .last("LIMIT " + limit)
                .list();
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
