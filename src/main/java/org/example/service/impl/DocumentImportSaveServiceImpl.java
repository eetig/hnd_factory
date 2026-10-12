package org.example.service.impl;

import org.example.dto.DocSaveOptions;
import org.example.dto.DocumentRowVO;
import org.example.dto.ImportSaveOutcome;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialPickSummary;
import org.example.entity.ProductionInbound;
import org.example.service.DocumentImportSaveService;
import org.example.service.MaterialPickSummaryService;
import org.example.service.ProductionInboundService;
import org.example.util.DocMaterialDateKey;
import org.example.util.DocumentDiffPlanner;
import org.example.util.ImportDedupeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 单据落库实现。
 *
 * <p>这里有<b>两套</b>编排，共用同一批 {@code ImportUtil} 与图片提供者：
 * <ul>
 *   <li>{@link #saveRows}（legacy）：逐行 upsert，跳过「单据号+物料编码+日期+数量」全同的行。
 *       老客户端（不带 {@code mode}）与 Excel 导入走这条，变更-047 前后行为逐字不变；
 *   <li>{@link #saveDocRows}（document 模式，变更-047）：整单差量 —— 载入编辑后
 *       改的更新、删的删除、新的插入。判定全部委托给纯函数
 *       {@link DocumentDiffPlanner}，本类只负责「取图 → 组装实体 → 按序写库」。
 * </ul>
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
            public LocalDate date(MaterialPickSummary row) {
                return row.getPickDate();
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
                return materialPickSummaryService.findQtyByDocMaterialDate(documentNos);
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
            public LocalDate date(ProductionInbound row) {
                return row.getInboundDate();
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
                return productionInboundService.findQtyByDocMaterialDate(documentNos);
            }

            @Override
            public WorkOrderImportSaveVO save(List<ProductionInbound> kept) {
                return productionInboundService.saveImported(kept);
            }
        }, "生产入库单");
    }

    /**
     * legacy 落库编排（两种单据共用）。
     *
     * <p>「跳过」的判定是「单据号 + 物料编码 + 日期 + 数量」全同 —— 数量不同则走更新。
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
            String key = DocMaterialDateKey.of(ops.documentNo(row), ops.materialCode(row), ops.date(row));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSaveOutcome saveDocumentPick(String documentNo, List<MaterialPickSummary> rows,
                                              DocSaveOptions options, RowImageProvider images) {
        return saveDocRows(documentNo, rows, options, images, new DocRowOps<>() {
            @Override
            public void setFileName(MaterialPickSummary row, String fileName) {
                row.setFileName(fileName);
            }

            @Override
            public void setId(MaterialPickSummary row, Long id) {
                row.setId(id);
            }

            @Override
            public void setSeqNo(MaterialPickSummary row, int seqNo) {
                row.setSeqNo(seqNo);
            }

            @Override
            public DocumentDiffPlanner.PayloadRow toPayloadRow(MaterialPickSummary row) {
                return new DocumentDiffPlanner.PayloadRow(row.getId(), row.getMaterialName(),
                        row.getMaterialCode(), row.getPickQty(), row.getUnit(), row.getPickDate());
            }

            @Override
            public List<DocumentRowVO> stored(String documentNo) {
                return materialPickSummaryService.listByDocument(documentNo);
            }

            @Override
            public void applyChanges(List<MaterialPickSummary> toInsert,
                                     List<MaterialPickSummary> toUpdate,
                                     Collection<Long> idsToDelete) {
                materialPickSummaryService.applyDocumentChanges(toInsert, toUpdate, idsToDelete);
            }
        }, "领料汇总");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSaveOutcome saveDocumentInbound(String documentNo, List<ProductionInbound> rows,
                                                 DocSaveOptions options, RowImageProvider images) {
        return saveDocRows(documentNo, rows, options, images, new DocRowOps<>() {
            @Override
            public void setFileName(ProductionInbound row, String fileName) {
                row.setFileName(fileName);
            }

            @Override
            public void setId(ProductionInbound row, Long id) {
                row.setId(id);
            }

            @Override
            public void setSeqNo(ProductionInbound row, int seqNo) {
                row.setSeqNo(seqNo);
            }

            @Override
            public DocumentDiffPlanner.PayloadRow toPayloadRow(ProductionInbound row) {
                return new DocumentDiffPlanner.PayloadRow(row.getId(), row.getMaterialName(),
                        row.getMaterialCode(), row.getInboundQty(), row.getUnit(), row.getInboundDate());
            }

            @Override
            public List<DocumentRowVO> stored(String documentNo) {
                return productionInboundService.listByDocument(documentNo);
            }

            @Override
            public void applyChanges(List<ProductionInbound> toInsert,
                                     List<ProductionInbound> toUpdate,
                                     Collection<Long> idsToDelete) {
                productionInboundService.applyDocumentChanges(toInsert, toUpdate, idsToDelete);
            }
        }, "生产入库单");
    }

    /**
     * document 模式编排（两种单据共用）—— 读库、判定、取图、写库必须在<b>同一个事务</b>里。
     *
     * <p>⚠️ 事务注解挂在上面的公开方法上（不用挂这里）：本方法是同类内部调用，
     * 走不到 Spring 代理，注解挂在这会静默失效 —— 读与写落进两个快照，
     * 「行数对不对」的并发校验就白做了。
     *
     * <p>图片<b>惰性取</b>：只有「确有新增行」或「更新行在库里原本没有图」时才向提供者要一次。
     * 全程无写入（全跳过）的保存一张图都不会上传；已有 {@code file_name} 的行原样保留，绝不覆写。
     */
    private <T> ImportSaveOutcome saveDocRows(String documentNo, List<T> rows, DocSaveOptions options,
                                              RowImageProvider images, DocRowOps<T> ops, String label) {
        List<T> payload = rows == null ? List.of() : rows;

        List<DocumentRowVO> stored = ops.stored(documentNo);
        Map<Long, DocumentRowVO> storedById = new HashMap<>();
        for (DocumentRowVO s : stored) {
            if (s.getId() != null) {
                storedById.put(s.getId(), s);
            }
        }

        List<DocumentDiffPlanner.PayloadRow> payloadRows = payload.stream()
                .map(ops::toPayloadRow).collect(Collectors.toList());
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(documentNo, stored, payloadRows, options);

        boolean needImage = !plan.insertIndexes().isEmpty();
        if (!needImage) {
            for (DocumentDiffPlanner.UpdateDecision u : plan.updates()) {
                DocumentRowVO s = storedById.get(u.dbId());
                if (s != null && !StringUtils.hasText(s.getFileName())) {
                    needImage = true;
                    break;
                }
            }
        }
        // 一张图就是一张单据：整单共用同一个 fileName，提供者至多被调一次
        String docFileName = needImage ? images.fileNameFor(0) : null;

        List<T> toInsert = new ArrayList<>();
        for (int idx : plan.insertIndexes()) {
            T row = payload.get(idx);
            ops.setId(row, null);          // 载荷可能带着别处的 id，新增前必须清掉
            ops.setSeqNo(row, idx + 1);    // seqNo = 行序（载荷顺序是唯一口径，见 DocumentDiffPlanner）
            ops.setFileName(row, docFileName);
            toInsert.add(row);
        }

        List<T> toUpdate = new ArrayList<>();
        for (DocumentDiffPlanner.UpdateDecision u : plan.updates()) {
            T row = payload.get(u.payloadIndex());
            DocumentRowVO s = storedById.get(u.dbId());
            ops.setId(row, u.dbId());      // 无 id 命中三键的行在这里拿回行身份
            ops.setSeqNo(row, u.payloadIndex() + 1);
            // 已有图不覆写：file_name 非空就显式拷回原值。显式赋值而不是「留空靠更新策略跳过」，
            // 是为了不依赖 MyBatis-Plus 的字段更新策略（哪天全局配成 IGNORED 就会误清）
            String kept = s != null && StringUtils.hasText(s.getFileName()) ? s.getFileName() : docFileName;
            ops.setFileName(row, kept);
            toUpdate.add(row);
        }

        ops.applyChanges(toInsert, toUpdate, plan.deleteIds());
        log.info("{}「载入编辑」落库完成, 单据号={}, 新增={}, 更新={}, 跳过={}, 删除={}, 严格模式={}",
                label, documentNo, toInsert.size(), toUpdate.size(), plan.skipCount(),
                plan.deleteIds().size(), options.strict());
        return new ImportSaveOutcome(toInsert.size(), toUpdate.size(), plan.skipCount(),
                plan.deleteIds().size());
    }

    /** legacy 编排的差异收敛点 —— 只认这几个动作，不认具体实体 */
    private interface RowOps<T> {

        String documentNo(T row);

        String materialCode(T row);

        LocalDate date(T row);

        BigDecimal qty(T row);

        void fileName(T row, String fileName);

        Map<String, BigDecimal> findDbQty(Collection<String> documentNos);

        WorkOrderImportSaveVO save(List<T> rows);
    }

    /** document 编排的差异收敛点：比 {@link RowOps} 多「行身份（id/seqNo）」与「按单查行」 */
    private interface DocRowOps<T> {

        void setFileName(T row, String fileName);

        void setId(T row, Long id);

        void setSeqNo(T row, int seqNo);

        DocumentDiffPlanner.PayloadRow toPayloadRow(T row);

        List<DocumentRowVO> stored(String documentNo);

        void applyChanges(List<T> toInsert, List<T> toUpdate, Collection<Long> idsToDelete);
    }
}
