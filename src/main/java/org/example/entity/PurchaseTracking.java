package org.example.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购需求详情（需求登记 → 提 OA 单 → 到货）。
 *
 * <p>列与页面对齐：序号（id）/ 需求人 / 提出需求日期 / 物料名称 / 物料编码 / 规格型号 /
 * 类别 / 数量 / 单位 / 要求到货日期 / OA 单号 / 送货单图片 / 实际到货数量 / 实际到货日期（+ 备注与时间戳）。
 * 表定义见 {@code docs/schema.sql} 第 1 部分末尾；变更-096 补的 5 列见该文件第 20 步迁移。
 *
 * <p><b>物料名称与编码是快照文本，不是外键</b>：采购记录要能独立读懂，
 * 历史单据也不该被后来的主数据改名影响。
 *
 * <p><b>物料编码刻意不做唯一键</b>：采购是流水账，同一物料在不同需求批次里本来就会重复出现；
 * 而且 SAP 源数据里编码本身就不唯一（{@code material_master} 246 行 / 241 个编码，
 * 有 5 个编码各对应两个**真实不同**的物料 —— 1000/500/250/100ml 四种瓶子共用一个外贸编码、
 * {@code 114002501} 同时是「HND-2171前馏份」与「HND-2171过渡馏份」）。
 * 强加唯一键会丢信息，所以这里只有普通索引。
 *
 * <p><b>没有「状态」列，状态是派生的</b>（变更-096 的刻意设计）：见
 * {@code org.example.support.PurchaseTrackingStatus}。存一个状态列一定会和实际字段打架
 * （状态写着「已到货」但送货单被清空了），派生出来则永不脱节。
 *
 * <p>⚠️ {@code requester} / {@code materialName} / {@code materialCode} /
 * {@code oaNo} / {@code deliveryNote} 在库里是 {@code NOT NULL DEFAULT ''}
 * ——「还没提单」「还没到货」用**空串**表示，不是 NULL。理由见 schema.sql 第 170 行那段：
 * MySQL 唯一索引不约束 NULL，可空列上将来想加唯一键会形同虚设。
 * 所以服务端写库前要把 null 归一成空串（见 {@code PurchaseTrackingServiceImpl}）。
 */
@Data
@TableName("purchase_tracking")
public class PurchaseTracking {

    @TableId(type = IdType.AUTO)
    private Long id;

    /*
     * ⚠️ 下面 14 个可编辑字段全部标 `updateStrategy = ALWAYS` —— 这是本项目的
     * 「整行覆写」契约（与 EquipmentLedger 同一套，见 ADR 0002）：
     * 保存时以提交上来的整份表单为准，**没填的字段就是空**。
     * 不标 ALWAYS 的话 MyBatis-Plus 默认会跳过 null 字段 ⇒ 使用者把「要求到货日期」
     * 或「规格型号」清空后保存，旧值会**留在库里**、界面上却显示已清空 —— 静默不一致。
     * 变更-096 新加的 5 列同样适用：把「数量」清空就得真的写 NULL，否则页面显示「—」
     * 而库里还留着旧数字，汇总时按哪个算都可能是错的。
     */

    /** 需求人 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String requester;

    /** 提出需求日期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate requestDate;

    /** 物料名称（快照） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String materialName;

    /** 物料编码（快照） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String materialCode;

    /** 规格型号 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spec;

    /** 用途（这料拿去干什么用；变更-098 起） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String purpose;

    /** 类别（备件 / 五金 / 原料 / 设备 / 其他；变更-096 起） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String category;

    /** 需求数量（变更-096 起） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

    /** 单位（个 / 台 / 套 / …；变更-096 起） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unit;

    /** 要求到货日期（超期/临期的判定基准，源自 OA 单上的希望到货日期） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate requiredArrivalDate;

    /**
     * OA 单号（还没提单时空串）。
     *
     * <p>⚠️ 物理列名沿用历史上的 {@code purchase_order_no}（变更-096 明确语义为「OA 提单号」）。
     * 不重建列名的原因：改名要连 {@code idx_purchase_order_no} 一起重建，纯增风险；
     * 而语义靠这里和 schema 注释说清楚就够了。
     */
    @TableField(value = "purchase_order_no", updateStrategy = FieldStrategy.ALWAYS)
    private String oaNo;

    /**
     * OA 表单链接（变更-100 起；还没粘过时为空串）。
     *
     * <p>为什么要存整条 URL 而不是 favId：OA 的「按单号筛列表」地址
     * （{@code /ssc/fee/#/j_path=…&criteria=docNumber:…}）在**未登录时会被 302 到
     * login.jsp，登录后落回门户首页**（2026-10-18 实测），所以靠单号拼链接这条路走不通；
     * 只有用户从浏览器地址栏复制的那条 {@code fsscFeeMain.do?method=view&fdId=…}
     * 才能直达表单。存整条 URL 的好处是零解析、零猜测，用户粘什么就打开什么。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String oaUrl;

    /** 送货单图片路径（还没到货时空串；变更-094 起是图片，老数据可能是文本） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String deliveryNote;

    /** 实际到货数量（变更-096 起；< quantity 即部分到货） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal receivedQuantity;

    /** 实际到货日期（变更-096 起；算出「提前/延后几天」靠它） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate actualArrivalDate;

    /** 备注 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
