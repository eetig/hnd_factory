package org.example.service;

import org.example.dto.DocSaveOptions;
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

    /**
     * 领料汇总落库（legacy 路径，缺省语义）：跳过「单据号+物料编码+日期+数量」全同的行，其余 upsert。
     *
     * <p>无删除能力 —— 老客户端（不带 {@code mode}）永远走这条路，行为与变更-047 之前逐字一致。
     */
    ImportSaveOutcome savePickSummaries(List<MaterialPickSummary> rows, RowImageProvider images);

    /** 生产入库单落库：规则同上 */
    ImportSaveOutcome saveInbounds(List<ProductionInbound> rows, RowImageProvider images);

    /**
     * 领料汇总落库（document 模式，变更-047）：按<b>行身份</b>做差量 —— 改的更新、删的删除、新的插入。
     *
     * <p>与 legacy 路径的区别不只是「能删」：
     * <ul>
     *   <li>legacy 是逐行的 upsert，每行独立判定，单据行数只会增不会减；
     *   <li>document 模式是<b>整单</b>判定：载入时几行、现在几行、少了谁多了谁，
     *       全部由 {@code DocumentDiffPlanner} 一次算清（并发冲突也在这里暴露）。
     * </ul>
     *
     * @param documentNo 单据号（本单全部行共用；调用方已校验非空）
     * @param rows       本次载荷的全部行（顺序 = 用户看到的行序，seqNo 按此重排）
     * @param options    {@code expectedRowCount} 非空 = 严格模式（校验行数 + 允许删除）；
     *                   {@code allowEmpty} = 载荷为空时允许整单删除
     * @param images     行图片提供者；<b>只在确有插入行、或有更新行缺图时才被调用</b>
     */
    ImportSaveOutcome saveDocumentPick(String documentNo, List<MaterialPickSummary> rows,
                                       DocSaveOptions options, RowImageProvider images);

    /** 生产入库单「载入编辑」落库：规则同 {@link #saveDocumentPick} */
    ImportSaveOutcome saveDocumentInbound(String documentNo, List<ProductionInbound> rows,
                                          DocSaveOptions options, RowImageProvider images);
}
