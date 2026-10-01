package org.example.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 「每 mm 液位对应质量」换算：kg/mm = 每 mm 液位对应体积(m3/mm) × 介质密度(t/m3) × 1000。
 *
 * <p>台账里给的是体积（V1m3/mm），质量还要乘介质密度 —— 同一个罐装不同物料，
 * 每 mm 对应的质量并不相同，所以这两个量分开存，密度是第三条独立信息。
 *
 * <p>任一输入为空即返回 null（不假装算得出来）：宁可让调用方看到空值，
 * 也不要拿默认密度 1.0 兜底 —— 那会凭空冒出真实数值的错误结果。
 */
public final class MassPerMmCalculator {

    /** 与库表 mass_per_mm decimal(18,6) 对齐，避免返回给前端的精度多于库里的口径 */
    private static final int SCALE = 6;

    private MassPerMmCalculator() {
        // 纯函数工具类，不实例化
    }

    public static BigDecimal massPerMm(BigDecimal volumePerMm, BigDecimal density) {
        if (volumePerMm == null || density == null) {
            return null;
        }
        return volumePerMm.multiply(density)
                .multiply(BigDecimal.valueOf(1000))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }
}
