package org.example.dto;

/**
 * 单据落库结果。
 *
 * <p>比 {@link WorkOrderImportSaveVO} 多 {@code skipCount} 与 {@code deleteCount}：
 * 跳过是「单据号+物料编码+日期+数量」全同的行，既不新增也不更新，也不为它上传图片
 * （变更-002/003 的去重语义，变更-047 起键加入日期）；删除只在 document 模式 +
 * 严格行数校验下才可能非零（变更-047），legacy 路径恒为 0。
 */
public record ImportSaveOutcome(int insertCount, int updateCount, int skipCount, int deleteCount) {

    /** legacy（无删除能力）路径的兼容构造：删除数恒为 0 */
    public ImportSaveOutcome(int insertCount, int updateCount, int skipCount) {
        this(insertCount, updateCount, skipCount, 0);
    }
}
