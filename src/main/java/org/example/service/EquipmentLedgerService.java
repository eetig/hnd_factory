package org.example.service;

import org.example.dto.EquipmentLedgerVO;

import java.util.List;

/**
 * 设备台账查询（只读，无写入入口）。
 *
 * <p>台账一次性导入后手工维护（见 docs/schema.sql 第 2 部分），本期不做管理接口；
 * 需要录入/修改时走 SQL，或另开变更补带 @SaCheck* 的写接口。
 */
public interface EquipmentLedgerService {

    /**
     * 关键字模糊检索设备（备用查询：如输入「三甲」列出三甲相关设备及其参数）。
     *
     * <p>匹配范围：设备位号 / 设备名称 / 设备规格 / 车间。四个字段任一含关键字即命中，
     * 与 {@code /api/material/search} 的「包含式匹配」口径一致 —— 这里的用途是「人找设备」，
     * 候选列表交给人看，不存在 material_master 那种「自动填码」的错配风险。
     *
     * @param keyword  关键字；空白时返回空列表（不做「返回全表」）
     * @param workshop 车间/装置精确匹配；空白表示不限
     * @param limit    返回条数上限；null 或非正数取默认值，超过上限按上限截断
     */
    List<EquipmentLedgerVO> search(String keyword, String workshop, Integer limit);
}
