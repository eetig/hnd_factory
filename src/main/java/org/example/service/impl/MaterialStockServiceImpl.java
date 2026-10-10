package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.ImportSaveOutcome;
import org.example.dto.MaterialStockQueryDTO;
import org.example.dto.MaterialStockVO;
import org.example.entity.MaterialStock;
import org.example.mapper.MaterialStockMapper;
import org.example.service.MaterialStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MaterialStockServiceImpl extends ServiceImpl<MaterialStockMapper, MaterialStock>
        implements MaterialStockService {

    @Override
    public IPage<MaterialStockVO> listPage(MaterialStockQueryDTO query) {
        int pageNum = query.resolvedPageNum();
        int pageSize = query.resolvedPageSize();
        boolean onlyInStock = Boolean.TRUE.equals(query.getOnlyInStock());
        // 空串与纯空白都当「不限」—— 与改造前前端 applyStockFilter 里
        // `keyword.trim()` 之后判空同口径
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;

        long total = baseMapper.countStock(keyword, onlyInStock);
        // total 为 0 时不查明细：翻到越界页码（比如删了最后一页的最后一条）也走这里，
        // 省一次必然为空的查询
        List<MaterialStockVO> rows = total == 0
                ? List.of()
                : baseMapper.selectStockPage(keyword, onlyInStock, (long) (pageNum - 1) * pageSize, pageSize);

        Page<MaterialStockVO> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(rows);
        return page;
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
