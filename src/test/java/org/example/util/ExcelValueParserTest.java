package org.example.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 单元格取值的统一口径。
 *
 * <p>这些断言同时是**回归网**。统一之前，同一份 Excel 在不同单据类型里结果不同：
 * <ul>
 *   <li>库存汇总那份 {@code parseDecimal} 剥全角逗号，其余四份不剥 —— {@code "1，000.5"}
 *       在库存汇总是 1000.5，在领料汇总直接报「领料数量无法解析」整行失败；</li>
 *   <li>{@code parseInt} 有两个版本 —— {@code "5.0"} 在工单/货物移动里失败、在领料/入库里成 5。</li>
 * </ul>
 * 现在口径只有一处，谁再「顺手改一下」都会在这里红。
 */
class ExcelValueParserTest {

    @Test
    @DisplayName("数量：半角/全角千分位与空格都要能剥（原先只有库存汇总剥全角）")
    void parsesThousandSeparators() {
        assertEquals(0, new BigDecimal("1000.5").compareTo(ExcelValueParser.parseDecimal("1，000.5")));
        assertEquals(0, new BigDecimal("1000.5").compareTo(ExcelValueParser.parseDecimal("1,000.5")));
        assertEquals(0, new BigDecimal("6000.000").compareTo(ExcelValueParser.parseDecimal("6,000.000")));
        assertEquals(0, new BigDecimal("12").compareTo(ExcelValueParser.parseDecimal(" 12 ")));
        assertEquals(0, new BigDecimal("9482.000").compareTo(ExcelValueParser.parseDecimal("9,482.000")));
    }

    @Test
    @DisplayName("数量：解析不出返回 null，由调用方的校验给出业务文案")
    void returnsNullForUnparsableDecimal() {
        assertNull(ExcelValueParser.parseDecimal("待盘点"));
        assertNull(ExcelValueParser.parseDecimal(""));
        assertNull(ExcelValueParser.parseDecimal("   "));
        assertNull(ExcelValueParser.parseDecimal(null));
    }

    @Test
    @DisplayName("整数：Excel 常把整数列写成 5.0，两个版本统一成宽容的那个")
    void parsesIntegerWrittenAsDecimal() {
        assertEquals(5, ExcelValueParser.parseInt("5.0").intValue());
        assertEquals(5, ExcelValueParser.parseInt("5").intValue());
        assertEquals(7, ExcelValueParser.parseInt(" 7 ").intValue());
        assertEquals(0, ExcelValueParser.parseInt("0.0").intValue());
        assertNull(ExcelValueParser.parseInt("第一项"));
        assertNull(ExcelValueParser.parseInt(null));
    }

    @Test
    @DisplayName("日期：5 种写法 + Excel 序列号（25569 = 1970-01-01，外部锚点）")
    void parsesDateFormats() {
        assertEquals(LocalDate.of(2026, 8, 6), ExcelValueParser.parseDate("2026-08-06"));
        assertEquals(LocalDate.of(2026, 8, 6), ExcelValueParser.parseDate("2026/8/6"));
        assertEquals(LocalDate.of(2026, 8, 6), ExcelValueParser.parseDate("2026.8.6"));
        assertEquals(LocalDate.of(2026, 8, 6), ExcelValueParser.parseDate("20260806"));
        assertEquals(LocalDate.of(2026, 8, 6), ExcelValueParser.parseDate("8/6/2026"));
        assertEquals(LocalDate.of(1970, 1, 1), ExcelValueParser.parseDate("25569"));
        assertNull(ExcelValueParser.parseDate("八月六日"));
        assertNull(ExcelValueParser.parseDate(null));
    }

    @Test
    @DisplayName("trim：null 仍 null、空串不转 null —— 原 6 份拷贝的语义，迁移不改行为")
    void trimsWithoutChangingNullSemantics() {
        assertNull(ExcelValueParser.trim(null));
        assertEquals("", ExcelValueParser.trim("   "));
        assertEquals("硅粉", ExcelValueParser.trim(" 硅粉 "));
    }
}
