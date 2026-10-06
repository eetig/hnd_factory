package org.example.service;

import org.example.dto.EquipmentLedgerVO;
import org.example.dto.EquipmentSaveDTO;
import org.example.dto.VesselVO;

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

    /**
     * 「压力容器体积计算」页的容器列表（变更-025）。
     *
     * <p>筛选口径：{@code enabled = 1} **且几何与底图填齐**
     * （内径 / 筒体直段长 / 上封头曲面深 / 底图文件名 / 底图坐标 都非空）。
     * 台账里绝大多数行没有这些参数 —— 它们**不会**出现在本列表里，这是预期，
     * 不是漏了：只有照图纸核过几何的容器才该参与精算。
     *
     * <p>排序沿用 {@link #search} 的名称拼音序（同一个 Collator），两处下拉的顺序一致。
     */
    List<VesselVO> listVessels();

    // ===== 以下三个服务「设备数据维护」页（2026-10-06 起本表不再只读）=====

    /**
     * 台账全表，供维护页列表用。
     *
     * <p>不分页：源台账 91 行、将来也就几百行；维护页要「逐个设备对比确认」，
     * 分页反而碍事（翻页时看不全同一车间的同类设备）。排序同 {@link #search} 的拼音序。
     * 停用的行**也要返回**（维护页要能重新启用）。
     */
    List<EquipmentLedgerVO> listAll();

    /**
     * 保存一行：{@code id} 为空则新增，非空则更新。
     *
     * <p>可写字段由 {@link org.example.dto.EquipmentSaveDTO} 的字段集合界定（白名单即类型本身）。
     *
     * <p>新增时自动处理 {@code uk_source_row} 唯一键：{@code source_sheet} 记 {@code "manual"}、
     * {@code source_row} 取该工作表当前最大值 +1 —— 这条规矩 `docs/schema.sql` 里已经写明，
     * 不这么做的话同一次导入或第二次新增就会撞唯一键。
     *
     * @return 该行的 id（新增时是自增出来的新 id）
     */
    Long save(EquipmentSaveDTO payload);

    /**
     * 停用 / 启用一行。
     *
     * <p>**不提供删除**：台账是历史依据，删了就查不到「某一行是怎么来的」。停用只是不参与检索。
     */
    void setEnabled(Long id, boolean enabled);
}
