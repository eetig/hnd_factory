package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.EquipmentTankSaveDTO;
import org.example.entity.EquipmentLedger;
import org.example.entity.EquipmentTank;
import org.example.exception.BusinessException;
import org.example.mapper.EquipmentLedgerMapper;
import org.example.mapper.EquipmentTankMapper;
import org.example.service.EquipmentTankService;
import org.example.vo.EquipmentTankVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 储罐服务实现（2026-11-09）。
 *
 * <p>两条刻意的规则：
 * <ol>
 *   <li><b>图纸必须存在且启用</b>：罐绑了一张不存在的图纸，后面算体积就是空指针；</li>
 *   <li><b>罐号查重只在同一车间内</b>：现场「1#1L」这种号在不同车间会重复出现，
 *       全厂唯一会把真实数据挡在门外。</li>
 * </ol>
 * 唯一范围等使用方确认后再改成数据库唯一索引；现在是服务层查重，改起来不用动表。
 */
@Service
public class EquipmentTankServiceImpl implements EquipmentTankService {

    @Autowired
    private EquipmentTankMapper tankMapper;

    @Autowired
    private EquipmentLedgerMapper ledgerMapper;

    @Override
    public List<EquipmentTankVO> list(String keyword, Integer enabled, String workshop) {
        LambdaQueryWrapper<EquipmentTank> wrapper = new LambdaQueryWrapper<EquipmentTank>()
                .orderByAsc(EquipmentTank::getWorkshop)
                .orderByAsc(EquipmentTank::getTankCode)
                .orderByAsc(EquipmentTank::getId);
        if (enabled != null) {
            wrapper.eq(EquipmentTank::getEnabled, enabled);
        }
        if (StringUtils.hasText(workshop)) {
            wrapper.eq(EquipmentTank::getWorkshop, workshop.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(EquipmentTank::getTankCode, kw)
                    .or().like(EquipmentTank::getTankName, kw)
                    .or().like(EquipmentTank::getLocation, kw)
                    .or().like(EquipmentTank::getMaterialName, kw));
        }
        List<EquipmentTank> rows = tankMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 图纸一次性取回，避免 N+1（罐多起来以后这条很关键）
        List<Long> blueprintIds = rows.stream().map(EquipmentTank::getBlueprintId).distinct().toList();
        Map<Long, EquipmentLedger> blueprints = ledgerMapper.selectBatchIds(blueprintIds).stream()
                .collect(Collectors.toMap(EquipmentLedger::getId, Function.identity()));

        return rows.stream().map(row -> toVO(row, blueprints.get(row.getBlueprintId()))).toList();
    }

    @Override
    @Transactional
    public Long save(EquipmentTankSaveDTO dto) {
        String code = trim(dto.getTankCode());
        String name = trim(dto.getTankName());
        String workshop = trim(dto.getWorkshop());
        if (code.isEmpty()) {
            throw new BusinessException("请填写罐号");
        }
        if (name.isEmpty()) {
            throw new BusinessException("请填写罐名");
        }
        if (dto.getBlueprintId() == null) {
            throw new BusinessException("请选择图纸");
        }
        EquipmentLedger blueprint = ledgerMapper.selectById(dto.getBlueprintId());
        if (blueprint == null) {
            throw new BusinessException("图纸不存在");
        }

        // 同车间内罐号不许重复（不同车间的 1#1L 是真实存在的）
        LambdaQueryWrapper<EquipmentTank> dup = new LambdaQueryWrapper<EquipmentTank>()
                .eq(EquipmentTank::getTankCode, code)
                .eq(EquipmentTank::getWorkshop, workshop);
        if (dto.getId() != null) {
            dup.ne(EquipmentTank::getId, dto.getId());
        }
        if (tankMapper.selectCount(dup) > 0) {
            throw new BusinessException("同车间下已有罐号「" + code + "」，换个号或改车间");
        }

        EquipmentTank row = dto.getId() == null ? new EquipmentTank() : tankMapper.selectById(dto.getId());
        if (row == null) {
            throw new BusinessException("储罐不存在或已删除");
        }
        row.setTankCode(code);
        row.setTankName(name);
        row.setBlueprintId(dto.getBlueprintId());
        row.setWorkshop(workshop);
        row.setLocation(trim(dto.getLocation()));
        row.setCategory(trim(dto.getCategory()));
        row.setMaterialCode(trim(dto.getMaterialCode()));
        row.setMaterialName(trim(dto.getMaterialName()));
        row.setDensity(dto.getDensity());
        row.setRemark(trim(dto.getRemark()));
        if (dto.getEnabled() != null) {
            row.setEnabled(dto.getEnabled() == 1 ? 1 : 0);
        } else if (dto.getId() == null) {
            row.setEnabled(1);
        }

        if (dto.getId() == null) {
            tankMapper.insert(row);
        } else {
            tankMapper.updateById(row);
        }
        return row.getId();
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        EquipmentTank row = tankMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("储罐不存在或已删除");
        }
        row.setEnabled(enabled ? 1 : 0);
        tankMapper.updateById(row);
    }

    @Override
    public void delete(Long id) {
        EquipmentTank row = tankMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("储罐不存在或已删除");
        }
        tankMapper.deleteById(id);
    }

    private EquipmentTankVO toVO(EquipmentTank row, EquipmentLedger blueprint) {
        EquipmentTankVO vo = new EquipmentTankVO();
        vo.setId(row.getId());
        vo.setTankCode(row.getTankCode());
        vo.setTankName(row.getTankName());
        vo.setWorkshop(row.getWorkshop());
        vo.setLocation(row.getLocation());
        vo.setCategory(row.getCategory());
        vo.setMaterialCode(row.getMaterialCode());
        vo.setMaterialName(row.getMaterialName());
        vo.setDensity(row.getDensity());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        vo.setBlueprintId(row.getBlueprintId());
        if (blueprint != null) {
            vo.setBlueprintName(blueprint.getEquipmentName());
            vo.setBlueprintSpec(blueprint.getSpec());
            vo.setContainerType(blueprint.getContainerType());
            vo.setInnerDiameter(blueprint.getInnerDiameter());
            vo.setShellLength(blueprint.getShellLength());
            vo.setTopHeadDepth(blueprint.getTopHeadDepth());
            vo.setBottomHeadDepth(blueprint.getBottomHeadDepth());
            vo.setStraightFlange(blueprint.getStraightFlange());
            vo.setVolume(blueprint.getVolume());
            // 体积计算的硬前提：内径与筒长都有值
            vo.setGeometryReady(blueprint.getInnerDiameter() != null && blueprint.getShellLength() != null);
        } else {
            vo.setGeometryReady(false);
        }
        return vo;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
