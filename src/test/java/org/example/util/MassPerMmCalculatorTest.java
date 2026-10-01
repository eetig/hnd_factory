package org.example.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 「每 mm 液位对应质量」换算的边界。
 *
 * <p>这个换算直接决定月底抄表算出的理论质量，口径错一倍都不会报错、只会静默算错，
 * 所以把「缺输入就返回 null」这条也钉成用例。
 */
class MassPerMmCalculatorTest {

    @Test
    @DisplayName("甲醇储罐：每mm体积 0.0212 m3/mm × 密度 0.792 t/m3 = 16.7904 kg/mm")
    void computesMassPerMm() {
        assertEquals(new BigDecimal("16.790400"),
                MassPerMmCalculator.massPerMm(new BigDecimal("0.0212"), new BigDecimal("0.792")));
    }

    @Test
    @DisplayName("小口径缓冲罐：0.000385 m3/mm 这类值按 6 位小数保留，不被抹平")
    void keepsSmallValues() {
        // 0.000385 × 0.792 = 0.00030492 t/mm → 0.30492 kg/mm；
        // 若哪天把入参精度降成 4 位小数，这里会变成 0.316800 —— 正是要拦住的回归
        assertEquals(new BigDecimal("0.304920"),
                MassPerMmCalculator.massPerMm(new BigDecimal("0.000385"), new BigDecimal("0.792")));
    }

    @Test
    @DisplayName("任一输入缺失即返回 null，绝不拿默认密度 1.0 兜底")
    void returnsNullWhenInputMissing() {
        assertNull(MassPerMmCalculator.massPerMm(null, new BigDecimal("0.792")));
        assertNull(MassPerMmCalculator.massPerMm(new BigDecimal("0.0212"), null));
        assertNull(MassPerMmCalculator.massPerMm(null, null));
    }

    @Test
    @DisplayName("密度为 1 的水当量：质量数值等于体积数值 × 1000")
    void waterEquivalent() {
        assertEquals(new BigDecimal("1.000000"),
                MassPerMmCalculator.massPerMm(new BigDecimal("0.001"), BigDecimal.ONE));
    }
}
