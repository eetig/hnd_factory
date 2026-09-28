package org.example.service;

import org.example.dto.ImportSaveOutcome;
import org.example.entity.MaterialPickSummary;
import org.example.entity.ProductionInbound;

import java.util.List;

/**
 * 单据落库 —— <b>Excel 导入与图片识别共用同一套去重与写入规则</b>。
 *
 * <p>抽出来的理由（见《前后端改动统筹 · 变更-003》§2）：
 * 「图片识别只是多了一个数据来源，不是多了一套业务逻辑」。
 * 若让两条路各写一份落库判定，迟早出现「Excel 导入一套规则、图片识别另一套」的分裂 ——
 * 同一份数据从两个入口进来得到不同结果，是最难排查的一类问题。
 *
 * <p>本服务只做「去重判定 + 图片绑定 + 调 saveImported」这段编排，
 * 校验（必填字段、数量可解析）由调用方在更早的阶段完成（见各 {@code XxxImportUtil.validate}）。
 */
public interface DocumentImportSaveService {

    /**
     * 行图片提供者。
     *
     * <p><b>只在「确定要写入的行」上被调用</b> —— 被跳过的重复行不该为它上传图片。
     * 这是变更-002 的关键优化：全部行都重复时零图片上传，连 xlsx 都不必解压。
     * 若在过滤前就把图片物化出来，这个优化会被悄悄破坏。
     */
    @FunctionalInterface
    interface RowImageProvider {

        /**
         * @param originalIndex 该行在传入列表中的<b>原始下标</b>（不是过滤后的下标）
         * @return 该行要绑定的 fileName；无图为 null
         */
        String fileNameFor(int originalIndex);
    }

    /** 领料汇总落库：跳过「单据号+物料编码+数量」三者全同的行，其余 upsert */
    ImportSaveOutcome savePickSummaries(List<MaterialPickSummary> rows, RowImageProvider images);

    /** 生产入库单落库：规则同上 */
    ImportSaveOutcome saveInbounds(List<ProductionInbound> rows, RowImageProvider images);
}
