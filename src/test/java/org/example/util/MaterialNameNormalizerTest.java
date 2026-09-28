package org.example.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 名称归一化的边界。归一化是这个功能的地基 —— 它一旦过度（比如顺手去掉括号或连字符），
 * 就会把主数据里两个不同的物料压成同一个 key，进而错配编码。
 */
class MaterialNameNormalizerTest {

    @Test
    @DisplayName("去空白：半角、全角空格一律剔除（Excel 复制来的名称常夹带全角空格）")
    void removesWhitespace() {
        assertEquals("三氯氢硅", MaterialNameNormalizer.normalize("三 氯 氢 硅"));
        assertEquals("三氯氢硅", MaterialNameNormalizer.normalize("三　氯　氢　硅")); // U+3000 全角空格
        assertEquals("hndv150", MaterialNameNormalizer.normalize(" HND V150 "));
    }

    @Test
    @DisplayName("全角转半角：括号、连字符等 ASCII 区全角字符要归一，否则同一物料两个 key")
    void convertsFullWidthAscii() {
        assertEquals("hnd-tmos(h)", MaterialNameNormalizer.normalize("HND－TMOS（H）"));
        assertEquals("1000ml", MaterialNameNormalizer.normalize("１０００ｍｌ"));
    }

    @Test
    @DisplayName("大小写不敏感：仅影响英文/编码，中文不受影响")
    void lowerCases() {
        assertEquals(MaterialNameNormalizer.normalize("hnd-v150"), MaterialNameNormalizer.normalize("HND-V150"));
        assertEquals("三氯氢硅", MaterialNameNormalizer.normalize("三氯氢硅"));
    }

    @Test
    @DisplayName("刻意不做的归一：括号/连字符/下划线必须保留语义，否则会把不同物料压成同一个 key")
    void keepsMeaningfulDelimiters() {
        // 若哪天有人「顺手」把这些也归一掉，这几条会失败 —— 那正是主数据出现错配的开始
        assertEquals("hnd-tmos(h)", MaterialNameNormalizer.normalize("HND-TMOS(H)"));
        assertEquals("hnd-v150_辅料包", MaterialNameNormalizer.normalize("HND-V150_辅料包"));
        assertEquals("hnd-tmos(h)_辅料包", MaterialNameNormalizer.normalize("HND-TMOS(H)_辅料包"));
        // 归一化后「HND-TMOS(H)」不等于「HND-TMOS(H)_辅料包」——两者是两个不同物料
    }

    @Test
    @DisplayName("null 与空串安全，返回空串（调用方据此判空即可）")
    void handlesNull() {
        assertEquals("", MaterialNameNormalizer.normalize(null));
        assertEquals("", MaterialNameNormalizer.normalize(""));
        assertEquals("", MaterialNameNormalizer.normalize("   "));
    }
}
