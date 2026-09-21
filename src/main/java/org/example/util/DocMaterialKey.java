package org.example.util;

/**
 * 业务唯一键：(单据号 + 物料编码)。
 * 同一张单据下会有多条不同物料的明细，因此单据号单独不唯一，
 * 需与物料编码组合才构成一条唯一记录。
 */
public final class DocMaterialKey {

    /** 分隔符用不可见字符 U+0001，避免与业务值本身冲突 */
    private static final String SEP = "\u0001";

    private DocMaterialKey() {
    }

    /** 组合键 */
    public static String of(String documentNo, String materialCode) {
        return (documentNo == null ? "" : documentNo.trim())
                + SEP
                + (materialCode == null ? "" : materialCode.trim());
    }

    /** 用于日志/错误提示的可读描述 */
    public static String label(String documentNo, String materialCode) {
        return "单据号 " + documentNo + " + 物料编码 " + materialCode;
    }
}
