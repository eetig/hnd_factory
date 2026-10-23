package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 物料详情：主档信息 + 评估类型价格表。
 *
 * <p>罐类物料（6 个编码）会有 7 行评估类型价；其余物料 {@code valuations} 为空数组。
 */
@Data
public class MaterialDetailVO {
    private MaterialVO material;
    private List<MaterialValuationVO> valuations;
}
