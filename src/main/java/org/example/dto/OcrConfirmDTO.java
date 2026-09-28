package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 图片识别「确认入库」的请求体（multipart 里的 payload 部分，JSON 字符串）。
 *
 * <p>行数据刻意用<b>字符串</b>承载数量与日期，再由 {@code XxxImportUtil} 解析 ——
 * 与 Excel 导入走的是同一套解析与校验，不另写一份。若这里改成 BigDecimal，
 * 两边的容错能力（千分位、空白、多种日期写法）就会分叉。
 */
@Data
public class OcrConfirmDTO {

    /**
     * 单据大类。取值与文件导入一致（不另造枚举）：
     * {@code material_pick_summary} 领料汇总 / {@code production_inbound} 生产入库单。
     */
    private String billType;

    /** 单据号（业务唯一键） */
    private String documentNo;

    /** 日期，yyyy-MM-dd（myocr 已归一，与 ImportUtil 的日期格式列表对得上） */
    private String date;

    private List<Row> rows;

    @Data
    public static class Row {
        private Integer seqNo;
        private String materialName;
        private String materialCode;
        /** 数量：按文本收，交给 ImportUtil 解析（可容忍千分位、空白等） */
        private String qty;
        private String unit;
    }
}
