package org.example.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 「采购需求详情」的保存入参（白名单）。
 *
 * <p>为什么不直接收实体（{@code MaterialStockController} 那种写法）：收实体等于让调用方
 * 任意指定主键与时间戳等列。本项目已经把这条定为不变式（见 EquipmentSaveDTO 的注释），
 * 新页面一律走白名单 DTO，只开放使用者真正会填的字段。
 *
 * <p>⚠️ **只把「需求人」「物料名称」定为必填** —— 其余全部可空/可空串：
 * 提单号与送货单本来就要后面才有（需求登记时还没提单），要求到货日期也可能先不定。
 * 变更-096 新加的 5 个字段（类别/数量/单位/到货数量/到货日期）**同样全部可空**：
 * 历史数据没有它们，硬设必填会让老记录一改就报错。
 * 这条是从「变更-085 的教训」学来的：当时后端把两条**库里 92 行全为空**的列当必填，
 * 结果每一次保存都被拒、界面上还看不出来。新增的必填项必须先问一句
 * 「这条记录真的可能没有它吗」，答案是"可能"就不要设为必填。
 */
@Data
public class PurchaseTrackingSaveDTO {

    /** 修改时必传；新增时留空 */
    private Long id;

    /** 需求人（必填，≤32） */
    @Size(max = 32, message = "需求人最多 32 个字")
    private String requester;

    /** 提出需求日期 */
    private LocalDate requestDate;

    /** 物料名称（必填，≤128） */
    @Size(max = 128, message = "物料名称最多 128 个字")
    private String materialName;

    /** 物料编码（可空 —— 编码可以后面才定，≤32） */
    @Size(max = 32, message = "物料编码最多 32 个字")
    private String materialCode;

    /** 规格型号（≤255） */
    @Size(max = 255, message = "规格型号最多 255 个字")
    private String spec;

    /** 类别（备件/五金/原料/设备/其他；变更-096 起，可空） */
    @Size(max = 16, message = "类别最多 16 个字")
    private String category;

    /** 需求数量（变更-096 起，可空；填了就必须 > 0，见 ServiceImpl 的校验） */
    private BigDecimal quantity;

    /** 单位（个/台/套/…；变更-096 起，可空，≤16） */
    @Size(max = 16, message = "单位最多 16 个字")
    private String unit;

    /** 要求到货日期（超期/临期的判定基准） */
    private LocalDate requiredArrivalDate;

    /** OA 单号（还没提单时留空，≤64）。变更-096 起语义明确：这是 OA 提单号，不是采购订单号 */
    @Size(max = 64, message = "OA 单号最多 64 个字")
    private String oaNo;

    /** 送货单图片路径（还没到货时留空，≤64） */
    @Size(max = 64, message = "送货单最多 64 个字")
    private String deliveryNote;

    /** 实际到货数量（变更-096 起，可空；不能大于 quantity，见 ServiceImpl 的校验） */
    private BigDecimal receivedQuantity;

    /** 实际到货日期（变更-096 起，可空） */
    private LocalDate actualArrivalDate;

    /** 备注（≤255） */
    @Size(max = 255, message = "备注最多 255 个字")
    private String remark;
}
