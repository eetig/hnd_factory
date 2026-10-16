package org.example.util;

import org.example.entity.EquipmentLedger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启用门槛（变更-081）：把「什么样的容器才允许启用」这条规则钉住。
 *
 * <p>规则的存在理由：体积计算页的下拉只收「启用 + 有底图 + 有坐标 + 几何齐全」的行
 * （{@code listVessels} 的 WHERE），而维护页原先允许把不合规的行点成「启用」——
 * 状态显示成功、下拉里却永远不出现，使用者只能以为页面坏了。
 *
 * <p><b>本测试里最有价值的一条</b>：{@link #productionRowsAreAllEnableable()} 用的是
 * **生产库那 4 行的原样字段值**。门槛一旦写严了，就会把正在被使用的设备锁死 ——
 * 那是比"漏拦"严重得多的故障，所以拿真数据当回归基线。
 */
class VesselEnableGateTest {

    // ================= 生产库 4 行的原样副本（2026-10-16 从 equipment_ledger 读出）=================

    /** id 674 甲醇计量罐：立式（3）、有上下封头、底图带上下切线 */
    private static EquipmentLedger methanol() {
        EquipmentLedger row = base(674L, "甲醇计量罐", 3);
        row.setInnerDiameter(new BigDecimal("2200.00"));
        row.setShellLength(new BigDecimal("3400.00"));
        row.setStraightFlange(new BigDecimal("40.00"));
        row.setTopHeadDepth(new BigDecimal("550.00"));
        row.setBottomHeadDepth(new BigDecimal("550.00"));
        row.setImageFile("vessel-methanol.png");
        row.setImageBounds("{\"top\": 83, \"left\": 72, \"right\": 627.5, \"width\": 708, "
                + "\"bottom\": 1247, \"height\": 1520, \"tangent\": 218, \"tangentBottom\": 1114}");
        return row;
    }

    /** id 724 三氯氢硅储罐：卧式（1），坐标里没有切线 —— 卧式不需要切线 */
    private static EquipmentLedger horizontal() {
        EquipmentLedger row = base(724L, "三氯氢硅储罐", 1);
        row.setInnerDiameter(new BigDecimal("2800.00"));
        row.setShellLength(new BigDecimal("5500.00"));
        row.setStraightFlange(new BigDecimal("40.00"));
        row.setTopHeadDepth(new BigDecimal("700.00"));
        row.setBottomHeadDepth(new BigDecimal("700.00"));
        row.setImageFile("vessel.png");
        row.setImageBounds("{\"top\": 131, \"left\": 75, \"right\": 2069, \"width\": 2150, "
                + "\"bottom\": 931, \"height\": 1060}");
        return row;
    }

    /** id 726 150产品罐：平底立式（2），下封头深度为 null、只有上切线 —— 平底不该要求下切线 */
    private static EquipmentLedger flatBottom() {
        EquipmentLedger row = base(726L, "150产品罐", 2);
        row.setInnerDiameter(new BigDecimal("3600.00"));
        row.setShellLength(new BigDecimal("4800.00"));
        row.setStraightFlange(new BigDecimal("0.00"));
        row.setTopHeadDepth(new BigDecimal("900.00"));
        row.setBottomHeadDepth(null);
        row.setImageFile("vessel-product150.png");
        row.setImageBounds("{\"top\": 63, \"left\": 131, \"right\": 1351, \"width\": 1760, "
                + "\"bottom\": 1930, \"height\": 1938, \"tangent\": 310}");
        return row;
    }

    /** id 729 再沸器：卧式带管束（4），**没有介质密度** —— 密度不该进门槛（否则锁死在用设备） */
    private static EquipmentLedger reboiler() {
        EquipmentLedger row = base(729L, "再沸器", 4);
        row.setInnerDiameter(new BigDecimal("2200.00"));
        row.setShellLength(new BigDecimal("4000.00"));
        row.setStraightFlange(new BigDecimal("40.00"));
        row.setTopHeadDepth(new BigDecimal("550.00"));
        row.setBottomHeadDepth(new BigDecimal("550.00"));
        row.setImageFile("vessel-reboiler.png");
        row.setImageBounds("{\"top\": 92, \"left\": 53, \"right\": 1377, \"width\": 1616, "
                + "\"bottom\": 659, \"height\": 820}");
        row.setDensity(null);
        row.setMedium(null);
        return row;
    }

    private static EquipmentLedger base(Long id, String name, Integer type) {
        EquipmentLedger row = new EquipmentLedger();
        row.setId(id);
        row.setEquipmentName(name);
        row.setWorkshop("罐区");
        row.setContainerType(type);
        row.setEnabled(1);
        return row;
    }

    // ================= 回归基线：别把在用的设备锁死 =================

    @Test
    @DisplayName("生产上正在使用的那 4 行都必须判为「可启用」")
    void productionRowsAreAllEnableable() {
        for (EquipmentLedger row : List.of(methanol(), horizontal(), flatBottom(), reboiler())) {
            List<String> problems = VesselEnableGate.reasons(row);
            assertTrue(problems.isEmpty(),
                    "生产在用的设备被判成不能启用，会把它锁死：id=" + row.getId() + " 原因=" + problems);
            assertNull(VesselEnableGate.reasonText(row));
            assertTrue(VesselEnableGate.canEnable(row));
        }
    }

    @Test
    @DisplayName("卧式（1）与再沸器（4）不需要切线；立式（2/3）需要")
    void tangentNeededOnlyForVertical() {
        assertFalse(VesselEnableGate.needsTangent(1));
        assertFalse(VesselEnableGate.needsTangent(4));
        assertTrue(VesselEnableGate.needsTangent(2));
        assertTrue(VesselEnableGate.needsTangent(3));
        assertTrue(VesselEnableGate.isHorizontal(1));
        assertTrue(VesselEnableGate.isHorizontal(4));
        assertFalse(VesselEnableGate.isHorizontal(3));
    }

    @Test
    @DisplayName("立式缺上切线 → 拒绝（下拉里的行画不出液面）")
    void verticalWithoutTangentIsRejected() {
        EquipmentLedger row = methanol();
        row.setImageBounds("{\"width\": 708, \"height\": 1520, \"left\": 72, \"right\": 627.5, "
                + "\"top\": 83, \"bottom\": 1247}");   // 少了 tangent / tangentBottom

        List<String> problems = VesselEnableGate.reasons(row);

        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("上切线")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("下切线")), problems.toString());
    }

    @Test
    @DisplayName("立式有下封头但缺下切线 → 拒绝；下封头深度为 0（平底）则不再要求")
    void bottomTangentRequiredOnlyWhenBottomHeadExists() {
        EquipmentLedger withHead = methanol();
        withHead.setImageBounds("{\"width\": 708, \"height\": 1520, \"left\": 72, \"right\": 627.5, "
                + "\"top\": 83, \"bottom\": 1247, \"tangent\": 218}");
        assertTrue(VesselEnableGate.reasons(withHead).stream().anyMatch(p -> p.contains("下切线")),
                VesselEnableGate.reasons(withHead).toString());

        EquipmentLedger flat = withHead;
        flat.setContainerType(2);            // 平底立式
        flat.setBottomHeadDepth(new BigDecimal("0"));
        assertTrue(VesselEnableGate.reasons(flat).isEmpty(), VesselEnableGate.reasons(flat).toString());
    }

    // ================= 缺项的文案 =================

    @Test
    @DisplayName("缺底图/坐标/几何 → 一次列全，且文案面向使用者")
    void listsEveryMissingItemAtOnce() {
        EquipmentLedger empty = base(680L, "三甲粗品罐", 3);

        List<String> problems = VesselEnableGate.reasons(empty);

        assertTrue(problems.contains("未配置容器底图"), problems.toString());
        assertTrue(problems.contains("底图未标定坐标"), problems.toString());
        assertTrue(problems.contains("未填内径（或为 0）"), problems.toString());
        assertTrue(problems.contains("未填筒体直段长"), problems.toString());
        assertTrue(problems.contains("未填封头直边（平底可填 0，但不能留空）"), problems.toString());
        assertTrue(problems.contains("未填上封头曲面深度（或为 0）"), problems.toString());
        assertTrue(problems.contains("未填下封头曲面深度（非平底容器）"), problems.toString());
        assertEquals(7, problems.size(), problems.toString());

        String text = VesselEnableGate.reasonText(empty);
        assertNotNull(text);
        assertTrue(text.startsWith("不能启用："), text);
        assertTrue(text.contains("；"), "多条原因要拼成一句：" + text);
    }

    @Test
    @DisplayName("容器类型没填 → 拒绝（朝向与分段都靠它）")
    void missingContainerTypeIsRejected() {
        EquipmentLedger row = methanol();
        row.setContainerType(null);

        assertTrue(VesselEnableGate.reasons(row).stream().anyMatch(p -> p.contains("容器类型")),
                VesselEnableGate.reasons(row).toString());
    }

    @Test
    @DisplayName("坐标 JSON 坏掉 → 拒绝，并把校验器的原话带出来（不抛异常、让整页挂掉）")
    void malformedBoundsAreReportedNotThrown() {
        EquipmentLedger row = methanol();
        row.setImageBounds("{\"width\": 708, \"height\": 1520}");

        List<String> problems = VesselEnableGate.reasons(row);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("left"), problems.toString());
    }

    @Test
    @DisplayName("平底立式（2）允许下封头深度为 null —— 它本来就是空的")
    void flatBottomAllowsNullBottomHead() {
        EquipmentLedger row = flatBottom();
        assertEquals(0, VesselEnableGate.reasons(row).size(), VesselEnableGate.reasons(row).toString());
        // 若把平底改成"必须有下封头"，这条会失败
        row.setContainerType(3);
        assertFalse(VesselEnableGate.reasons(row).isEmpty(), "立式却少了下封头深度，应当被拒");
    }
}
