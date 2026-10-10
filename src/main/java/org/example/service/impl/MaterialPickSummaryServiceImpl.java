package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.MaterialDocQueryDTO;
import org.example.dto.PickSummaryGroupVO;
import org.example.dto.PickSummaryVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialPickSummary;
import org.example.mapper.MaterialPickSummaryMapper;
import org.example.service.MaterialPickSummaryService;
import org.example.util.DocMaterialKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MaterialPickSummaryServiceImpl extends ServiceImpl<MaterialPickSummaryMapper, MaterialPickSummary>
        implements MaterialPickSummaryService {

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

    /**
     * 查已有记录的领料数量，供导入预检判断重复行（单据号+物料编码+数量 三者全同 -> 跳过）。
     *
     * 这里用 HashMap 手工装填而不是 Collectors.toMap：pick_qty 列可为 null，
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
        for (MaterialPickSummary p : baseMapper.selectList(
                new QueryWrapper<MaterialPickSummary>()
                        .select("document_no", "material_code", "pick_qty")
                        .in("document_no", documentNos))) {
            if (!StringUtils.hasText(p.getDocumentNo())) {
                continue;
            }
            qtyMap.put(DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode()), p.getPickQty());
        }
        return qtyMap;
    }

    @Override
    public IPage<PickSummaryVO> listPage(MaterialDocQueryDTO query) {
        Page<MaterialPickSummary> page = new Page<>(query.resolvedPageNum(), query.resolvedPageSize());
        // convert 是先把当前页的实体映射成 VO，再连同 total / pages 一起返回 ——
        // 不会为了映射而多查一次库
        return baseMapper.selectPage(page, filterOnly(query)
                        // ⚠️ 末尾必须再按 id 兜一层：**分页要求排序键唯一**。
                        // 同一天（pick_date 相同）的记录不止一条，只按日期+序号排时
                        // MySQL 不保证两次查询的相对顺序一致 —— 翻页会漏行、也会重复行。
                        // 改造前只查一次数组再本地 slice，所以这个问题根本不会暴露，
                        // 是改后端分页才引入的新要求（实测对账对不上就是这个原因）。
                        .orderByDesc("pick_date").orderByAsc("seq_no").orderByAsc("id"))
                .convert(this::toVO);
    }

    @Override
    public List<String> listMaterialNames(DateRangeQueryDTO query) {
        // ⚠️ 用 dateRangeOnly 而不是 filterOnly：候选里**不能**带物料名称条件，
        // 否则用户选中「三氯氢硅」之后弹窗里就只剩它自己，想改选别的得先清空筛选。
        // 改造前前端也是这么算的（只按日期区间从全量里取名称去重）。
        QueryWrapper<MaterialPickSummary> wrapper = dateRangeOnly(query).select("material_name");
        return baseMapper.selectList(wrapper).stream()
                .map(MaterialPickSummary::getMaterialName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                // 按中文排序：与改造前前端的 localeCompare('zh-CN') 一致。
                // 不能交给 SQL 的 ORDER BY —— utf8mb4 默认排序规则是按码点，
                // 中文出来的顺序不是拼音顺序，与用户习惯的顺序对不上。
                .sorted(Collator.getInstance(Locale.CHINA))
                .collect(Collectors.toList());
    }

    @Override
    public List<PickSummaryGroupVO> summarizeByMaterial(MaterialDocQueryDTO query) {
        QueryWrapper<MaterialPickSummary> wrapper = filterOnly(query)
                .select("material_code", "material_name", "SUM(pick_qty) AS qty")
                .groupBy("material_code", "material_name")
                // 排序键用普通列（MyBatis-Plus 的 orderBy* 是把字符串原样拼进 ORDER BY 的，
                // 传聚合表达式其实也能生成合法 SQL）；按名称、编码排就是为了结果确定，
                // 前端拿到后本来还要按名称排一次
                .orderByAsc("material_name").orderByAsc("material_code");

        return baseMapper.selectMaps(wrapper).stream()
                .map(this::toGroupVO)
                .collect(Collectors.toList());
    }

    /**
     * selectMaps 的结果集（列标签 → 值）转成聚合行。
     *
     * <p>分组键里的 null 用空串兜底（与改造前前端 {@code String(x ?? '')} 同口径），
     * 否则前端 Map 的 key 会变成字符串 "null"。
     */
    private PickSummaryGroupVO toGroupVO(Map<String, Object> row) {
        PickSummaryGroupVO vo = new PickSummaryGroupVO();
        vo.setMaterialCode(trimToEmpty(row.get("material_code")));
        vo.setMaterialName(trimToEmpty(row.get("material_name")));
        // SUM 在「该组所有 pick_qty 都是 NULL」时回 NULL，不是 0
        Object qty = row.get("qty");
        vo.setPickQty(qty == null ? BigDecimal.ZERO : new BigDecimal(qty.toString()));
        return vo;
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 只带筛选条件（日期区间 + 物料名称 / 编码 / 单据号）的 wrapper，不带排序。
     *
     * <p>抽出来是因为三个查询条件完全相同、只有「排序」和「取哪些列」不同：
     * 各写一遍迟早会漏掉刚加的那个条件（改造前的典型症状就是「这个页能筛、那个页筛不动」）。
     *
     * <p>日期用 {@code ge}/{@code le} 而不是字符串比较：DATE 列走原生比较，
     * 顺便把 {@code pick_date} 为 NULL 的行排除掉 —— 与改造前前端
     * {@code if (!pickDate) return false} 的口径一致（这条**和周统计相反**，那边设计上要计入，
     * 见 WeeklyStatsServiceImpl#inRange，别把两者的口径「统一」掉）。
     */
    private QueryWrapper<MaterialPickSummary> filterOnly(MaterialDocQueryDTO query) {
        QueryWrapper<MaterialPickSummary> wrapper = new QueryWrapper<>();
        LocalDate start = query.parsedStartDate();
        LocalDate end = query.parsedEndDate();
        if (start != null) {
            wrapper.ge("pick_date", start);
        }
        if (end != null) {
            wrapper.le("pick_date", end);
        }
        if (StringUtils.hasText(query.getMaterialName())) {
            wrapper.eq("material_name", query.getMaterialName().trim());
        }
        if (StringUtils.hasText(query.getMaterialCode())) {
            wrapper.eq("material_code", query.getMaterialCode().trim());
        }
        if (StringUtils.hasText(query.getDocumentNo())) {
            wrapper.eq("document_no", query.getDocumentNo().trim());
        }
        return wrapper;
    }

    /** 只带日期区间的 wrapper —— 给「物料名称候选」用，理由见 {@link #listMaterialNames} */
    private QueryWrapper<MaterialPickSummary> dateRangeOnly(DateRangeQueryDTO query) {
        QueryWrapper<MaterialPickSummary> wrapper = new QueryWrapper<>();
        LocalDate start = query.parsedStartDate();
        LocalDate end = query.parsedEndDate();
        if (start != null) {
            wrapper.ge("pick_date", start);
        }
        if (end != null) {
            wrapper.le("pick_date", end);
        }
        return wrapper;
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
