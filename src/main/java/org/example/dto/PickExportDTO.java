package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 领料解析导出请求（一次导出 = 一张或多张领料单）。
 *
 * <h2>为什么按「单」分组而不是拍平成一堆行</h2>
 * 使用方原话：「一单 1–8 行，每单分开」、「单与单之间空一行」。所以前端核对完要保留单据边界，
 * 后端按 {@code orders} 逐单写、单与单之间留空行；跨部门时按 {@code dept} 分 sheet。
 *
 * <p>{@code dept} 允许每单不同（一次可能拍了好几张不同部门的单），后端按首次出现顺序建 sheet。
 */
@Data
public class PickExportDTO {

    /** 模板 id（部门→候选模板里选一张） */
    private Long templateId;

    private List<Order> orders;

    @Data
    public static class Order {

        /** OA/纸质单号，仅用于排查，不写进 Excel（SAP 侧不需要） */
        private String docNo;

        /** 领料部门 —— 决定落哪个 sheet；空则用模板的 dept，再空则用「领料」 */
        private String dept;

        private String requester;

        private List<Item> items;
    }

    @Data
    public static class Item {

        @NotBlank
        private String materialCode;

        private String materialName;

        /** 数量，写进 SAP 的「单据数量」列 */
        private Double quantity;

        /** 单位；模板有单位列且这里给了才写，否则留空由 SAP 带出 */
        private String unit;
    }
}
