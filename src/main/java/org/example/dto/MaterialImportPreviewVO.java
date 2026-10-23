package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 物料导入预览（变更-159）。
 *
 * <p>两阶段导入的第一阶段：只解析 + 比对、不落库。前端拿 {@code token} 去调 commit，
 * 人可以在确认前先看清「新增多少 / 变更多少 / 消失多少」。
 */
@Data
public class MaterialImportPreviewVO {
    private String token;
    private String fileName;
    /** 解析出的有效行数（不含表头与空行） */
    private int totalRows;
    private int newCount;
    private int changedCount;
    /** 文件里没有、库里有的键：导入时转停用（不删除） */
    private int removedCount;
    private int unchangedCount;
    /** 识别到的列名（按文件顺序），用于「列对不上」时给人看 */
    private List<String> headers;
    /** 缺列等非致命问题：不阻断，但要在页面上说出来 */
    private List<String> warnings;
    /** 每类给前 20 行样例，够人确认口径即可；完整数据留在服务端缓存里 */
    private List<Sample> newSamples;
    private List<Sample> changedSamples;
    private List<Sample> removedSamples;

    @Data
    public static class Sample {
        private String code;
        private String valuationType;
        private String name;
        /** 变更行才有的「旧值 → 新值」摘要 */
        private String diff;
    }
}
