package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 「采购需求详情」列表项（页面上就是那一行）。
 *
 * <p>前半段字段与实体一一对应（物料名称与编码在本表里是快照文本，见实体注释）；
 * <b>后半段是本表不存在的派生列</b>（{@code status} / {@code overdueDays} /
 * {@code daysToDue} / {@code partial}），由 {@code PurchaseTrackingServiceImpl#toVO}
 * 用 {@code PurchaseTrackingStatus} 算出来。
 *
 * <p>**之所以单独建 VO 而不是直接回实体**，这就是理由：实体是库表形状，VO 是页面形状。
 * 派生状态刻意不落库（见实体注释），所以它只能长在 VO 上 —— 也正好应了本类原来那句
 * 「将来页面要加一列派生值时只改 VO，不必动实体」。
 *
 * <p>⚠️ 前端**不要自己重算** {@code status}：超期/临期的口径（提前几天算临期、
 * 到货后还算不算超期）只写在 {@code PurchaseTrackingStatus} 一处，
 * 前端照抄一份迟早会和后端不一致。
 */
@Data
public class PurchaseTrackingVO {

    // ===== 与库表一一对应的字段 =====

    /** 序号 */
    private Long id;

    /** 需求人 */
    private String requester;

    /** 提出需求日期 */
    private LocalDate requestDate;

    /** 物料名称（快照） */
    private String materialName;

    /** 物料编码（快照，可能为空 —— 编码后面才定） */
    private String materialCode;

    /** 规格型号 */
    private String spec;

    /** 用途（这料拿去干什么用；变更-098 起） */
    private String purpose;

    /** 类别（备件/五金/原料/设备/其他；NULL = 未分类） */
    private String category;

    /** 需求数量（NULL = 历史数据未记） */
    private BigDecimal quantity;

    /** 单位（个/台/套/…；NULL = 未填） */
    private String unit;

    /** 要求到货日期（超期/临期的判定基准） */
    private LocalDate requiredArrivalDate;

    /** OA 单号（空串 = 还没提单）。变更-096 起语义明确：是 OA 提单号，不是采购订单号 */
    private String oaNo;

    /** OA 表单链接（空串 = 还没粘过；变更-100 起）。页面把单号渲染成指向它的链接 */
    private String oaUrl;

    /** 送货单图片路径（空串 = 还没到货） */
    private String deliveryNote;

    /** 实际到货数量（NULL = 未记；< quantity 即部分到货） */
    private BigDecimal receivedQuantity;

    /** 实际到货日期（NULL = 还没到货） */
    private LocalDate actualArrivalDate;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ===== 派生列（不落库，见类注释） =====

    /**
     * 派生状态，取值 {@code PENDING_OA} / {@code WAITING} / {@code DUE_SOON} /
     * {@code OVERDUE} / {@code ARRIVED} / {@code PARTIAL}。
     *
     * <p>给前端做筛选和着色用；显示文案请用 {@link #statusLabel}，别在前端维护一份映射表。
     */
    private String status;

    /** 派生状态的中文文案（待提单 / 等货 / 临期 / 超期 / 已到货 / 部分到货） */
    private String statusLabel;

    /** 超期天数（只有 status=OVERDUE 时非空） */
    private Integer overdueDays;

    /** 距要求到货日的天数（已到货、待提单 或没有要求到货日时为空；当天 = 0） */
    private Integer daysToDue;

    /** 是否部分到货（需求数量与到货数量都填了，且到货 < 需求） */
    private Boolean partial;
}
