package org.example.util;

import org.example.dto.TankLevelSaveDTO;

import java.math.BigDecimal;

/**
 * 月底储罐液位记录的录入校验（纯函数，无 Spring 依赖）。
 *
 * <p>规则集中在这里而不是散在 Service 里，理由与 {@link MassPerMmCalculator} 相同：
 * 这些约束对着的是库表的 NOT NULL 与唯一键，改了一处而漏改另一处时，
 * 表现是「页面上能存、存完库里报 500」，排查要跨前后端。集中成纯函数后可以直接钉用例。
 *
 * <p>只返回**首个**错误：前端一次只弹一条提示，攒一列错误清单再让用户逐条对，
 * 不如先把第一处改掉。
 */
public final class TankLevelValidator {

    private TankLevelValidator() {
    }

    /**
     * 校验一条待保存的记录。
     *
     * @return {@code null} 表示通过；否则是给用户看的错误消息
     */
    public static String validate(TankLevelSaveDTO dto) {
        if (dto == null) {
            return "请求内容为空。";
        }
        if (dto.getRecordDate() == null) {
            return "请填写记录日期。";
        }
        if (isBlank(dto.getLocation())) {
            return "请填写属地。";
        }
        if (isBlank(dto.getTankName())) {
            return "请填写容器名称。";
        }
        // 容器编号【不再校验】：界面（hnd_factory_UI）已撤掉这一栏，新增的记录必然没有编号，
        // 再拦就是「页面上根本填不了、却被告知必须填」。空编号落库后由唯一键
        // uk_date_tank (record_date, tank_code) 兜底 —— 此时它退化成「一天一条」，
        // 撞上时 Service 会给可读提示（见 TankLevelRecordServiceImpl.duplicateMessage）。
        if (isNegative(dto.getLevelValue())) {
            return "容器液位不能为负数。";
        }
        if (isNegative(dto.getTheoreticalWeight())) {
            return "理论质量不能为负数。";
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isNegative(BigDecimal value) {
        // 允许为空：抄表时液位或理论质量可能确实没记，不强迫填 0 —— 0 与「没抄」是两回事
        return value != null && value.signum() < 0;
    }
}
