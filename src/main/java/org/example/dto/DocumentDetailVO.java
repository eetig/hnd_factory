package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 单据详情（{@code GET /api/work-order/document/detail}，变更-047）。
 *
 * <p>单据号不存在也按成功返回（{@code exists=false, rows=[]}）——
 * 「查无此单」是正常分支（前端据此判定走「插入」），不是错误。
 */
@Data
public class DocumentDetailVO {

    /** material_pick_summary / production_inbound */
    private String billType;

    private String documentNo;

    private boolean exists;

    private int rowCount;

    /** 单据图（取第一张非空的行图片）；无图为 null */
    private String imageUrl;

    private String thumbnailUrl;

    private List<DocumentRowVO> rows;
}
