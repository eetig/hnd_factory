package org.example.dto;

import lombok.Data;

/**
 * 物料主数据查询条件（2026-10-23，变更-159）。
 *
 * <p>默认**不含停用**（{@code includeDisabled=false} 时排除 status=0）：
 * 30,434 行里有 5,390 行是停用的历史物料，默认铺出来只会把正常物料挤下去；
 * 用户要查停用物料时显式勾选，或直接按编码搜（编码搜索也遵循这个开关）。
 */
@Data
public class MaterialQueryDTO {
    /** 关键词：按归一化名称包含 或 编码前缀匹配；空 = 不过滤 */
    private String keyword;
    /** 物料类型 Z001–Z010；空 = 全部 */
    private String type;
    /** 状态：1 正常 / 2 待停用 / 0 停用；null = 由 includeDisabled 决定 */
    private Integer status;
    /** 是否包含停用（status=0）。默认 false */
    private Boolean includeDisabled;
    /** 是否只看有价（price > 0） */
    private Boolean hasPrice;
    private Integer pageNum;
    private Integer pageSize;

    public int resolvedPageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int resolvedPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 50;
        }
        return Math.min(pageSize, 200);
    }
}
