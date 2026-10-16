package org.example.util;

import org.example.entity.EquipmentLedger;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 「能不能启用一台容器」的判定（变更-081）。
 *
 * <p>背景：体积计算页的下拉只列出「启用 + 有底图 + 有坐标 + 几何填齐」的行
 * （{@code EquipmentLedgerServiceImpl#listVessels} 的 WHERE 就是这么写的）。
 * 于是维护页会出现一种**没有任何提示的失败**：把一台没底图的设备点成「启用」，
 * 保存成功、状态显示"启用"，但它**永远不出现在下拉里** —— 使用者只会觉得"页面坏了"。
 *
 * <p>本类把「启用」这件事的前置条件显式化，给出一句人能看懂的原因，用在两处：
 * <ul>
 *   <li>{@code listPage} 的每一行：算出原因 → {@code EquipmentLedgerVO.enableBlockReason}
 *       → 维护页据此把【启用】按钮置灰并显示原因（**规则只有这一份**，前端不重复实现）；</li>
 *   <li>{@code setEnabled(id, true)}：启用前校验，不合格就抛业务异常，前端直接展示这句话。</li>
 * </ul>
 *
 * <p><b>刻意不纳入门槛的两项</b>：
 * <ul>
 *   <li>{@code density}/{@code medium} —— 体积照算，只影响"换算成吨"那半截。
 *       再沸器（id 729）现在就没有密度、且已在线使用，把它拦下来等于把在用设备锁死；</li>
 *   <li>{@code volumePerMm} 等旧口径列 —— 该页自己按几何算，不用它们（见 schema.sql）。</li>
 * </ul>
 *
 * <p><b>容器类型的口径</b>（实体的注释）：1 卧式、2 平底立式、3 立式、4 再沸器（卧式带管束）。
 * 「卧式」= 1 或 4：这两类不需要切线（它们没有上下封头的分段概念，绘制走另一条分支）；
 * 「立式」= 2 或 3：必须有上切线，且**下封头存在时**必须有下切线。
 */
public final class VesselEnableGate {

    private VesselEnableGate() {
    }

    private static final int TYPE_HORIZONTAL = 1;
    private static final int TYPE_FLAT_BOTTOM = 2;
    private static final int TYPE_VERTICAL = 3;
    private static final int TYPE_REBOILER = 4;

    /**
     * 列出「不能启用」的全部原因；返回空列表 = 可以启用。
     *
     * <p>一次给全（而不是只报第一条）：维护页的人一次就能把缺的东西补齐，
     * 不必"改一个、再被拒一次"。
     */
    public static List<String> reasons(EquipmentLedger row) {
        List<String> problems = new ArrayList<>();
        if (row == null) {
            return List.of("设备不存在");
        }
        Integer type = row.getContainerType();
        if (type == null) {
            problems.add("未填容器类型（1 卧式 / 2 平底立式 / 3 立式 / 4 再沸器）");
        }

        // ---- 底图与坐标 ----
        if (!StringUtils.hasText(row.getImageFile())) {
            problems.add("未配置容器底图");
        }
        VesselBounds.Values bounds = null;
        if (!StringUtils.hasText(row.getImageBounds())) {
            problems.add("底图未标定坐标");
        } else {
            try {
                bounds = VesselBounds.parseOrNull(row.getImageBounds());
            } catch (RuntimeException e) {
                // 已经把「哪一项不合法」写进了消息里，直接透传给使用者
                problems.add(e.getMessage());
            }
        }

        // ---- 几何五件套 ----
        boolean flatBottom = type != null && type == TYPE_FLAT_BOTTOM;
        if (row.getInnerDiameter() == null || row.getInnerDiameter().signum() <= 0) {
            problems.add("未填内径（或为 0）");
        }
        if (row.getShellLength() == null || row.getShellLength().signum() < 0) {
            problems.add("未填筒体直段长");
        }
        if (row.getStraightFlange() == null) {
            problems.add("未填封头直边（平底可填 0，但不能留空）");
        }
        if (row.getTopHeadDepth() == null || row.getTopHeadDepth().signum() <= 0) {
            problems.add("未填上封头曲面深度（或为 0）");
        }
        // 平底立式的下封头本来就是空的（null = 平底，见实体注释）；其余型式必须有
        if (!flatBottom && (row.getBottomHeadDepth() == null || row.getBottomHeadDepth().signum() <= 0)) {
            problems.add("未填下封头曲面深度（非平底容器）");
        }

        // ---- 切线：立式才需要 ----
        boolean vertical = type != null && (type == TYPE_FLAT_BOTTOM || type == TYPE_VERTICAL);
        if (vertical && bounds != null) {
            if (bounds.tangent() == null) {
                problems.add("底图未标出筒体上切线");
            }
            boolean hasBottomHead = row.getBottomHeadDepth() != null && row.getBottomHeadDepth().signum() > 0;
            if (hasBottomHead && bounds.tangentBottom() == null) {
                problems.add("底图未标出筒体下切线（有下封头）");
            }
        }
        return problems;
    }

    /** 可启用判定 */
    public static boolean canEnable(EquipmentLedger row) {
        return reasons(row).isEmpty();
    }

    /** 一行文案，直接给使用者看；可启用时返回 {@code null} */
    public static String reasonText(EquipmentLedger row) {
        List<String> problems = reasons(row);
        return problems.isEmpty() ? null : "不能启用：" + String.join("；", problems);
    }

    /** 该型式是否为卧式（1 卧式 / 4 再沸器）—— 供调用方复用，避免各处再写一遍 1/4 判断 */
    public static boolean isHorizontal(Integer containerType) {
        return containerType != null
                && (containerType == TYPE_HORIZONTAL || containerType == TYPE_REBOILER);
    }

    /** 该型式是否需要切线（立式 2/3） */
    public static boolean needsTangent(Integer containerType) {
        return containerType != null
                && (containerType == TYPE_FLAT_BOTTOM || containerType == TYPE_VERTICAL);
    }
}
