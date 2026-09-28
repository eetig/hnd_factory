package org.example.service.impl;

import org.example.dto.ImportSaveOutcome;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialPickSummary;
import org.example.service.MaterialPickSummaryService;
import org.example.service.ProductionInboundService;
import org.example.util.DocMaterialKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 落库编排（去重 + 绑图 + upsert）单测。
 *
 * <p>这是 Excel 导入与图片识别<b>共用</b>的一段逻辑，一旦出错是两条路一起错，
 * 所以把行为钉在测试里。
 */
@ExtendWith(MockitoExtension.class)
class DocumentImportSaveServiceImplTest {

    @Mock
    private MaterialPickSummaryService pickService;

    @Mock
    private ProductionInboundService inboundService;

    @InjectMocks
    private DocumentImportSaveServiceImpl service;

    private static MaterialPickSummary pickRow(String documentNo, String materialCode, String qty) {
        MaterialPickSummary row = new MaterialPickSummary();
        row.setDocumentNo(documentNo);
        row.setMaterialCode(materialCode);
        row.setPickQty(new BigDecimal(qty));
        return row;
    }

    private void givenSaved() {
        when(pickService.saveImported(anyList())).thenReturn(new WorkOrderImportSaveVO());
    }

    @Test
    @DisplayName("★ 数量全同 -> 跳过：不写库，也【不触发图片上传】（变更-002 的关键优化）")
    void skipsIdenticalQtyAndDoesNotUploadImage() {
        when(pickService.findQtyByDocAndMaterial(any())).thenReturn(
                Map.of(DocMaterialKey.of("0031271", "111001786"), new BigDecimal("2380.0000")));
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
    @DisplayName("数量不同 -> 不跳过：走更新，且要为它绑图片")
    void keepsRowWhenQtyDiffers() {
        when(pickService.findQtyByDocAndMaterial(any())).thenReturn(
                Map.of(DocMaterialKey.of("0031271", "111001786"), new BigDecimal("2380.0000")));
        givenSaved();

        MaterialPickSummary row = pickRow("0031271", "111001786", "3760");
        ImportSaveOutcome outcome = service.savePickSummaries(List.of(row), index -> "img.jpg");

        assertEquals(0, outcome.skipCount());
        assertEquals("img.jpg", row.getFileName(), "保留的行必须拿到 fileName");
    }

    @Test
    @DisplayName("库里没有该组合键 -> 新增，同样要绑图片")
    void insertsNewRow() {
        when(pickService.findQtyByDocAndMaterial(any())).thenReturn(Map.of());
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
        when(pickService.findQtyByDocAndMaterial(any())).thenReturn(
                Map.of(DocMaterialKey.of("0031271", "111001786"), new BigDecimal("2380.0000")));
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
}
