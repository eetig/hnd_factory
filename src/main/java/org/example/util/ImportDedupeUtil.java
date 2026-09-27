package org.example.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 导入去重判定：单据号 + 物料编码 + 数量 三者全同 => 视为重复行，整行跳过
 * （不 insert、不 update，也不上传它的单据图片）。
 */
public final class ImportDedupeUtil {

    /** 数量列的精度：decimal(18,4) */
    private static final int QTY_SCALE = 4;

    private ImportDedupeUtil() {
    }

    /**
     * 两个数量是否视为「相同」。
     *
     * 有两处坑，缺一不可：
     *
     * 1) 不能用 equals。BigDecimal.equals 会把 scale 一起比：库里是 decimal(18,4) 存成 2.0000，
     *    Excel 解析出来的是 2，equals 返回 false，会被误判成「数量不同」而白白重传一次图片
     *    —— 恰好就是本次要修的那个毛病。
     *
     * 2) 光用 compareTo 也不够。库列是 decimal(18,4)，MySQL 入库时会把多余的位数四舍五入，
     *    而 Excel 解析出的 BigDecimal 保留原始位数：
     *        Excel 2.00005 → 入库被存成 2.0001
     *        重导时 2.00005 vs 2.0001 → compareTo != 0 → 判定「数量变了」→ 走更新 → 图片照旧重传
     *    去重会**静默失效**且无从察觉。所以两边都先归一到 4 位再比。
     *    用 HALF_UP 与 MySQL 对 DECIMAL 的舍入行为一致（半值远离零）。
     *
     * 任一为 null 一律视为「不相同」——宁可走更新，也不要误跳过把数据漏掉。
     */
    public static boolean isSameQty(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return false;
        }
        return a.setScale(QTY_SCALE, RoundingMode.HALF_UP)
                .compareTo(b.setScale(QTY_SCALE, RoundingMode.HALF_UP)) == 0;
    }
}
