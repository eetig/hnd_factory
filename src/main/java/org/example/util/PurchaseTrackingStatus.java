package org.example.util;

import org.example.entity.PurchaseTracking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 采购需求的**派生状态**（变更-096）。
 *
 * <p><b>为什么派生、不存列</b>：状态是 {@code OA 单号} / {@code 送货单} / {@code 要求到货日期}
 * 的函数。一旦落成数据库列，它就有机会和这几个字段不一致 —— 例如状态写着「已到货」而送货单
 * 被清空了、或者要求到货日期改早之后状态还停在「等货」。派生则永不脱节，代价只是每次算一遍
 * （一张流水账表，百级的量，这点开销可以忽略）。
 *
 * <p><b>判定顺序（先到货，再提单，最后看时间）</b>，越靠前优先级越高：
 * <ol>
 *   <li>有送货单图片 → {@link #ARRIVED}（若填了到货数量且小于需求数量 → {@link #PARTIAL}）
 *       —— 到货了就是到货了，哪怕晚了很多天，也不该继续显示「超期」；</li>
 *   <li>没有 OA 单号 → {@link #PENDING_OA} —— 需求登记了但还没提单，无从催；</li>
 *   <li>没有要求到货日期 → {@link #WAITING} —— 没有基准就算不出超期/临期；</li>
 *   <li>今天 &gt; 要求到货日 → {@link #OVERDUE}；</li>
 *   <li>距要求到货日 ≤ {@link #DUE_SOON_DAYS} 天 → {@link #DUE_SOON}；</li>
 *   <li>否则 → {@link #WAITING}。</li>
 * </ol>
 *
 * <p>⚠️ {@code PARTIAL} 只在**需求数量与到货数量都填了**才判得出来。历史数据没有数量
 * （变更-096 之前的行），一律按 {@link #ARRIVED} 处理 —— 宁可少报"部分到货"，
 * 也不要凭空猜一个缺口出来。
 */
public enum PurchaseTrackingStatus {

    /** 已登记需求，但还没提单到 OA */
    PENDING_OA("待提单"),

    /** 已提单、未到货，且还不到临期 */
    WAITING("等货"),

    /** 已提单、未到货，距要求到货日不超过 {@link #DUE_SOON_DAYS} 天（含当天） */
    DUE_SOON("临期"),

    /** 已提单、未到货，且已过要求到货日 */
    OVERDUE("超期"),

    /** 已到货（送货单已上传），且未填缺口 */
    ARRIVED("已到货"),

    /** 已到货，但到货数量小于需求数量 */
    PARTIAL("部分到货");

    /**
     * 距要求到货日不超过这个天数就算「临期」（含当天）。
     *
     * <p>变更-096 使用方确认的口径：提前 3 天开始提醒，好有缓冲时间去催采购。
     * 改这个值只需要改这一处 —— 汇总、列表、推送都读它。
     */
    public static final int DUE_SOON_DAYS = 3;

    private final String label;

    PurchaseTrackingStatus(String label) {
        this.label = label;
    }

    /** 页面显示用的中文文案 */
    public String getLabel() {
        return label;
    }

    /** 是否属于「已到货」这一族（含部分到货） */
    public boolean isArrived() {
        return this == ARRIVED || this == PARTIAL;
    }

    /** 是否属于「已提单但还没到货」这一族（等货/临期/超期） */
    public boolean isWaitingFamily() {
        return this == WAITING || this == DUE_SOON || this == OVERDUE;
    }

    /**
     * 派生状态。传 {@code today} 而不是内部取 {@code LocalDate.now()}，
     * 是为了让「列表」与「汇总」在同一次请求里用**同一天**，也便于单测注入固定日期。
     */
    public static PurchaseTrackingStatus of(PurchaseTracking row, LocalDate today) {
        if (row == null) {
            return PENDING_OA;
        }
        LocalDate day = today == null ? LocalDate.now() : today;

        if (hasText(row.getDeliveryNote())) {
            return isPartial(row) ? PARTIAL : ARRIVED;
        }
        if (!hasText(row.getOaNo())) {
            return PENDING_OA;
        }
        LocalDate due = row.getRequiredArrivalDate();
        if (due == null) {
            return WAITING;
        }
        if (day.isAfter(due)) {
            return OVERDUE;
        }
        return ChronoUnit.DAYS.between(day, due) <= DUE_SOON_DAYS ? DUE_SOON : WAITING;
    }

    /**
     * 超期天数（只有 {@link #OVERDUE} 时非空）。
     *
     * <p>用「今天 - 要求到货日」而不是到货日算：到货之后就不再是超期了，
     * 那条记录该显示 {@link #ARRIVED}/{@link #PARTIAL}，不需要再挂着超期天数。
     */
    public static Integer overdueDays(PurchaseTracking row, LocalDate today) {
        if (of(row, today) != OVERDUE) {
            return null;
        }
        LocalDate day = today == null ? LocalDate.now() : today;
        return (int) ChronoUnit.DAYS.between(row.getRequiredArrivalDate(), day);
    }

    /**
     * 距要求到货日的天数（已到货或无基准时为空；当天 = 0）。
     *
     * <p>负数不会出现 —— 过了要求到货日就归 {@link #OVERDUE} 了，那边用
     * {@link #overdueDays}。
     */
    public static Integer daysToDue(PurchaseTracking row, LocalDate today) {
        PurchaseTrackingStatus status = of(row, today);
        if (!status.isWaitingFamily()) {
            return null;
        }
        LocalDate due = row.getRequiredArrivalDate();
        if (due == null) {
            return null;
        }
        LocalDate day = today == null ? LocalDate.now() : today;
        return (int) ChronoUnit.DAYS.between(day, due);
    }

    /**
     * 是否部分到货：需求数量与到货数量都填了，且到货 &lt; 需求。
     *
     * <p>库里 {@code received_quantity} / {@code quantity} 都是可空列，
     * 任一为空都判不出来 —— 返回 false（"不知道就当全到了"），不要猜。
     */
    public static boolean isPartial(PurchaseTracking row) {
        if (row == null) {
            return false;
        }
        BigDecimal need = row.getQuantity();
        BigDecimal got = row.getReceivedQuantity();
        if (need == null || got == null) {
            return false;
        }
        return got.compareTo(need) < 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
