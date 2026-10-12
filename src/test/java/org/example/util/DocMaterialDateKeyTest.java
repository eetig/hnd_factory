package org.example.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三键工具（单据号 + 物料编码 + 日期）—— 变更-047。
 *
 * <p>它是应用层与库表唯一键 {@code uk_document_no_material_date} 的对齐点，
 * 尤其是 NULL 日期那一格：MySQL 唯一索引不约束 NULL，应用层必须自己归一，
 * 否则「日期为空的旧数据」会在两键/三键判定之间摇摆。
 */
class DocMaterialDateKeyTest {

    @Test
    @DisplayName("★ NULL 日期归一成空段：与任何真实日期都不同键，且同一个输入恒得同一个键")
    void nullDateNeverCollidesWithRealDate() {
        // NULL 与真实日期必须区分开（读库时 NULL 日期是历史数据，不能被当成某一天）
        assertNotEquals(DocMaterialDateKey.of("D1", "M1", null),
                DocMaterialDateKey.of("D1", "M1", LocalDate.of(2026, 9, 20)));
        // 归一化是纯函数：两次调用（比如「查库」与「比对」各算一次）结果必须一样，
        // 否则同一行会在两处被判成两个键 → 误插重复行
        assertEquals(DocMaterialDateKey.of("D1", "M1", null), DocMaterialDateKey.of("D1", "M1", null));
    }

    @Test
    @DisplayName("不同日期 -> 不同键（D4：同单据同物料不同日期要能各存一行）")
    void differentDatesDiffer() {
        assertNotEquals(
                DocMaterialDateKey.of("D1", "M1", LocalDate.of(2026, 9, 19)),
                DocMaterialDateKey.of("D1", "M1", LocalDate.of(2026, 9, 20)));
    }

    @Test
    @DisplayName("单据号 / 物料编码 两侧 trim，且不含分隔符冲突（业务值里出现空格不该造出两个键）")
    void trimsBothSides() {
        assertEquals(DocMaterialDateKey.of(" D1 ", " M1 ", null), DocMaterialDateKey.of("D1", "M1", null));
        assertNotEquals(DocMaterialDateKey.of("D1", "M1", null), DocMaterialDateKey.of("D1M1", "", null));
    }

    @Test
    @DisplayName("可读描述用于文件内重复的报错文案（含日期，日期为空时写明「（空）」）")
    void labelIsReadable() {
        assertTrue(DocMaterialDateKey.label("D1", "M1", LocalDate.of(2026, 9, 20))
                .contains("2026-09-20"));
        assertTrue(DocMaterialDateKey.label("D1", "M1", null).contains("（空）"));
    }
}
