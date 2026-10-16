package org.example.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 容器底图的坐标（库表 {@code equipment_ledger.image_bounds}）—— 解析与校验，变更-081。
 *
 * <p><b>坐标口径（以库里现存的 4 行为准）</b>：JSON 里同时带**原图像素尺寸**与**罐体位置**，
 * 两者同一把尺子：
 * <pre>
 * {"width":708,"height":1520,"left":72,"right":627.5,"top":83,"bottom":1247,
 *  "tangent":218,"tangentBottom":1114}
 * </pre>
 * <ul>
 *   <li>{@code width/height} —— 底图**原图的像素尺寸**（已与 4 张内置图逐一对过：4/4 完全一致）；</li>
 *   <li>{@code left/right/top/bottom} —— 罐体轮廓框（液面与封头都按它定位）；</li>
 *   <li>{@code tangent/tangentBottom} —— 筒体的上/下**切线**，立式容器画液面必需
 *       （{@code WorkOrderList.vue} 的绘制段按它把液位映射到图上）；
 *       **平底容器没有下切线**（下封头深度为 null/0 时只给 {@code tangent}，见 150产品罐那一行）。</li>
 * </ul>
 *
 * <p>⚠️ 前端绘制时是 {@code s = IMAGE_W / bounds.width} 换算的，所以
 * 「原图像素」与「按宽度归一化（width=1、height=长宽比）」两种写法都能用 ——
 * 只要六个数字用同一把尺子。本项目**统一存原图像素**（与既有行一致）。
 * （{@code VesselVO}/{@code EquipmentLedger} 上原先写着「按宽度归一化」，与库里实际数据不符，
 * 已在本次一并改对。）
 *
 * <p>做成独立工具类而不是塞进 service：存量数据是人工按图量的，格式错一次就会让
 * 「下拉里少一台」而不报错；这里把「什么算合法」变成一处可单测的规则。
 */
public final class VesselBounds {

    private VesselBounds() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 必填的六个键：图宽高 + 罐体轮廓框 */
    private static final String[] REQUIRED = {"width", "height", "left", "right", "top", "bottom"};

    /**
     * 解析结果。{@code tangent}/{@code tangentBottom} 可空（卧式容器与平底容器没有）。
     */
    public record Values(BigDecimal width, BigDecimal height, BigDecimal left, BigDecimal right,
                         BigDecimal top, BigDecimal bottom,
                         BigDecimal tangent, BigDecimal tangentBottom) {
    }

    /**
     * 校验并解析。空白输入返回 {@code null}（= 未标定，是合法状态：设备可以先有底图再标定）。
     *
     * @throws BusinessException 格式或数值不合法 —— 文案面向维护页的使用者，可直接展示
     */
    public static Values parseOrNull(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new BusinessException("底图坐标不是合法的 JSON，请重新标定");
        }
        if (node == null || !node.isObject()) {
            throw new BusinessException("底图坐标必须是 JSON 对象，请重新标定");
        }
        for (String key : REQUIRED) {
            JsonNode v = node.get(key);
            if (v == null || !v.isNumber()) {
                throw new BusinessException("底图坐标缺少数值项「" + key + "」，请重新标定");
            }
        }
        BigDecimal width = node.get("width").decimalValue();
        BigDecimal height = node.get("height").decimalValue();
        BigDecimal left = node.get("left").decimalValue();
        BigDecimal right = node.get("right").decimalValue();
        BigDecimal top = node.get("top").decimalValue();
        BigDecimal bottom = node.get("bottom").decimalValue();

        if (width.signum() <= 0 || height.signum() <= 0) {
            throw new BusinessException("底图坐标的图宽高必须大于 0，请重新标定");
        }
        if (left.signum() < 0 || left.compareTo(right) >= 0 || right.compareTo(width) > 0) {
            throw new BusinessException("底图坐标的轮廓框左右边不合法（需 0 ≤ left < right ≤ width），请重新标定");
        }
        if (top.signum() < 0 || top.compareTo(bottom) >= 0 || bottom.compareTo(height) > 0) {
            throw new BusinessException("底图坐标的轮廓框上下边不合法（需 0 ≤ top < bottom ≤ height），请重新标定");
        }

        BigDecimal tangent = numberOrNull(node, "tangent");
        BigDecimal tangentBottom = numberOrNull(node, "tangentBottom");
        if (tangent != null && (tangent.compareTo(top) < 0 || tangent.compareTo(bottom) > 0)) {
            throw new BusinessException("底图坐标的上切线不在轮廓框内，请重新标定");
        }
        if (tangentBottom != null && (tangentBottom.compareTo(top) < 0 || tangentBottom.compareTo(bottom) > 0)) {
            throw new BusinessException("底图坐标的下切线不在轮廓框内，请重新标定");
        }
        // 只有下切线、没有上切线是没有意义的（绘制从上下切线各取一段）
        if (tangentBottom != null && tangent == null) {
            throw new BusinessException("底图坐标只给了下切线、缺上切线，请重新标定");
        }
        if (tangent != null && tangentBottom != null && tangent.compareTo(tangentBottom) >= 0) {
            throw new BusinessException("底图坐标的上下切线顺序不对（上切线应在下切线之上），请重新标定");
        }
        return new Values(width, height, left, right, top, bottom, tangent, tangentBottom);
    }

    /**
     * 保存路径用：空白 → {@code null}（表示没标定/不改），非空 → **校验通过后原样返回**。
     *
     * <p>不做「重排键序/改数字写法」的规范化：值的来源是前端的标定界面，
     * 原样存下来最便于日后对照现场（少一层"存进去的和传进来的不一样"的困惑）。
     */
    public static String requireValidOrNull(String json) {
        parseOrNull(json);
        return StringUtils.hasText(json) ? json.trim() : null;
    }

    private static BigDecimal numberOrNull(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v != null && v.isNumber()) ? v.decimalValue() : null;
    }
}
