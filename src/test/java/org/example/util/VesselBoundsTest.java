package org.example.util;

import org.example.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 底图坐标的校验（变更-081）。
 *
 * <p>为什么值得单测：坐标是**人工按图量出来**的，格式错一次不会报错，
 * 只会表现为「体积计算页的下拉里少一台」或「液面画歪」—— 两种都很难联想到坐标。
 * 这里把「什么算合法」钉死，并把面向使用者的文案也一起断言（它要能直接显示在维护页上）。
 *
 * <p>两种口径都要能用（前端按 {@code s = IMAGE_W / width} 换算）：
 * 「原图像素」（库里现存 4 行的写法）与「按宽度归一化」（width=1，早期样例的写法）。
 */
class VesselBoundsTest {

    /** 生产 674 行的原样值 —— 像素口径 */
    private static final String PIXELS = "{\"top\": 83, \"left\": 72, \"right\": 627.5, \"width\": 708, "
            + "\"bottom\": 1247, \"height\": 1520, \"tangent\": 218, \"tangentBottom\": 1114}";

    /** 归一化口径（width=1、height=长宽比），不带切线（卧式） */
    private static final String NORMALIZED = "{\"width\":1.0,\"height\":0.5,\"left\":0,\"right\":1,"
            + "\"top\":0,\"bottom\":0.5}";

    @Test
    @DisplayName("像素口径：八个数字都解出来（含上下切线）")
    void parsesPixelForm() {
        VesselBounds.Values v = VesselBounds.parseOrNull(PIXELS);

        assertNotNull(v);
        assertEquals(0, new BigDecimal("708").compareTo(v.width()));
        assertEquals(0, new BigDecimal("1520").compareTo(v.height()));
        assertEquals(0, new BigDecimal("72").compareTo(v.left()));
        assertEquals(0, new BigDecimal("627.5").compareTo(v.right()));
        assertEquals(0, new BigDecimal("83").compareTo(v.top()));
        assertEquals(0, new BigDecimal("1247").compareTo(v.bottom()));
        assertEquals(0, new BigDecimal("218").compareTo(v.tangent()));
        assertEquals(0, new BigDecimal("1114").compareTo(v.tangentBottom()));
    }

    @Test
    @DisplayName("归一化口径也能用，且切线可以为空（卧式/平底）")
    void parsesNormalizedFormWithoutTangents() {
        VesselBounds.Values v = VesselBounds.parseOrNull(NORMALIZED);

        assertNotNull(v);
        assertNull(v.tangent());
        assertNull(v.tangentBottom());
    }

    @Test
    @DisplayName("空白 = 未标定，是合法状态（先传图、后标定）")
    void blankMeansUncalibrated() {
        assertNull(VesselBounds.parseOrNull(null));
        assertNull(VesselBounds.parseOrNull(""));
        assertNull(VesselBounds.parseOrNull("   "));
        assertNull(VesselBounds.requireValidOrNull(null));
        assertNull(VesselBounds.requireValidOrNull(""));
    }

    @Test
    @DisplayName("保存路径：合法的原样返回（不做键序/数字写法的规范化）")
    void requireValidReturnsInputAsIs() {
        assertEquals(PIXELS, VesselBounds.requireValidOrNull("  " + PIXELS + "  "));
    }

    @Test
    @DisplayName("不是 JSON / 不是对象 → 拒")
    void rejectsNonJson() {
        assertTrue(assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull("{oops"))
                .getMessage().contains("不是合法的 JSON"));
        assertTrue(assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull("[1,2,3]"))
                .getMessage().contains("必须是 JSON 对象"));
    }

    @Test
    @DisplayName("缺数值项 → 拒，并点名缺的是哪一个")
    void rejectsMissingKey() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> VesselBounds.parseOrNull("{\"width\":708,\"height\":1520}"));
        assertTrue(e.getMessage().contains("left"), e.getMessage());
    }

    @Test
    @DisplayName("图宽高必须大于 0")
    void rejectsNonPositiveImageSize() {
        assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull(
                "{\"width\":0,\"height\":1520,\"left\":0,\"right\":0,\"top\":0,\"bottom\":1}"));
        assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull(
                "{\"width\":708,\"height\":-1,\"left\":0,\"right\":1,\"top\":0,\"bottom\":1}"));
    }

    @Test
    @DisplayName("轮廓框越界或次序颠倒 → 拒（左右 / 上下分别判定）")
    void rejectsBadBox() {
        // left >= right
        assertTrue(assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull(
                "{\"width\":708,\"height\":1520,\"left\":700,\"right\":700,\"top\":0,\"bottom\":1}"))
                .getMessage().contains("左右"));
        // right > width
        assertTrue(assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull(
                "{\"width\":708,\"height\":1520,\"left\":0,\"right\":709,\"top\":0,\"bottom\":1}"))
                .getMessage().contains("左右"));
        // top >= bottom
        assertTrue(assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull(
                "{\"width\":708,\"height\":1520,\"left\":0,\"right\":1,\"top\":5,\"bottom\":5}"))
                .getMessage().contains("上下"));
    }

    @Test
    @DisplayName("切线必须在轮廓框内，且上切线在下切线之上")
    void rejectsBadTangents() {
        // 切线落在框外
        assertTrue(assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull(
                "{\"width\":708,\"height\":1520,\"left\":0,\"right\":600,\"top\":100,\"bottom\":900,"
                        + "\"tangent\":50}")).getMessage().contains("上切线"));
        // 只有下切线
        assertTrue(assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull(
                "{\"width\":708,\"height\":1520,\"left\":0,\"right\":600,\"top\":100,\"bottom\":900,"
                        + "\"tangentBottom\":800}")).getMessage().contains("缺上切线"));
        // 上下颠倒
        assertTrue(assertThrows(BusinessException.class, () -> VesselBounds.parseOrNull(
                "{\"width\":708,\"height\":1520,\"left\":0,\"right\":600,\"top\":100,\"bottom\":900,"
                        + "\"tangent\":800,\"tangentBottom\":300}")).getMessage().contains("顺序"));
    }
}
