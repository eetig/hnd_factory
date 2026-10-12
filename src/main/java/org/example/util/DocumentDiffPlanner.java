package org.example.util;

import org.example.dto.DocSaveOptions;
import org.example.dto.DocumentRowVO;
import org.example.exception.BusinessException;
import org.example.exception.DocumentConflictException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * document 模式的行级差量规划（变更-047）。
 *
 * <p><b>纯函数</b>：输入「库中行 + 本次载荷 + 开关」，输出「谁要插、谁要更、谁要删、跳几行」，
 * 不碰数据库也不碰实体 —— 全部落库判定集中在这里，是单测主战场。
 * 实际写入（先删后插、按 id 更新）由 {@code DocumentImportSaveServiceImpl#saveDocRows} 执行。
 *
 * <p>判定口径（对应使用方 2026-10-12 确认的 D1–D5）：
 * <ul>
 *   <li>载荷行带 {@code id}：必须是库中真实存在的行（否则 409 冲突）；
 *       字段全同 → 跳过，有差异 → 按该 id 更新；
 *   <li>载荷行不带 {@code id}：按 (单据号+物料编码+日期) 命中库中行即视为同一行
 *       （<b>重发幂等</b>，保住库中 id，不会把已存在的行当新行插）；未命中 → 新增；
 *   <li><b>严格模式</b>（{@code expectedRowCount} 非空）：库中行数与载入时不一致 → 409；
 *       库中未被本次载荷保留的行 → 删除；
 *   <li><b>append 模式</b>（不带 expectedRowCount）：只增 / 改 / 跳，<b>永不删除</b>；
 *       库中多出载荷覆盖不到的行（他人刚插入的）→ 409 提示重新载入；
 *   <li>载荷为空且库中非空 = 整单删除：没有 {@code allowEmpty=true} 一律拒绝
 *       （三重闸门的后端一环，前端弹窗是第一环、严格行数校验是第三环）。
 * </ul>
 *
 * <p>⚠️ seqNo 以<b>载荷行序</b>为唯一口径（重排 1..n）：前端表格的行序就是用户看到的顺序，
 * 发来的 seqNo 有出入也以行序为准 —— 这样「行序没变的重发」不会因为序号字段的噪声被计成更新。
 *
 * <p>本方法处理的是一张单据：{@code documentNo} 为调用方给出的单据号，
 * 载荷行的 documentNo 已由调用方统一置为该值（见 {@code OcrConfirmController}）。
 */
public final class DocumentDiffPlanner {

    /** 载荷行：与实体解耦的纯数据，规划器不认识两种单据实体 */
    public record PayloadRow(Long id, String materialName, String materialCode,
                             BigDecimal qty, String unit, LocalDate date) {
    }

    /** 一条更新决策：载荷行下标 + 库中行 id（seqNo 由执行方按行序重排） */
    public record UpdateDecision(int payloadIndex, long dbId) {
    }

    /**
     * 规划结果。
     *
     * @param insertIndexes 要新增的载荷行下标（执行方负责清 id 再插）
     * @param updates       要按库中 id 更新的行
     * @param skipCount     字段全同、原样跳过的行数（不写库，也不为它补图）
     * @param deleteIds     要物理删除的库中行 id（append 模式恒为空）
     */
    public record Plan(List<Integer> insertIndexes, List<UpdateDecision> updates,
                       int skipCount, List<Long> deleteIds) {
    }

    private DocumentDiffPlanner() {
    }

    public static Plan plan(String documentNo, List<DocumentRowVO> stored, List<PayloadRow> payload,
                            DocSaveOptions options) {
        List<DocumentRowVO> st = stored == null ? List.of() : stored;
        List<PayloadRow> pl = payload == null ? List.of() : payload;

        Map<Long, DocumentRowVO> storedById = new HashMap<>();
        for (DocumentRowVO s : st) {
            if (s.getId() != null) {
                storedById.put(s.getId(), s);
            }
        }
        // 同三键的脏数据（历史遗留，只可能在日期为 NULL 的行之间——唯一索引不约束 NULL）：
        // 取 id 最小者，让判定结果不随查询顺序漂移
        Map<String, DocumentRowVO> storedByKey = new HashMap<>();
        for (DocumentRowVO s : st) {
            String k = DocMaterialDateKey.of(documentNo, s.getMaterialCode(), s.getDate());
            DocumentRowVO cur = storedByKey.get(k);
            if (cur == null || (s.getId() != null && cur.getId() != null && s.getId() < cur.getId())) {
                storedByKey.put(k, s);
            }
        }

        List<Integer> insertIndexes = new ArrayList<>();
        List<UpdateDecision> updates = new ArrayList<>();
        Set<Long> keptIds = new HashSet<>();
        int skipCount = 0;

        for (int i = 0; i < pl.size(); i++) {
            int rowNo = i + 1;
            PayloadRow row = pl.get(i);
            DocumentRowVO s;
            if (row.id() != null) {
                s = storedById.get(row.id());
                if (s == null) {
                    throw new DocumentConflictException(
                            "第" + rowNo + "行已不存在（可能已被他人删除），请重新载入后再保存");
                }
            } else {
                s = storedByKey.get(DocMaterialDateKey.of(documentNo, row.materialCode(), row.date()));
            }
            if (s != null && s.getId() != null) {
                keptIds.add(s.getId());
            }
            if (s == null) {
                insertIndexes.add(i);
            } else if (sameFields(s, row, rowNo)) {
                skipCount++;
            } else {
                updates.add(new UpdateDecision(i, s.getId()));
            }
        }

        // 整单删除闸门：载荷为空而库中非空，必须显式 allowEmpty=true
        if (pl.isEmpty() && !st.isEmpty() && !options.allowEmpty()) {
            throw new BusinessException("全部行被删除：整单删除需要显式确认（allowEmpty=true）");
        }

        List<Long> deleteIds = new ArrayList<>();
        if (options.strict()) {
            // 严格模式：行数被他人改过 -> 直接冲突。
            // ⚠️ 已知取舍：行数相同、字段被他人改过的并发保存仍是 last-write-wins
            // （不另做乐观锁，见变更-047 文档「风险与取舍」）
            if (st.size() != options.expectedRowCount()) {
                throw new DocumentConflictException("单据已被他人修改（载入时 " + options.expectedRowCount()
                        + " 行，当前 " + st.size() + " 行），请重新载入后再保存");
            }
            for (DocumentRowVO s : st) {
                if (s.getId() != null && !keptIds.contains(s.getId())) {
                    deleteIds.add(s.getId());
                }
            }
        } else {
            // append 模式：永不删除；但库中若有载荷覆盖不到的行（他人刚插的），提示重新载入
            int extra = 0;
            for (DocumentRowVO s : st) {
                if (s.getId() == null || !keptIds.contains(s.getId())) {
                    extra++;
                }
            }
            if (extra > 0) {
                throw new DocumentConflictException("库中该单据多出 " + extra + " 行，请重新载入后再保存");
            }
        }

        return new Plan(insertIndexes, updates, skipCount, deleteIds);
    }

    /** 字段是否全同（全同 = 跳过，不写库）。qty 必须走 {@link ImportDedupeUtil}，不另写数量比较 */
    private static boolean sameFields(DocumentRowVO s, PayloadRow row, int rowNo) {
        return Objects.equals(trim(s.getMaterialCode()), trim(row.materialCode()))
                && Objects.equals(trim(s.getMaterialName()), trim(row.materialName()))
                && Objects.equals(trim(s.getUnit()), trim(row.unit()))
                && Objects.equals(s.getDate(), row.date())
                // seqNo 以载荷行序为口径：行序没变的重发不该因为序号字段的噪声计成更新
                && Objects.equals(s.getSeqNo(), rowNo)
                && ImportDedupeUtil.isSameQty(s.getQty(), row.qty());
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }
}
