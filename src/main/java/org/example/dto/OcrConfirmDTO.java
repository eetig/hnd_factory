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

    /**
     * 日期，yyyy-MM-dd（myocr 已归一，与 ImportUtil 的日期格式列表对得上）。
     *
     * <p>变更-047 起是<b>行级日期的回退值</b>：某行不带 {@code rows[].date} 时用它。
     * 老客户端只发这个（每行都回退到它），行为与改造前逐字一致。
     */
    private String date;

    /**
     * 保存模式（变更-047）。仅 {@code "document"} 有效：
     * 启用「载入编辑」的整单差量落库（可删行、可校验并发）。
     *
     * <p>缺省/空 = legacy 逐行 upsert 路径，行为与变更-047 之前完全一致 ——
     * 老 APK 不带这个字段，必须走老路。
     */
    private String mode;

    /**
     * 载入编辑时客户端看到的库中行数；<b>仅 document 模式</b>，且只在「保存已存在单据」时传。
     *
     * <p>传了 = 严格模式：服务端核对库中行数是否仍等于它，并且允许删除库中多余的行；
     * 不传 = append 模式：只增 / 改 / 跳，<b>永不删除</b>（新建单据走的正是这条）。
     */
    private Integer expectedRowCount;

    /**
     * 载荷为空（rows=[]）时是否允许整单删除；仅 document 模式有意义。
     *
     * <p>三重闸门的中间一环（前端弹窗 → 这个标志 → 严格行数校验）：
     * 用户把行删光了、前端也确认过了，才会带 {@code true} 上来。
     */
    private Boolean allowEmpty;

    private List<Row> rows;

    @Data
    public static class Row {
        private Integer seqNo;
        private String materialName;
        private String materialCode;
        /** 数量：按文本收，交给 ImportUtil 解析（可容忍千分位、空白等） */
        private String qty;
        private String unit;

        /**
         * 行主键（变更-047）：「载入编辑」时由 detail 接口下发、保存时原样带回，
         * 服务端据此认定「还是那一行」并保留行身份（按行差量）。
         *
         * <p>新行不传；legacy 模式忽略。
         */
        private Long id;

        /**
         * 行日期 yyyy-MM-dd（变更-047）：日期是行身份的一部分（D4），
         * 缺省回退到 {@link OcrConfirmDTO#date}。
         */
        private String date;
    }
}
