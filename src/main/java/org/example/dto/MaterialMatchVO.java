package org.example.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 物料名称匹配结果。
 *
 * <p>把「自动填」与「给人挑」明确分开：
 * <ul>
 *   <li>{@link #matched} 仅在<b>归一化后全等且唯一命中</b>时非空；
 *       0 条或<b>多条</b>一律为 null —— 多条说明该名称确实对应多个物料，必须交人工判断</li>
 *   <li>{@link #candidates} 只供人工选择，<b>不参与自动填充</b></li>
 * </ul>
 */
@Data
public class MaterialMatchVO {

    /** 唯一严格命中；未命中或多条命中均为 null */
    private MaterialVO matched;

    /** 供人工选择的候选，按相关度排序 */
    private List<MaterialVO> candidates = new ArrayList<>();
}
