package org.example.util;

import org.example.dto.TankLevelSaveDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 录入校验的边界。
 *
 * <p>重点钉住「容器编号不能为空」—— 它是唯一键的一半，放空串进去不会报错，
 * 而是让同一日期能重复插入任意多条「无编号」记录，唯一键静默失效。
 */
class TankLevelValidatorTest {

    private TankLevelSaveDTO valid() {
        TankLevelSaveDTO dto = new TankLevelSaveDTO();
        dto.setRecordDate(LocalDate.of(2026, 8, 31));
        dto.setLocation("一车间");
        dto.setTankName("V150储罐A");
        dto.setTankCode("V150-A");
        dto.setLevelValue(new BigDecimal("1250"));
        dto.setTheoreticalWeight(new BigDecimal("1500"));
        return dto;
    }

    @Test
    @DisplayName("字段齐全即通过")
    void acceptsCompleteRecord() {
        assertNull(TankLevelValidator.validate(valid()));
    }

    @Test
    @DisplayName("液位与理论质量允许不填：0 与「没抄」是两回事，不强迫填 0")
    void acceptsMissingMeasures() {
        TankLevelSaveDTO dto = valid();
        dto.setLevelValue(null);
        dto.setTheoreticalWeight(null);
        assertNull(TankLevelValidator.validate(dto));
    }

    @Test
    @DisplayName("液位填 0 是合法值（不是缺失）")
    void acceptsZeroLevel() {
        TankLevelSaveDTO dto = valid();
        dto.setLevelValue(BigDecimal.ZERO);
        assertNull(TankLevelValidator.validate(dto));
    }

    @Test
    @DisplayName("容器编号为空即拦下 —— 空串会让唯一键 uk_date_tank 形同虚设")
    void rejectsBlankTankCode() {
        TankLevelSaveDTO dto = valid();
        dto.setTankCode("");
        assertNotNull(TankLevelValidator.validate(dto));

        dto.setTankCode("   ");
        assertNotNull(TankLevelValidator.validate(dto));

        dto.setTankCode(null);
        assertNotNull(TankLevelValidator.validate(dto));
    }

    @Test
    @DisplayName("日期 / 属地 / 容器名称为空即拦下，对应库表的 NOT NULL 列")
    void rejectsMissingRequiredFields() {
        TankLevelSaveDTO dto = valid();
        dto.setRecordDate(null);
        assertNotNull(TankLevelValidator.validate(dto));

        dto = valid();
        dto.setLocation("  ");
        assertNotNull(TankLevelValidator.validate(dto));

        dto = valid();
        dto.setTankName("");
        assertNotNull(TankLevelValidator.validate(dto));
    }

    @Test
    @DisplayName("负的液位 / 理论质量拦下：抄表不会出现负数，出现即是敲错")
    void rejectsNegativeMeasures() {
        TankLevelSaveDTO dto = valid();
        dto.setLevelValue(new BigDecimal("-1"));
        assertNotNull(TankLevelValidator.validate(dto));

        dto = valid();
        dto.setTheoreticalWeight(new BigDecimal("-0.5"));
        assertNotNull(TankLevelValidator.validate(dto));
    }

    @Test
    @DisplayName("入参为 null 不抛异常，返回可读消息")
    void handlesNullDto() {
        assertNotNull(TankLevelValidator.validate(null));
    }
}
