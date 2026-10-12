package org.example.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单据的一行明细（变更-047）：
 * {@code GET /api/work-order/document/detail} 的返回行，
 * 同时充当 document 模式落库时「库中行」的载体（差量比对的数据源）。
 */
@Data
public class DocumentRowVO {

    /** 行主键：「载入编辑」时必须随 payload 带回，服务端据此保行身份（按行差量） */
    private Long id;

    private Integer seqNo;
    private String materialName;
    private String materialCode;
    /** 数量：领料数量 / 入库数量，按单据类型落到同一字段 */
    private BigDecimal qty;
    private String unit;
    private LocalDate date;

    /** 原图：相对路径（如 /files/xxx.jpg）；无图片为 null */
    private String imageUrl;

    /** 缩略图：相对路径（如 /thumbs/xxx.jpg）；无图片为 null */
    private String thumbnailUrl;

    /**
     * 图片文件名原值：**仅供服务端**做「更新行是否要补图」的判定（为空才补、绝不覆写），
     * 不进接口返回 —— 对外只暴露 imageUrl / thumbnailUrl 两种形态。
     */
    @JsonIgnore
    private String fileName;
}
