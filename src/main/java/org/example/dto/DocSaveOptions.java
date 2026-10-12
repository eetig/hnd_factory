package org.example.dto;

/**
 * document 模式落库开关（契约见《前后端改动统筹 · 变更-047》§4.1）。
 *
 * @param expectedRowCount 客户端「载入编辑」时库中的行数。
 *                         传了 = <b>严格模式</b>：校验行数一致（不符回 409），并允许删除行；
 *                         不传（null）= <b>append</b>：只增 / 改 / 跳，<b>永不删除</b>。
 * @param allowEmpty       严格模式且本次提交 rows 为空（整单删除）时必须显式传 true ——
 *                         整单删除三重闸门的最后一环：前端弹确认 → 本开关 → 行数校验，
 *                         缺一即拒（见 {@code DocumentDiffPlanner}）。
 */
public record DocSaveOptions(Integer expectedRowCount, boolean allowEmpty) {

    /** 是否严格模式（客户端带回了载入时的行数） */
    public boolean strict() {
        return expectedRowCount != null;
    }
}
