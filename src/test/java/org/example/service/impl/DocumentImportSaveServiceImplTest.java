package org.example.service.impl;

import org.example.dto.DocSaveOptions;
import org.example.dto.DocumentRowVO;
import org.example.dto.ImportSaveOutcome;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialPickSummary;
import org.example.entity.ProductionInbound;
import org.example.exception.BusinessException;
import org.example.exception.DocumentConflictException;
import org.example.service.MaterialPickSummaryService;
import org.example.service.ProductionInboundService;
import org.example.util.DocMaterialDateKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 落库编排（去重 + 绑图 + upsert）单测。
 *
 * <p>这是 Excel 导入与图片识别<b>共用</b>的一段逻辑，一旦出错是两条路一起错，
 * 所以把行为钉在测试里。
 *
 * <p>变更-047 起键里加了日期，另有一组 document 模式（整单差量）的用例 ——
 * 差量判定本身在 {@code DocumentDiffPlannerTest}，这里只管「取图 + 组装实体 + 调写入口」。
 */
@ExtendWith(MockitoExtension.class)
class DocumentImportSaveServiceImplTest {

    private static final LocalDate D_0920 = LocalDate.of(2026, 9, 20);
    private static final LocalDate D_0919 = LocalDate.of(2026, 9, 19);

    @Mock
    private MaterialPickSummaryService pickService;

    @Mock
    private ProductionInboundService inboundService;

    @InjectMocks
    private DocumentImportSaveServiceImpl service;

    private static MaterialPickSummary pickRow(String documentNo, String materialCode, String qty) {
        return pickRow(documentNo, materialCode, qty, D_0920);
    }

    private static MaterialPickSummary pickRow(String documentNo, String materialCode, String qty, LocalDate date) {
        MaterialPickSummary row = new MaterialPickSummary();
        row.setDocumentNo(documentNo);
        row.setMaterialCode(materialCode);
        row.setPickQty(new BigDecimal(qty));
        row.setPickDate(date);
        return row;
    }

    /** 库中行（document 模式的差量数据源） */
    private static DocumentRowVO storedRow(long id, String materialCode, String qty, LocalDate date, String fileName) {
        DocumentRowVO vo = new DocumentRowVO();
        vo.setId(id);
        vo.setSeqNo((int) id);
        vo.setMaterialCode(materialCode);
        vo.setQty(new BigDecimal(qty));
        vo.setDate(date);
        vo.setFileName(fileName);
        return vo;
    }

    private void givenSaved() {
        when(pickService.saveImported(anyList())).thenReturn(new WorkOrderImportSaveVO());
    }

    // ===================== legacy 路径（Excel / 老客户端） =====================

    @Test
    @DisplayName("★ 数量全同 -> 跳过：不写库，也【不触发图片上传】（变更-002 的关键优化）")
    void skipsIdenticalQtyAndDoesNotUploadImage() {
        when(pickService.findQtyByDocMaterialDate(any())).thenReturn(
                Map.of(DocMaterialDateKey.of("0031271", "111001786", D_0920), new BigDecimal("2380.0000")));
        givenSaved();

        List<Integer> imageAsked = new ArrayList<>();
        ImportSaveOutcome outcome = service.savePickSummaries(
                List.of(pickRow("0031271", "111001786", "2380")),
                index -> {
                    imageAsked.add(index);
                    return "img.jpg";
                });

        assertEquals(1, outcome.skipCount());
        assertEquals(0, outcome.insertCount());
        assertTrue(imageAsked.isEmpty(),
                "被跳过的行不该触发图片上传 —— 否则重复导入会把图一遍遍重传到 MinIO");
    }

    @Test
    @DisplayName("★ 同单据同物料、日期不同 -> 【不跳过】（变更-047 / D4 锚点：日期是行身份的一部分）")
    void doesNotSkipSameMaterialOnDifferentDate() {
        // 库里已有 09-19 那一行（同物料、数量也一样），本次是 09-20 的行 —— 键不同，必须落新行
        when(pickService.findQtyByDocMaterialDate(any())).thenReturn(
                Map.of(DocMaterialDateKey.of("0031271", "111001786", D_0919), new BigDecimal("2380.0000")));
        givenSaved();

        MaterialPickSummary row = pickRow("0031271", "111001786", "2380", D_0920);
        ImportSaveOutcome outcome = service.savePickSummaries(List.of(row), index -> "img.jpg");

        assertEquals(0, outcome.skipCount(),
                "键里没有日期的话这里会被误判成重复行而整行丢掉 —— 库里就少了一天的领料数据");
        assertEquals("img.jpg", row.getFileName());
    }

    @Test
    @DisplayName("数量不同 -> 不跳过：走更新，且要为它绑图片")
    void keepsRowWhenQtyDiffers() {
        when(pickService.findQtyByDocMaterialDate(any())).thenReturn(
                Map.of(DocMaterialDateKey.of("0031271", "111001786", D_0920), new BigDecimal("2380.0000")));
        givenSaved();

        MaterialPickSummary row = pickRow("0031271", "111001786", "3760");
        ImportSaveOutcome outcome = service.savePickSummaries(List.of(row), index -> "img.jpg");

        assertEquals(0, outcome.skipCount());
        assertEquals("img.jpg", row.getFileName(), "保留的行必须拿到 fileName");
    }

    @Test
    @DisplayName("库里没有该组合键 -> 新增，同样要绑图片")
    void insertsNewRow() {
        when(pickService.findQtyByDocMaterialDate(any())).thenReturn(Map.of());
        givenSaved();

        MaterialPickSummary row = pickRow("0031271", "111001786", "2380");
        ImportSaveOutcome outcome = service.savePickSummaries(List.of(row), index -> "img.jpg");

        assertEquals(0, outcome.skipCount());
        assertEquals("img.jpg", row.getFileName());
    }

    @Test
    @DisplayName("★ 图片提供者拿到的是【原始下标】：前面的行被跳过后，下标不偏移")
    void imageProviderGetsOriginalIndex() {
        // 第 0 行会被跳过（数量全同），第 1 行保留 —— 提供者应收到 1，而不是 0
        when(pickService.findQtyByDocMaterialDate(any())).thenReturn(
                Map.of(DocMaterialDateKey.of("0031271", "111001786", D_0920), new BigDecimal("2380.0000")));
        givenSaved();

        List<Integer> asked = new ArrayList<>();
        service.savePickSummaries(
                List.of(pickRow("0031271", "111001786", "2380"),
                        pickRow("0031271", "111001787", "100")),
                index -> {
                    asked.add(index);
                    return "img-" + index + ".jpg";
                });

        assertEquals(List.of(1), asked,
                "下标若按过滤后的位置算，图片会串到别的行上（Excel 路径的 documentIds 按下标对齐）");
    }

    @Test
    @DisplayName("空列表直接返回零，不查库不写库")
    void emptyRowsShortCircuit() {
        ImportSaveOutcome outcome = service.savePickSummaries(List.of(), index -> "img.jpg");

        assertEquals(0, outcome.insertCount());
        assertEquals(0, outcome.updateCount());
        assertEquals(0, outcome.skipCount());
    }

    // ===================== document 模式（载入编辑） =====================

    @Test
    @DisplayName("document：全同 -> 全跳过，一张图都不传（无写入就不该有上传）")
    void documentModeDoesNotUploadImageWhenNothingToWrite() {
        when(pickService.listByDocument("0031271")).thenReturn(
                List.of(storedRow(1, "111001786", "2380.0000", D_0920, "old.jpg")));

        MaterialPickSummary row = pickRow("0031271", "111001786", "2380");
        row.setId(1L);
        row.setSeqNo(1);

        List<Integer> asked = new ArrayList<>();
        ImportSaveOutcome outcome = service.saveDocumentPick("0031271", List.of(row),
                new DocSaveOptions(1, false), index -> {
                    asked.add(index);
                    return "new.jpg";
                });

        assertEquals(1, outcome.skipCount());
        assertEquals(0, outcome.deleteCount());
        assertTrue(asked.isEmpty(), "全程无写入时不该向上传器要图");
        verify(pickService).applyDocumentChanges(List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("document：改数量 -> 更新，且【不覆写】库里已有的 file_name（已传图不重传）")
    void documentModeKeepsExistingFileName() {
        when(pickService.listByDocument("0031271")).thenReturn(
                List.of(storedRow(1, "111001786", "2380.0000", D_0920, "old.jpg")));

        MaterialPickSummary row = pickRow("0031271", "111001786", "3760");
        row.setId(1L);

        ImportSaveOutcome outcome = service.saveDocumentPick("0031271", List.of(row),
                new DocSaveOptions(1, false), index -> {
                    throw new AssertionError("库里已有图，不该再要新图");
                });

        assertEquals(1, outcome.updateCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaterialPickSummary>> updated = ArgumentCaptor.forClass(List.class);
        verify(pickService).applyDocumentChanges(anyList(), updated.capture(), anyCollection());
        assertEquals("old.jpg", updated.getValue().get(0).getFileName(),
                "已有 file_name 必须原样拷回 —— 覆写会让列表页的缩略图换成另一张单的图");
    }

    @Test
    @DisplayName("document：新增行 -> 传一次图，全单据共用（含 seqNo 按行序重排）")
    void documentModeUploadsImageOnceForInserts() {
        when(pickService.listByDocument("0031271")).thenReturn(List.of());

        MaterialPickSummary r1 = pickRow("0031271", "111001786", "2380");
        MaterialPickSummary r2 = pickRow("0031271", "111001787", "100");
        r2.setSeqNo(9);   // 客户端发来的序号带噪声，执行侧按行序重排

        List<Integer> asked = new ArrayList<>();
        ImportSaveOutcome outcome = service.saveDocumentPick("0031271", List.of(r1, r2),
                new DocSaveOptions(null, false), index -> {
                    asked.add(index);
                    return "new.jpg";
                });

        assertEquals(2, outcome.insertCount());
        assertEquals(List.of(0), asked, "一张图就是一张单据：整单至多要一次图，用行 0 的位次");
        assertEquals("new.jpg", r1.getFileName());
        assertEquals("new.jpg", r2.getFileName());
        assertEquals(2, r2.getSeqNo(), "seqNo 以载荷行序为唯一口径");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaterialPickSummary>> inserted = ArgumentCaptor.forClass(List.class);
        verify(pickService).applyDocumentChanges(inserted.capture(), anyList(), anyCollection());
        assertEquals(2, inserted.getValue().size());
        assertNull(inserted.getValue().get(1).getId(), "新增行必须清掉可能误带的 id");
    }

    @Test
    @DisplayName("document：删行 -> idsToDelete 传给写入口，deleteCount 与之一致")
    void documentModePassesDeletesToWriter() {
        when(pickService.listByDocument("0031271")).thenReturn(List.of(
                storedRow(1, "111001786", "2380.0000", D_0920, "old.jpg"),
                storedRow(2, "111001787", "100.0000", D_0920, "old.jpg")));

        // 只保留第 1 行、且数量未变 -> 第 2 行要被删掉，整单无写入（跳过即可）
        MaterialPickSummary row = pickRow("0031271", "111001786", "2380");
        row.setId(1L);

        ImportSaveOutcome outcome = service.saveDocumentPick("0031271", List.of(row),
                new DocSaveOptions(2, false), index -> "new.jpg");

        assertEquals(1, outcome.deleteCount());
        assertEquals(1, outcome.skipCount());

        ArgumentCaptor<Collection<Long>> deleted = ArgumentCaptor.forClass(Collection.class);
        verify(pickService).applyDocumentChanges(anyList(), anyList(), deleted.capture());
        assertEquals(List.of(2L), new ArrayList<>(deleted.getValue()));
    }

    @Test
    @DisplayName("document：带 id 的行若不在库里 -> 冲突异常（前端会据此提示重新载入）")
    void documentModeThrowsOnMissingId() {
        when(pickService.listByDocument("0031271")).thenReturn(
                List.of(storedRow(1, "111001786", "2380.0000", D_0920, "old.jpg")));

        MaterialPickSummary row = pickRow("0031271", "111001786", "2380");
        row.setId(99L);

        DocumentConflictException e = assertThrows(DocumentConflictException.class,
                () -> service.saveDocumentPick("0031271", List.of(row),
                        new DocSaveOptions(1, false), index -> "new.jpg"));
        assertTrue(e.getMessage().contains("第1行已不存在"), e.getMessage());
    }

    @Test
    @DisplayName("document：载荷为空且库里非空、未带 allowEmpty -> 整单删除被拒（三重闸门的后端一环）")
    void documentModeRequiresAllowEmptyForWholeDelete() {
        when(pickService.listByDocument("0031271")).thenReturn(
                List.of(storedRow(1, "111001786", "2380.0000", D_0920, "old.jpg")));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.saveDocumentPick("0031271", List.of(),
                        new DocSaveOptions(1, false), index -> "new.jpg"));
        assertTrue(e.getMessage().contains("整单删除需要显式确认"), e.getMessage());

        // 显式确认后才放行，并真的删掉
        ImportSaveOutcome outcome = service.saveDocumentPick("0031271", List.of(),
                new DocSaveOptions(1, true), index -> "new.jpg");
        assertEquals(1, outcome.deleteCount());
    }

    @Test
    @DisplayName("document：严格模式行数不符 -> 冲突异常（其他人刚改过这张单）")
    void documentModeThrowsOnRowCountMismatch() {
        when(pickService.listByDocument("0031271")).thenReturn(
                List.of(storedRow(1, "111001786", "2380.0000", D_0920, "old.jpg")));

        MaterialPickSummary row = pickRow("0031271", "111001786", "2380");
        row.setId(1L);

        DocumentConflictException e = assertThrows(DocumentConflictException.class,
                () -> service.saveDocumentPick("0031271", List.of(row),
                        new DocSaveOptions(3, false), index -> "new.jpg"));
        assertTrue(e.getMessage().contains("载入时 3 行，当前 1 行"), e.getMessage());
    }

    @Test
    @DisplayName("document 入库侧同款：口子同样走通（两个单据类型不许一套生效一套不生效）")
    void documentModeWorksForInboundToo() {
        when(inboundService.listByDocument("0031271")).thenReturn(List.of());

        ProductionInbound row = new ProductionInbound();
        row.setDocumentNo("0031271");
        row.setMaterialCode("111001786");
        row.setInboundQty(new BigDecimal("2380"));
        row.setInboundDate(D_0920);

        ImportSaveOutcome outcome = service.saveDocumentInbound("0031271", List.of(row),
                new DocSaveOptions(null, false), index -> "new.jpg");

        assertEquals(1, outcome.insertCount());
        assertEquals("new.jpg", row.getFileName());
        verify(inboundService).applyDocumentChanges(anyList(), anyList(), eq(List.of()));
    }
}
