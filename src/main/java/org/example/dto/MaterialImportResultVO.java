package org.example.dto;

import lombok.Data;

/**
 * 物料导入结果（第二阶段 commit 的返回）。
 *
 * <p>注意 {@code removedDisabled}：文件里消失的键不是删除，而是置 {@code status=0}
 * （软停用）—— 主数据一旦删掉，历史单据里的编码就成了孤儿，查不到名字。
 */
@Data
public class MaterialImportResultVO {
    private int inserted;
    private int updated;
    private int disabled;
    /** 文件里停止标记为「待禁用」的行数（仅用于展示，不单独落库动作） */
    private int pendingDisabled;
    private String fileName;
}
