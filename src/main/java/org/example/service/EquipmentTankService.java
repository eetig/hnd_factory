package org.example.service;

import org.example.dto.EquipmentTankSaveDTO;
import org.example.vo.EquipmentTankVO;

import java.util.List;

/**
 * 储罐（容器实例）服务（2026-11-09）。
 *
 * <p>职责边界：只管"罐 → 图纸"的绑定与罐自身的属性；
 * 液位换算、质量计算属于后面的步骤，不在这一层先铺。
 */
public interface EquipmentTankService {

    /**
     * 列表。
     *
     * @param keyword  模糊匹配罐号/罐名/属地/物料名称（可空）
     * @param enabled  1 / 0 / null（不过滤）
     * @param workshop 车间（可空）
     */
    List<EquipmentTankVO> list(String keyword, Integer enabled, String workshop);

    /** 新增或更新；返回主键 */
    Long save(EquipmentTankSaveDTO dto);

    /** 启用/停用 */
    void setEnabled(Long id, boolean enabled);

    /** 删除（仅限还没有液位记录的罐；有记录的只能停用） */
    void delete(Long id);
}
