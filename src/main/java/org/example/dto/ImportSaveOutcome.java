package org.example.dto;

/**
 * 单据落库结果。
 *
 * <p>比 {@link WorkOrderImportSaveVO} 多一个 {@code skipCount}：跳过是「单据号+物料编码+数量」
 * 三者全同的行，既不新增也不更新，也不为它上传图片（变更-002/003 的去重语义）。
 */
public record ImportSaveOutcome(int insertCount, int updateCount, int skipCount) {
}
