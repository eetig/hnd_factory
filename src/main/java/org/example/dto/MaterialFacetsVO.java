package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 物料主数据页的筛选计数（类型 10 项 + 状态 3 项 + 总数）。
 *
 * <p>为什么单独一个接口：这些数是 {@code GROUP BY} 出来的，跟着关键词/类型筛选变会让
 * 每次搜索多两次聚合；而它们只用于"左侧导航显示数量"，变的是**全库分布**，
 * 与当前关键词无关。所以一次取回、前端缓存即可。
 */
@Data
public class MaterialFacetsVO {
    private long total;                  // 物料数（按编码，不含评估类型行）
    private List<CountItem> types;       // 每个 Z00x 的物料数
    private List<CountItem> statuses;    // 正常/待停用/停用 的物料数

    @Data
    public static class CountItem {
        private String code;    // Z001 / 1 / 2 / 0
        private String label;   // 原材料 / 正常 / …
        private String color;   // 徽标色（类型有，状态没有）
        private long count;

        public CountItem() {
        }

        public CountItem(String code, String label, String color, long count) {
            this.code = code;
            this.label = label;
            this.color = color;
            this.count = count;
        }
    }
}
