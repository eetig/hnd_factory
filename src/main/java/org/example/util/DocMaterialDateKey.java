package org.example.util;

import java.time.LocalDate;

/**
 * 业务唯一键：(单据号 + 物料编码 + 日期)，变更-047 起由二键扩展。
 *
 * <p>同一张单据下会有多条不同物料的明细，因此单据号单独不唯一；
 * 「同一物料、不同日期」也要能各存一行（使用方 2026-10-12 定的口径 D4），
 * 所以日期必须进键，与库表唯一键 {@code uk_document_no_material_date} 一一对应。
 *
 * <p>⚠️ 日期为 NULL 时归一成**空串**：MySQL 的唯一索引不约束 NULL
 * （{@code material_stock} / {@code tank_level_record} 都踩过同一个坑），
 * 读写两侧都必须走这里取键，才能保证应用层对「日期为空的旧数据」的判定
 * 与二键时代完全一致（旧数据天然满足新键，迁移不动它们）。
 */
public final class DocMaterialDateKey {

    /** 分隔符用不可见字符 U+0001，避免与业务值本身冲突 */
    private static final String SEP = "\u0001";

    private DocMaterialDateKey() {
    }

    /** 组合键 */
    public static String of(String documentNo, String materialCode, LocalDate date) {
        return (documentNo == null ? "" : documentNo.trim())
                + SEP
                + (materialCode == null ? "" : materialCode.trim())
                + SEP
                + (date == null ? "" : date.toString());
    }

    /** 用于日志/错误提示的可读描述 */
    public static String label(String documentNo, String materialCode, LocalDate date) {
        return "单据号 " + documentNo + " + 物料编码 " + materialCode
                + " + 日期 " + (date == null ? "（空）" : date);
    }
}
