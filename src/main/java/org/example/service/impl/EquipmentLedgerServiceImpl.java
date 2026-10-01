package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.EquipmentLedgerVO;
import org.example.entity.EquipmentLedger;
import org.example.mapper.EquipmentLedgerMapper;
import org.example.service.EquipmentLedgerService;
import org.example.util.MassPerMmCalculator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备台账检索实现。
 *
 * <p>过滤、排序、截断都放在 SQL 侧：本表虽然只有几百行，但接口是给「边输边查」用的，
 * 没必要每次都把整表搬进内存（与 {@code /api/material/search} 不同 —— 那边是把全表取回后
 * 按型号分档打分排序，规则必须在 Java 里）。
 */
@Service
public class EquipmentLedgerServiceImpl extends ServiceImpl<EquipmentLedgerMapper, EquipmentLedger>
        implements EquipmentLedgerService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    @Override
    public List<EquipmentLedgerVO> search(String keyword, String workshop, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            // 空关键字返回空列表：本接口的用途是「按名字/位号找设备」，
            // 返回全表既没意义，也会把几百行灌给调用方。
            return List.of();
        }

        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String kw = keyword.trim();

        LambdaQueryWrapper<EquipmentLedger> wrapper = new LambdaQueryWrapper<EquipmentLedger>()
                .eq(EquipmentLedger::getEnabled, 1)
                // 括号必须显式包住 or 段：否则 or 会与 enabled 平级，
                // 变成「停用的 或 名称含关键字」，把停用行一起带出来
                .and(w -> w.like(EquipmentLedger::getEquipmentCode, kw)
                        .or().like(EquipmentLedger::getEquipmentName, kw)
                        .or().like(EquipmentLedger::getSpec, kw)
                        .or().like(EquipmentLedger::getWorkshop, kw));
        if (StringUtils.hasText(workshop)) {
            wrapper.eq(EquipmentLedger::getWorkshop, workshop.trim());
        }
        wrapper.orderByAsc(EquipmentLedger::getWorkshop)
                .orderByAsc(EquipmentLedger::getEquipmentCode)
                .orderByAsc(EquipmentLedger::getEquipmentName)
                .orderByAsc(EquipmentLedger::getId)
                // size 已夹到 [1, MAX_LIMIT] 的 int，不存在注入；SQL 侧截断比取回全表再截更省
                .last("LIMIT " + size);

        return baseMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    private EquipmentLedgerVO toVO(EquipmentLedger ledger) {
        EquipmentLedgerVO vo = new EquipmentLedgerVO();
        vo.setId(ledger.getId());
        vo.setEquipmentCode(ledger.getEquipmentCode());
        vo.setEquipmentName(ledger.getEquipmentName());
        vo.setWorkshop(ledger.getWorkshop());
        vo.setSpec(ledger.getSpec());
        vo.setContainerType(ledger.getContainerType());
        vo.setThickness(ledger.getThickness());
        vo.setVolume(ledger.getVolume());
        vo.setHeatArea(ledger.getHeatArea());
        vo.setHeadVolume(ledger.getHeadVolume());
        vo.setVolumePerMm(ledger.getVolumePerMm());
        vo.setDensity(ledger.getDensity());
        vo.setMedium(ledger.getMedium());
        vo.setDesignTemp(ledger.getDesignTemp());
        vo.setDesignPressure(ledger.getDesignPressure());
        vo.setRemark(ledger.getRemark());
        // 质量优先用库里已核定的值；库里没填、但体积与密度都有时现算，避免同一列两种口径
        vo.setMassPerMm(ledger.getMassPerMm() != null
                ? ledger.getMassPerMm()
                : MassPerMmCalculator.massPerMm(ledger.getVolumePerMm(), ledger.getDensity()));
        return vo;
    }
}
