package org.example.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数量比较是本次导入去重里唯一有认知陷阱的地方，这里把这几个坑钉死。
 */
class ImportDedupeUtilTest {

    @Test
    @DisplayName("scale 不同但数值相同 —— 必须判为相同，否则每次重导都会多传一次图片")
    void sameValueDifferentScale() {
        assertTrue(ImportDedupeUtil.isSameQty(new BigDecimal("2.0000"), new BigDecimal("2")));
        assertTrue(ImportDedupeUtil.isSameQty(new BigDecimal("2"), new BigDecimal("2.00")));
        assertTrue(ImportDedupeUtil.isSameQty(new BigDecimal("0"), new BigDecimal("0.0000")));
    }

    @Test
    @DisplayName("超过 4 位小数 —— MySQL 按 decimal(18,4) 舍入后再比，否则去重静默失效")
    void roundsToColumnScale() {
        // 入库时 2.00005 被存成 2.0001，重导必须仍判定为「相同」
        assertTrue(ImportDedupeUtil.isSameQty(new BigDecimal("2.0001"), new BigDecimal("2.00005")));
        assertTrue(ImportDedupeUtil.isSameQty(new BigDecimal("2.0000"), new BigDecimal("1.99999")));
    }

    @Test
    @DisplayName("数值确实不同 —— 判为不相同，走更新")
    void differentValue() {
        assertFalse(ImportDedupeUtil.isSameQty(new BigDecimal("2"), new BigDecimal("2.0001")));
        assertFalse(ImportDedupeUtil.isSameQty(new BigDecimal("2.0001"), new BigDecimal("2.0002")));
        assertFalse(ImportDedupeUtil.isSameQty(new BigDecimal("-1"), new BigDecimal("1")));
    }

    @Test
    @DisplayName("任一为 null 一律判为不相同 —— 宁可更新，也不要误跳过漏数据")
    void nullIsNeverSame() {
        assertFalse(ImportDedupeUtil.isSameQty(null, new BigDecimal("2")));
        assertFalse(ImportDedupeUtil.isSameQty(new BigDecimal("2"), null));
        assertFalse(ImportDedupeUtil.isSameQty(null, null));
    }

    @Test
    @DisplayName("边界：负零与科学计数法")
    void edgeCases() {
        assertTrue(ImportDedupeUtil.isSameQty(new BigDecimal("0.0000"), new BigDecimal("-0.0000")));
        assertTrue(ImportDedupeUtil.isSameQty(new BigDecimal("1000.0000"), new BigDecimal("1E+3")));
    }
}
