package org.example.util;

import org.example.dto.DocSaveOptions;
import org.example.dto.DocumentRowVO;
import org.example.exception.BusinessException;
import org.example.exception.DocumentConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「载入编辑」的整单差量判定（变更-047）。
 *
 * <p>全部落库判定都在这一个纯函数里（谁插、谁更、谁删、跳几行），
 * 所以这里也是全部边界用例的所在地 —— 它算错一次，用户看到的就是「保存完了库不对」。
 */
class DocumentDiffPlannerTest {

    private static final String DOC = "0031271";
    private static final LocalDate D20 = LocalDate.of(2026, 9, 20);
    private static final LocalDate D19 = LocalDate.of(2026, 9, 19);

    private static DocumentRowVO stored(long id, String code, String qty, LocalDate date) {
        return stored(id, 1, code, qty, date);
    }

    private static DocumentRowVO stored(long id, int seqNo, String code, String qty, LocalDate date) {
        DocumentRowVO vo = new DocumentRowVO();
        vo.setId(id);
        vo.setSeqNo(seqNo);
        vo.setMaterialCode(code);
        vo.setQty(new BigDecimal(qty));
        vo.setDate(date);
        return vo;
    }

    private static DocumentDiffPlanner.PayloadRow payload(Long id, String code, String qty, LocalDate date) {
        return new DocumentDiffPlanner.PayloadRow(id, null, code, new BigDecimal(qty), null, date);
    }

    /** 严格模式（保存已存在单据）：expectedRowCount = 载入时行数 */
    private static DocSaveOptions strict(int expected) {
        return new DocSaveOptions(expected, false);
    }

    /** append 模式（新单据 / 不带 expectedRowCount）：只增改跳，永不删 */
    private static DocSaveOptions append() {
        return new DocSaveOptions(null, false);
    }

    @Test
    @DisplayName("库空 + 载荷新行 -> 全部新增，无删除")
    void insertsWhenNothingStored() {
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC, List.of(),
                List.of(payload(null, "111001785", "6000", D20),
                        payload(null, "111001786", "2380", D20)),
                append());

        assertEquals(List.of(0, 1), plan.insertIndexes());
        assertTrue(plan.updates().isEmpty());
        assertEquals(0, plan.skipCount());
        assertTrue(plan.deleteIds().isEmpty());
    }

    @Test
    @DisplayName("★ 同物料不同日期 -> 各自一行（D4 锚点，键里没日期这条就会错）")
    void sameMaterialDifferentDateIsNotTheSameRow() {
        // 库里只有 09-19 那一行；载荷是 09-20 的行（物料、数量都一样）
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC,
                List.of(stored(1, "111001785", "6000.0", D19)),
                List.of(payload(null, "111001785", "6000", D20)),
                strict(1));

        assertEquals(List.of(0), plan.insertIndexes(), "日期不同 -> 新行，不是「改已有行」");
        assertTrue(plan.deleteIds().contains(1L), "旧的那一行没被本次载荷保留 -> 严格模式下删除");
    }

    @Test
    @DisplayName("带 id 且字段全同 -> 跳过（不写库）")
    void skipsUnchangedRowWithId() {
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC,
                List.of(stored(1, "111001785", "6000.0", D20)),
                List.of(payload(1L, "111001785", "6000", D20)),
                strict(1));

        assertEquals(1, plan.skipCount());
        assertTrue(plan.insertIndexes().isEmpty());
        assertTrue(plan.updates().isEmpty());
        assertTrue(plan.deleteIds().isEmpty());
    }

    @Test
    @DisplayName("带 id 数量变 -> 按该 id 更新（行身份保住）")
    void updatesRowById() {
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC,
                List.of(stored(7, "111001785", "6000.0", D20)),
                List.of(payload(7L, "111001785", "9000", D20)),
                strict(1));

        assertEquals(1, plan.updates().size());
        assertEquals(7L, plan.updates().get(0).dbId());
        assertEquals(0, plan.updates().get(0).payloadIndex());
        assertEquals(0, plan.deleteIds().size());
    }

    @Test
    @DisplayName("★ 无 id 但三键命中 -> 算「同一行」（重发幂等，不会当成新行插）")
    void matchesByIdentityKeyWhenNoId() {
        // 重发场景：客户端没带 id，但 (单据号+物料编码+日期) 与库中一致 -> 仍是那一行
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC,
                List.of(stored(3, "111001785", "6000.0", D20)),
                List.of(payload(null, "111001785", "6000", D20)),
                strict(1));

        assertEquals(1, plan.skipCount(), "三键全同 -> 跳过");
        assertTrue(plan.deleteIds().isEmpty(), "命中的行被保留 -> 不该被删");
    }

    @Test
    @DisplayName("行序变了 -> 记成更新（seqNo 以载荷行序为口径重排）")
    void reorderCountsAsUpdate() {
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC,
                List.of(stored(1, 1, "111001785", "6000.0", D20),
                        stored(2, 2, "111001786", "2380.0", D20)),
                List.of(payload(2L, "111001786", "2380", D20),
                        payload(1L, "111001785", "6000", D20)),
                strict(2));

        assertEquals(2, plan.updates().size(), "两行行序都变了 -> 两行都要更新 seqNo");
        assertEquals(2L, plan.updates().get(0).dbId());
        assertEquals(0, plan.updates().get(0).payloadIndex());
        assertEquals(1L, plan.updates().get(1).dbId());
        assertEquals(1, plan.updates().get(1).payloadIndex());
    }

    @Test
    @DisplayName("严格模式：库中行数与会话载入时不一致 -> 冲突（409 文案带两个数）")
    void strictModeConflictsOnRowCountMismatch() {
        DocumentConflictException e = assertThrows(DocumentConflictException.class,
                () -> DocumentDiffPlanner.plan(DOC,
                        List.of(stored(1, "111001785", "6000.0", D20)),
                        List.of(payload(1L, "111001785", "6000", D20)),
                        strict(3)));

        assertTrue(e.getMessage().contains("载入时 3 行，当前 1 行"), e.getMessage());
    }

    @Test
    @DisplayName("严格模式：载荷里没有的库中行 -> 删除（这就是「删行」唯一的出口）")
    void strictModeDeletesUnkeptRows() {
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC,
                List.of(stored(1, "111001785", "6000.0", D20),
                        stored(2, "111001786", "2380.0", D20)),
                List.of(payload(1L, "111001785", "6000", D20)),
                strict(2));

        assertEquals(List.of(2L), plan.deleteIds());
    }

    @Test
    @DisplayName("带 id 的行在库里不存在 -> 冲突（别人已经把它删了）")
    void conflictsWhenIdMissing() {
        DocumentConflictException e = assertThrows(DocumentConflictException.class,
                () -> DocumentDiffPlanner.plan(DOC, List.of(),
                        List.of(payload(99L, "111001785", "6000", D20)),
                        strict(0)));

        assertTrue(e.getMessage().startsWith("第1行已不存在"), e.getMessage());
    }

    @Test
    @DisplayName("append 模式：只增改跳，一个都不删")
    void appendNeverDeletes() {
        // 库里两行、行序与载荷一致，字段也全同 —— append 模式下既不删也不改，全跳过
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC,
                List.of(stored(1, 1, "111001785", "6000.0", D20),
                        stored(2, 2, "111001786", "2380.0", D20)),
                List.of(payload(1L, "111001785", "6000", D20),
                        payload(2L, "111001786", "2380", D20)),
                append());

        assertTrue(plan.deleteIds().isEmpty());
        assertEquals(2, plan.skipCount());
    }

    @Test
    @DisplayName("append 模式：库中多出载荷覆盖不到的行 -> 冲突（提示重新载入）")
    void appendConflictsOnExtraStoredRows() {
        DocumentConflictException e = assertThrows(DocumentConflictException.class,
                () -> DocumentDiffPlanner.plan(DOC,
                        List.of(stored(1, "111001785", "6000.0", D20),
                                stored(2, "111001786", "2380.0", D20)),
                        List.of(payload(1L, "111001785", "6000", D20)),
                        append()));

        assertTrue(e.getMessage().contains("多出 1 行"), e.getMessage());
    }

    @Test
    @DisplayName("空载荷 + 空库 -> 无事发生（新单据被删光行，不当事）")
    void emptyPayloadOnEmptyDocumentIsNoop() {
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC, List.of(), List.of(), strict(0));

        assertTrue(plan.insertIndexes().isEmpty());
        assertTrue(plan.updates().isEmpty());
        assertTrue(plan.deleteIds().isEmpty());
    }

    @Test
    @DisplayName("★ 空载荷 + 库非空：没带 allowEmpty -> 拒绝；带了 -> 整单删除")
    void emptyPayloadNeedsExplicitAllowance() {
        List<DocumentRowVO> stored = List.of(stored(1, "111001785", "6000.0", D20));

        BusinessException e = assertThrows(BusinessException.class,
                () -> DocumentDiffPlanner.plan(DOC, stored, List.of(), strict(1)));
        assertTrue(e.getMessage().contains("整单删除需要显式确认"), e.getMessage());

        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC, stored, List.of(),
                new DocSaveOptions(1, true));
        assertEquals(List.of(1L), plan.deleteIds());
    }

    @Test
    @DisplayName("同三键的脏数据（日期为 NULL 的历史行）-> 取 id 最小者，判定不随查询顺序漂移")
    void duplicateIdentityKeyPicksSmallestId() {
        DocumentDiffPlanner.Plan plan = DocumentDiffPlanner.plan(DOC,
                List.of(stored(9, 1, "111001785", "6000.0", null),
                        stored(4, 1, "111001785", "6000.0", null)),
                List.of(payload(null, "111001785", "6000", null)),
                strict(2));

        assertEquals(1, plan.skipCount());
        assertEquals(List.of(9L), plan.deleteIds(), "命中的是 id 最小的那条，另一条按未保留删除");
    }
}
