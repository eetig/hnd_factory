package org.example.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.component.MaterialImportCache;
import org.example.dto.MaterialImportPreviewVO;
import org.example.dto.MaterialImportResultVO;
import org.example.dto.MaterialImportRow;
import org.example.entity.MaterialMaster;
import org.example.mapper.MaterialMasterMapper;
import org.example.service.MaterialImportService;
import org.example.util.MaterialNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 物料主数据导入实现（2026-10-23，变更-159）。
 *
 * <p>文件就是 SAP 导出原样（17 列，一行 = 编码 × 评估类型），不做列映射配置 ——
 * 列名固定，配一套映射规则反而多一处会漂的地方。缺列时直接拒绝并说出缺哪个。
 *
 * <p>三种差异口径：
 * <ul>
 *   <li><b>新增</b>：文件里有、库里没有这个 (编码, 评估类型)；</li>
 *   <li><b>变更</b>：键都在，但名称/类型/组/单位/采购组/MRP/评估类/价格控制/价格/价格单位/货币/状态
 *       任何一项变了；</li>
 *   <li><b>消失</b>：库里有、文件里没有 → <b>软停用</b>（status=0），不删除。</li>
 * </ul>
 */
@Service
public class MaterialImportServiceImpl implements MaterialImportService {

    private static final Logger log = LoggerFactory.getLogger(MaterialImportServiceImpl.class);

    /** 必需列：缺了就直接拒绝，并告诉人缺哪一列 */
    private static final List<String> REQUIRED_HEADERS = List.of("物料", "物料描述", "物料类型");
    /** 每类差异给多少行样例 */
    private static final int SAMPLE_SIZE = 20;
    /** 批量 upsert 的批大小（一条多值 INSERT，控制单条 SQL 的体积） */
    private static final int UPSERT_CHUNK = 1000;
    private static final int DISABLE_CHUNK = 500;

    @Autowired
    private MaterialMasterMapper materialMasterMapper;

    @Autowired
    private MaterialImportCache importCache;

    // ============================ preview ============================

    @Override
    public MaterialImportPreviewVO preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 Excel 文件。");
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "物料清单.xlsx";

        Parsed parsed = parse(file);
        Map<String, MaterialMaster> existing = loadExistingSnapshot();

        MaterialImportPreviewVO vo = new MaterialImportPreviewVO();
        vo.setFileName(fileName);
        vo.setHeaders(parsed.headers);
        vo.setWarnings(parsed.warnings);
        vo.setTotalRows(parsed.rows.size());

        List<MaterialImportPreviewVO.Sample> newSamples = new ArrayList<>();
        List<MaterialImportPreviewVO.Sample> changedSamples = new ArrayList<>();
        int created = 0;
        int changed = 0;
        for (MaterialImportRow row : parsed.rows) {
            MaterialMaster old = existing.get(row.key());
            if (old == null) {
                created++;
                if (newSamples.size() < SAMPLE_SIZE) {
                    newSamples.add(sample(row, null));
                }
                continue;
            }
            String diff = diffOf(old, row);
            if (diff != null) {
                changed++;
                if (changedSamples.size() < SAMPLE_SIZE) {
                    changedSamples.add(sample(row, diff));
                }
            }
        }

        List<MaterialImportPreviewVO.Sample> removedSamples = new ArrayList<>();
        int removed = 0;
        for (MaterialMaster old : existing.values()) {
            if (parsed.keys.containsKey(old.getMaterialCode() + "|"
                    + (old.getValuationType() == null ? "" : old.getValuationType()))) {
                continue;
            }
            removed++;
            if (removedSamples.size() < SAMPLE_SIZE) {
                MaterialImportPreviewVO.Sample sample = new MaterialImportPreviewVO.Sample();
                sample.setCode(old.getMaterialCode());
                sample.setValuationType(old.getValuationType());
                sample.setName(old.getMaterialName());
                sample.setDiff(old.getStatus() != null && old.getStatus() == 0 ? "已停用（保持）" : "将转停用");
                removedSamples.add(sample);
            }
        }

        vo.setNewCount(created);
        vo.setChangedCount(changed);
        vo.setRemovedCount(removed);
        vo.setUnchangedCount(parsed.rows.size() - created - changed);
        vo.setNewSamples(newSamples);
        vo.setChangedSamples(changedSamples);
        vo.setRemovedSamples(removedSamples);
        vo.setToken(importCache.put(parsed.rows, fileName));

        log.info("物料导入预览：file={} rows={} new={} changed={} removed={}",
                fileName, vo.getTotalRows(), vo.getNewCount(), vo.getChangedCount(), vo.getRemovedCount());
        return vo;
    }

    private MaterialImportPreviewVO.Sample sample(MaterialImportRow row, String diff) {
        MaterialImportPreviewVO.Sample sample = new MaterialImportPreviewVO.Sample();
        sample.setCode(row.getCode());
        sample.setValuationType(row.getValuationType());
        sample.setName(row.getName());
        sample.setDiff(diff);
        return sample;
    }

    // ============================ commit ============================

    @Override
    public MaterialImportResultVO commit(String token) {
        MaterialImportCache.CachedImport cached = importCache.take(token);
        if (cached == null) {
            throw new IllegalArgumentException("导入会话已过期（30 分钟），请重新上传文件。");
        }

        Map<String, MaterialMaster> existing = loadExistingSnapshot();
        Map<String, Boolean> keys = new HashMap<>();
        for (MaterialImportRow row : cached.rows) {
            keys.put(row.key(), Boolean.TRUE);
        }

        LocalDateTime now = LocalDateTime.now();
        List<MaterialMaster> upserts = new ArrayList<>();
        int inserted = 0;
        int updated = 0;
        int pendingDisabled = 0;

        for (MaterialImportRow row : cached.rows) {
            MaterialMaster old = existing.get(row.key());
            if (old != null && diffOf(old, row) == null) {
                continue; // 一行没变：不发 SQL
            }
            MaterialMaster entity = toEntity(row, cached.fileName, now);
            if (old != null) {
                entity.setId(old.getId());
                updated++;
            } else {
                inserted++;
            }
            if (row.getStatus() != null && row.getStatus() == 2) {
                pendingDisabled++;
            }
            upserts.add(entity);
        }

        // 消失的键：软停用。已经停用的不重复发 SQL
        List<Long> disableIds = new ArrayList<>();
        for (MaterialMaster old : existing.values()) {
            String key = old.getMaterialCode() + "|" + (old.getValuationType() == null ? "" : old.getValuationType());
            if (keys.containsKey(key)) {
                continue;
            }
            if (old.getStatus() != null && old.getStatus() != 0) {
                disableIds.add(old.getId());
            }
        }

        for (int i = 0; i < upserts.size(); i += UPSERT_CHUNK) {
            materialMasterMapper.upsertBatch(upserts.subList(i, Math.min(i + UPSERT_CHUNK, upserts.size())));
        }
        for (int i = 0; i < disableIds.size(); i += DISABLE_CHUNK) {
            materialMasterMapper.disableBatch(disableIds.subList(i, Math.min(i + DISABLE_CHUNK, disableIds.size())));
        }

        MaterialImportResultVO result = new MaterialImportResultVO();
        result.setFileName(cached.fileName);
        result.setInserted(inserted);
        result.setUpdated(updated);
        result.setDisabled(disableIds.size());
        result.setPendingDisabled(pendingDisabled);

        log.info("物料导入完成：file={} inserted={} updated={} disabled={}",
                cached.fileName, inserted, updated, disableIds.size());
        return result;
    }

    // ============================ 解析 ============================

    private static final class Parsed {
        private List<String> headers = List.of();
        private List<String> warnings = List.of();
        private List<MaterialImportRow> rows = List.of();
        private Map<String, Boolean> keys = Map.of();
    }

    private Parsed parse(MultipartFile file) {
        List<Map<Integer, String>> raw;
        try (InputStream in = file.getInputStream()) {
            raw = EasyExcel.read(in).sheet(0).headRowNumber(0).doReadSync();
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 解析失败：" + e.getMessage() + "（只支持 .xlsx / .xls）");
        }
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("文件里没有数据。");
        }

        Map<Integer, String> headRow = raw.get(0);
        List<String> headers = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        for (Map.Entry<Integer, String> entry : headRow.entrySet()) {
            String name = clean(entry.getValue());
            while (headers.size() <= entry.getKey()) {
                headers.add("");
            }
            headers.set(entry.getKey(), name);
            if (StringUtils.hasText(name)) {
                index.putIfAbsent(name, entry.getKey());
            }
        }
        for (String required : REQUIRED_HEADERS) {
            if (!index.containsKey(required)) {
                throw new IllegalArgumentException("缺少必需的列「" + required + "」。"
                        + "请上传 SAP 导出的原样文件（表头需包含：物料、物料描述、物料类型）。");
            }
        }

        List<String> warnings = new ArrayList<>();
        List<MaterialImportRow> rows = new ArrayList<>();
        Map<String, Boolean> keys = new HashMap<>();
        int duplicated = 0;

        for (int r = 1; r < raw.size(); r++) {
            Map<Integer, String> line = raw.get(r);
            if (line == null || line.isEmpty()) {
                continue;
            }
            String code = clean(value(line, index, "物料"));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            MaterialImportRow row = new MaterialImportRow();
            row.setCode(code);
            row.setValuationType(defaultString(value(line, index, "评估类型")));
            row.setName(defaultString(value(line, index, "物料描述")));
            row.setType(defaultString(value(line, index, "物料类型")));
            row.setGroup(defaultString(value(line, index, "物料组")));
            row.setUnit(defaultString(value(line, index, "基本计量单位")));
            row.setProcurementGroup(defaultString(value(line, index, "采购组")));
            row.setMrpType(defaultString(value(line, index, "MRP 类型")));
            row.setValuationClass(defaultString(value(line, index, "ValCl")));
            row.setPriceControl(defaultString(value(line, index, "价格控制")));
            row.setPrice(parseDecimal(value(line, index, "价格")));
            row.setPriceUnit(parseInt(value(line, index, "价格单位"), 1));
            row.setCurrency(defaultIfBlank(value(line, index, "货币"), "CNY"));
            row.setPlant(defaultString(value(line, index, "工厂")));
            row.setCreator(defaultString(value(line, index, "创建人")));
            row.setChangedAt(normalizeDate(value(line, index, "上次更改")));
            row.setStatus(statusOf(row.getName()));
            row.setSourceRow(r + 1);

            if (!StringUtils.hasText(row.getType())) {
                warnings.add("第 " + (r + 1) + " 行没有物料类型：" + code);
            }
            if (keys.put(row.key(), Boolean.TRUE) != null) {
                duplicated++;
            }
            rows.add(row);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("表头之后没有有效数据行。");
        }
        if (duplicated > 0) {
            warnings.add("文件里有 " + duplicated + " 行「编码 + 评估类型」重复，按最后一行生效。");
        }

        Parsed parsed = new Parsed();
        parsed.headers = headers;
        parsed.warnings = warnings.size() > 20 ? warnings.subList(0, 20) : warnings;
        parsed.rows = dedupe(rows);
        parsed.keys = keys;
        return parsed;
    }

    /** 同键重复时保留最后一行（与 SAP 导出「后写覆盖」的直觉一致） */
    private List<MaterialImportRow> dedupe(List<MaterialImportRow> rows) {
        Map<String, MaterialImportRow> map = new LinkedHashMap<>();
        for (MaterialImportRow row : rows) {
            map.put(row.key(), row);
        }
        return new ArrayList<>(map.values());
    }

    // ============================ 差异 ============================

    private Map<String, MaterialMaster> loadExistingSnapshot() {
        List<MaterialMaster> all = materialMasterMapper.selectList(new QueryWrapper<MaterialMaster>()
                .select("id", "material_code", "valuation_type", "material_name", "material_type",
                        "material_group", "unit", "procurement_group", "mrp_type", "valuation_class",
                        "price_control", "price", "price_unit", "currency", "creator", "changed_at", "status"));
        Map<String, MaterialMaster> map = new HashMap<>(all.size() * 2);
        for (MaterialMaster material : all) {
            map.put(material.getMaterialCode() + "|"
                    + (material.getValuationType() == null ? "" : material.getValuationType()), material);
        }
        return map;
    }

    /**
     * 比对一行；返回 null 表示没变（列表里只有人要看的字段，不把 20 个字段全铺出来）。
     */
    private String diffOf(MaterialMaster old, MaterialImportRow row) {
        List<String> parts = new ArrayList<>();
        // 名称按归一化值比较：库里存的可能是带尾部全角空格的原文，而系统各处一律按
        // normalize() 判等 —— 用 trim() 比会把这些「只差空白」的行报成变更（实测 2 行）
        if (!sameName(old.getMaterialName(), row.getName())) {
            parts.add("名称「" + brief(old.getMaterialName()) + "」→「" + brief(row.getName()) + "」");
        }
        if (!same(old.getMaterialType(), row.getType())) {
            parts.add("类型 " + old.getMaterialType() + " → " + row.getType());
        }
        if (!same(old.getMaterialGroup(), row.getGroup())) {
            parts.add("物料组 " + old.getMaterialGroup() + " → " + row.getGroup());
        }
        if (!same(old.getUnit(), row.getUnit())) {
            parts.add("单位 " + old.getUnit() + " → " + row.getUnit());
        }
        if (!same(old.getProcurementGroup(), row.getProcurementGroup())) {
            parts.add("采购组 " + old.getProcurementGroup() + " → " + row.getProcurementGroup());
        }
        if (!same(old.getMrpType(), row.getMrpType())) {
            parts.add("MRP " + old.getMrpType() + " → " + row.getMrpType());
        }
        if (!same(old.getValuationClass(), row.getValuationClass())) {
            parts.add("评估类 " + old.getValuationClass() + " → " + row.getValuationClass());
        }
        if (!same(old.getPriceControl(), row.getPriceControl())) {
            parts.add("价格控制 " + old.getPriceControl() + " → " + row.getPriceControl());
        }
        if (!samePrice(old.getPrice(), row.getPrice())) {
            parts.add("价格 " + money(old.getPrice()) + " → " + money(row.getPrice()));
        }
        if (!Objects.equals(old.getPriceUnit(), row.getPriceUnit())) {
            parts.add("价格单位 " + old.getPriceUnit() + " → " + row.getPriceUnit());
        }
        if (!same(old.getCurrency(), row.getCurrency())) {
            parts.add("货币 " + old.getCurrency() + " → " + row.getCurrency());
        }
        if (!Objects.equals(old.getStatus(), row.getStatus())) {
            parts.add("状态 " + statusText(old.getStatus()) + " → " + statusText(row.getStatus()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("；", parts.subList(0, Math.min(3, parts.size())))
                + (parts.size() > 3 ? " 等 " + parts.size() + " 项" : "");
    }

    private MaterialMaster toEntity(MaterialImportRow row, String fileName, LocalDateTime now) {
        MaterialMaster entity = new MaterialMaster();
        entity.setMaterialCode(row.getCode());
        entity.setValuationType(row.getValuationType() == null ? "" : row.getValuationType());
        entity.setMaterialName(row.getName());
        entity.setNameNorm(MaterialNameNormalizer.normalize(row.getName()));
        entity.setUnit(row.getUnit());
        entity.setMaterialType(row.getType());
        entity.setMaterialGroup(row.getGroup());
        entity.setProcurementGroup(row.getProcurementGroup());
        entity.setMrpType(row.getMrpType());
        entity.setValuationClass(row.getValuationClass());
        entity.setPriceControl(row.getPriceControl());
        entity.setPrice(row.getPrice());
        entity.setPriceUnit(row.getPriceUnit() == null ? 1 : row.getPriceUnit());
        entity.setCurrency(row.getCurrency());
        entity.setPlant(row.getPlant());
        entity.setCreator(row.getCreator());
        entity.setChangedAt(parseDate(row.getChangedAt()));
        entity.setStatus(row.getStatus());
        entity.setEnabled(row.getStatus() != null && row.getStatus() == 0 ? 0 : 1);
        entity.setSourceSheet("Sheet1");
        entity.setSourceRow(row.getSourceRow());
        entity.setSourceFile(fileName);
        entity.setImportedAt(now);
        return entity;
    }

    // ============================ 小工具 ============================

    /** 停用标记推导：SAP 导出没有状态列，只能从名称里读（与 docs/schema.sql 第 25 步同一规则） */
    static int statusOf(String name) {
        String text = name == null ? "" : name.trim();
        if ("禁用".equals(text) || "作废".equals(text)) {
            return 0;
        }
        if (text.contains("待禁用")) {
            return 2;
        }
        if (text.contains("禁用") || text.contains("作废") || text.contains("勿用") || text.contains("停用")) {
            return 0;
        }
        return 1;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case 0 -> "停用";
            case 2 -> "待停用";
            default -> "正常";
        };
    }

    private static String value(Map<Integer, String> line, Map<String, Integer> index, String header) {
        Integer column = index.get(header);
        if (column == null) {
            return null;
        }
        return line.get(column);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String defaultString(String value) {
        String text = clean(value);
        return text.isEmpty() ? null : text;
    }

    private static String defaultIfBlank(String value, String fallback) {
        String text = clean(value);
        return text.isEmpty() ? fallback : text;
    }

    private static boolean sameName(String a, String b) {
        return comparableName(a).equals(comparableName(b));
    }

    /**
     * 比对用的名称：在 {@link MaterialNameNormalizer} 之上再抹掉「不换行空格」。
     *
     * <p>为什么要多这一步：SAP 导出的名称里有 NBSP（U+00A0，实测 111002221 的
     * 「…盐酸盐&#92;u00A0v50」、还有行尾带一个 NBSP 的）。Java 的 trim() 不认它，
     * Character.isWhitespace 也不认它 —— 于是「只差一个 NBSP」的两行会被报成变更。
     * 只影响比对口径，不改库里的原文，也不动匹配用的归一化规则。
     */
    private static String comparableName(String text) {
        return MaterialNameNormalizer.normalize(text)
                .replace("\u00A0", "").replace("\u2007", "").replace("\u202F", "");
    }

    private static boolean same(String a, String b) {
        return Objects.equals(clean(a), clean(b));
    }

    private static boolean samePrice(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    private static String brief(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 18 ? text : text.substring(0, 18) + "…";
    }

    private static String money(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal parseDecimal(String value) {
        String text = clean(value).replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value, Integer fallback) {
        String text = clean(value);
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** EasyExcel 读出来可能是 "2025/11/30"、"2025-11-30 00:00:00"、也可能是 Excel 序列号 */
    private static String normalizeDate(String value) {
        String text = clean(value);
        if (text.isEmpty()) {
            return null;
        }
        if (text.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")) {
            return text.substring(0, 10).replace('/', '-');
        }
        return null;
    }

    private static LocalDate parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (Exception e) {
            return null;
        }
    }
}
