package org.example.service.impl;

import org.example.dto.ImportSaveOutcome;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialPickSummary;
import org.example.entity.ProductionInbound;
import org.example.service.DocumentImportSaveService;
import org.example.service.MaterialPickSummaryService;
import org.example.service.ProductionInboundService;
import org.example.util.DocMaterialKey;
import org.example.util.ImportDedupeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 单据落库实现。
 *
 * <p>领料汇总与生产入库单的落库编排（查库数量 → 跳过重复 → 绑图片 → upsert）逐字相同，
 * 差异只有「字段名、数量列、保存入口」三处，因此收敛到 {@link RowOps}，
 * 编排本身只写一份 —— 这也是本服务存在的意义。
 */
@Service
public class DocumentImportSaveServiceImpl implements DocumentImportSaveService {

    private static final Logger log = LoggerFactory.getLogger(DocumentImportSaveServiceImpl.class);

    @Autowired
    private MaterialPickSummaryService materialPickSummaryService;

    @Autowired
    private ProductionInboundService productionInboundService;

    @Override
    public ImportSaveOutcome savePickSummaries(List<MaterialPickSummary> rows, RowImageProvider images) {
        return saveRows(rows, images, new RowOps<>() {
            @Override
            public String documentNo(MaterialPickSummary row) {
                return row.getDocumentNo();
            }

            @Override
            public String materialCode(MaterialPickSummary row) {
                return row.getMaterialCode();
            }

            @Override
            public BigDecimal qty(MaterialPickSummary row) {
                return row.getPickQty();
            }

            @Override
            public void fileName(MaterialPickSummary row, String fileName) {
                row.setFileName(fileName);
            }

            @Override
            public Map<String, BigDecimal> findDbQty(Collection<String> documentNos) {
                return materialPickSummaryService.findQtyByDocAndMaterial(documentNos);
            }

            @Override
            public WorkOrderImportSaveVO save(List<MaterialPickSummary> kept) {
                return materialPickSummaryService.saveImported(kept);
            }
        }, "领料汇总");
    }

    @Override
    public ImportSaveOutcome saveInbounds(List<ProductionInbound> rows, RowImageProvider images) {
        return saveRows(rows, images, new RowOps<>() {
            @Override
            public String documentNo(ProductionInbound row) {
                return row.getDocumentNo();
            }

            @Override
            public String materialCode(ProductionInbound row) {
                return row.getMaterialCode();
            }

            @Override
            public BigDecimal qty(ProductionInbound row) {
                return row.getInboundQty();
            }

            @Override
            public void fileName(ProductionInbound row, String fileName) {
                row.setFileName(fileName);
            }

            @Override
            public Map<String, BigDecimal> findDbQty(Collection<String> documentNos) {
                return productionInboundService.findQtyByDocAndMaterial(documentNos);
            }

            @Override
            public WorkOrderImportSaveVO save(List<ProductionInbound> kept) {
                return productionInboundService.saveImported(kept);
            }
        }, "生产入库单");
    }

    /**
     * 落库编排（两种单据共用）。
     *
     * <p>「跳过」的判定是「单据号 + 物料编码 + 数量」三者全同 —— 数量不同则走更新。
     * 判定用 {@link ImportDedupeUtil#isSameQty}，它处理了 scale 与列精度的坑，不要另写比较。
     */
    private <T> ImportSaveOutcome saveRows(List<T> rows, RowImageProvider images, RowOps<T> ops, String label) {
        if (rows == null || rows.isEmpty()) {
            return new ImportSaveOutcome(0, 0, 0);
        }

        Collection<String> documentNos = rows.stream()
                .map(ops::documentNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, BigDecimal> dbQty = ops.findDbQty(documentNos);

        List<T> kept = new ArrayList<>();
        int skipCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            T row = rows.get(i);
            String key = DocMaterialKey.of(ops.documentNo(row), ops.materialCode(row));
            if (dbQty.containsKey(key) && ImportDedupeUtil.isSameQty(dbQty.get(key), ops.qty(row))) {
                skipCount++;
                continue;
            }
            // 图片只对「确定要写入的行」取：被跳过的行不触发上传（变更-002 的优化，别提前物化）
            ops.fileName(row, images.fileNameFor(i));
            kept.add(row);
        }

        WorkOrderImportSaveVO saved = ops.save(kept);
        log.info("{}落库完成, 保留={}, 跳过={}, 新增={}, 更新={}",
                label, kept.size(), skipCount, saved.getInsertCount(), saved.getUpdateCount());
        return new ImportSaveOutcome(saved.getInsertCount(), saved.getUpdateCount(), skipCount);
    }

    /** 两种单据的差异收敛点 —— 编排只认这几个动作，不认具体实体 */
    private interface RowOps<T> {

        String documentNo(T row);

        String materialCode(T row);

        BigDecimal qty(T row);

        void fileName(T row, String fileName);

        Map<String, BigDecimal> findDbQty(Collection<String> documentNos);

        WorkOrderImportSaveVO save(List<T> rows);
    }
}
