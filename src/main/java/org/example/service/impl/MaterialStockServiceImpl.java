package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.ImportSaveOutcome;
import org.example.dto.MaterialStockVO;
import org.example.entity.MaterialMaster;
import org.example.entity.MaterialStock;
import org.example.mapper.MaterialStockMapper;
import org.example.service.MaterialMasterService;
import org.example.service.MaterialStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MaterialStockServiceImpl extends ServiceImpl<MaterialStockMapper, MaterialStock>
        implements MaterialStockService {

    /** 联查物料名称与规格用 */
    @Autowired
    private MaterialMasterService materialMasterService;

    @Override
    public List<MaterialStockVO> listAll() {
        List<MaterialStock> rows = baseMapper.selectList(new QueryWrapper<MaterialStock>()
                .orderByAsc("plant_code")
                .orderByAsc("storage_location")
                .orderByAsc("material_code"));

        // 联查主数据：页面要的是主数据里的规范名称与规格（按物料编码取），
        // 主数据是全量取回后在内存里对（表仅数百行），见 MaterialMasterService#mapFirstByCode
        Map<String, MaterialMaster> masterByCode = materialMasterService.mapFirstByCode();

        Set<String> codesWithStock = new HashSet<>();
        List<MaterialStockVO> result = rows.stream().map(row -> {
            codesWithStock.add(row.getMaterialCode());
            MaterialMaster master = masterByCode.get(row.getMaterialCode());
            MaterialStockVO vo = new MaterialStockVO();
            vo.setId(row.getId());
            vo.setPlantCode(row.getPlantCode());
            vo.setMaterialCode(row.getMaterialCode());
            // 物料信息分在两处：主数据（规范名称/规格）+ 库存表（SAP 导出的描述/规格型号）。
            // 显示口径：**主数据优先，没有就回退库存表那份**，两边都没有留 null（前端显示「/」）。
            // 不回退的话，主数据只覆盖 589 个库存编码里的 30 个 —— 页面会是一片「/」。
            vo.setMaterialName(firstText(master == null ? null : master.getMaterialName(), row.getMaterialDesc()));
            vo.setSpec(firstText(master == null ? null : master.getSpec(), row.getSpec()));
            vo.setStorageLocation(row.getStorageLocation());
            vo.setStorageDesc(row.getStorageDesc());
            vo.setUnit(row.getUnit());
            vo.setStockQty(row.getStockQty());
            return vo;
        }).collect(Collectors.toList());

        // 只有主数据、库存汇总里没有的物料也要能查到（使用方口径：搜 V150 时这类物料要出现，
        // 有编码/名称/规格，库存那几列由前端显示「/」）。
        // 排在后面：「有库存的」在前，「只有主数据的」在后，扫一眼就知道哪些还没进过库存表。
        masterByCode.values().stream()
                .filter(master -> !codesWithStock.contains(master.getMaterialCode()))
                .sorted(Comparator.comparing(MaterialMaster::getMaterialCode,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(master -> {
                    MaterialStockVO vo = new MaterialStockVO();
                    vo.setMaterialCode(master.getMaterialCode());
                    vo.setMaterialName(firstText(master.getMaterialName(), null));
                    vo.setSpec(firstText(master.getSpec(), null));
                    // 存储地点 / 单位 / 数量 / 存储地点描述 一律留 null —— 这些本来就来自库存表
                    result.add(vo);
                });

        return result;
    }

    /** 取第一个有内容的文本；都没有返回 null（由前端决定显示成什么） */
    private String firstText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    /**
     * 导入落库：本次文件 = 当前库存快照。
     *
     * <p>顺序很关键（用户 2026-10-01 确认的口径）：
     * <ol>
     *   <li>先把本次涉及工厂的 stock_qty 清零，**不删行** —— 物料这次没出现在导出里
     *       （用完 / 清空）时，行还在，页面上仍查得到它的物料信息；</li>
     *   <li>再按 (工厂+物料编码+存储地点) upsert 本次的行。</li>
     * </ol>
     * 所以清零必须用「按工厂批量 UPDATE」，不能用「按工厂 DELETE」—— 后者会把上一条
     * 特意保留下来的行一起删掉。
     *
     * <p>批内同键多行相加（源文件里同一物料常有多行）。这一点与领料汇总相反：
     * 那边批内重复判为错误，这里重复是正常形态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSaveOutcome saveImported(List<MaterialStock> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ImportSaveOutcome(0, 0, 0);
        }

        // 1) 批内按组合键合并（数量相加），后者不会覆盖前者
        Map<String, MaterialStock> merged = new LinkedHashMap<>();
        for (MaterialStock row : rows) {
            if (row == null || !StringUtils.hasText(row.getMaterialCode())) {
                continue;
            }
            String key = keyOf(row);
            MaterialStock exists = merged.get(key);
            if (exists == null) {
                row.setStockQty(nvl(row.getStockQty()));
                merged.put(key, row);
            } else {
                exists.setStockQty(exists.getStockQty().add(nvl(row.getStockQty())));
            }
        }
        List<MaterialStock> incoming = new ArrayList<>(merged.values());
        if (incoming.isEmpty()) {
            return new ImportSaveOutcome(0, 0, 0);
        }

        Set<String> plants = incoming.stream()
                .map(MaterialStock::getPlantCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        // 2) 查本次涉及工厂下已有的记录，用于区分「新增」与「更新」
        Map<String, Long> existingIds = new LinkedHashMap<>();
        if (!plants.isEmpty()) {
            baseMapper.selectList(new QueryWrapper<MaterialStock>()
                            .select("id", "plant_code", "material_code", "storage_location")
                            .in("plant_code", plants))
                    .forEach(s -> existingIds.put(keyOf(s), s.getId()));
        }

        // 3) 清零：只清数量，保留行（见类注释）
        if (!plants.isEmpty()) {
            baseMapper.update(null, new UpdateWrapper<MaterialStock>()
                    .set("stock_qty", BigDecimal.ZERO)
                    .in("plant_code", plants));
        }

        // 4) upsert
        LocalDateTime now = LocalDateTime.now();
        List<MaterialStock> toInsert = new ArrayList<>();
        List<MaterialStock> toUpdate = new ArrayList<>();
        for (MaterialStock row : incoming) {
            row.setLastImportTime(now);
            Long id = existingIds.get(keyOf(row));
            if (id == null) {
                toInsert.add(row);
            } else {
                row.setId(id);
                toUpdate.add(row);
            }
        }
        if (!toInsert.isEmpty()) {
            saveBatch(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            updateBatchById(toUpdate);
        }

        return new ImportSaveOutcome(toInsert.size(), toUpdate.size(), 0);
    }

    @Override
    public boolean add(MaterialStock row) {
        if (row == null) {
            return false;
        }
        row.setId(null);
        row.setLastImportTime(null);
        // 手工新增也要落在唯一键上：同键已存在时按修改处理，避免直接撞唯一索引报 500
        MaterialStock exists = findByKey(row);
        if (exists != null) {
            row.setId(exists.getId());
            return updateById(row);
        }
        return save(row);
    }

    @Override
    public boolean update(MaterialStock row) {
        return row != null && row.getId() != null && updateById(row);
    }

    @Override
    public boolean remove(Long id) {
        return id != null && removeById(id);
    }

    private MaterialStock findByKey(MaterialStock row) {
        return baseMapper.selectOne(new QueryWrapper<MaterialStock>()
                .eq("plant_code", nvlText(row.getPlantCode()))
                .eq("material_code", nvlText(row.getMaterialCode()))
                .eq("storage_location", nvlText(row.getStorageLocation()))
                .last("limit 1"));
    }

    /** 组合键：工厂 + 物料编码 + 存储地点（与表上的唯一键一致） */
    private String keyOf(MaterialStock row) {
        return nvlText(row.getPlantCode()) + '|' + nvlText(row.getMaterialCode()) + '|' + nvlText(row.getStorageLocation());
    }

    private String nvlText(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
