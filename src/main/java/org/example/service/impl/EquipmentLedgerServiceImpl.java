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

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 设备台账检索实现。
 *
 * <p>过滤、截断放在 SQL 侧：本表虽然只有几百行，但接口是给「边输边查」用的，
 * 没必要每次都把整表搬进内存（与 {@code /api/material/search} 不同 —— 那边是把全表取回后
 * 按型号分档打分排序，规则必须在 Java 里）。**排序是例外**，见 {@link #NAME_ORDER}。
 */
@Service
public class EquipmentLedgerServiceImpl extends ServiceImpl<EquipmentLedgerMapper, EquipmentLedger>
        implements EquipmentLedgerService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    /**
     * 名称排序：中文按拼音，也就是用户说的「首字母顺序」。
     *
     * <p>**不能交给库里的排序规则**：utf8mb4 的排序对汉字是按码点走的，不是拼音 ——
     * 拿「反 / 前 / 三」举例，码点序是 三 &lt; 前 &lt; 反，拼音序是 反 &lt; 前 &lt; 三，
     * 人扫列表时看不出规律。口径与 {@code /api/tank-level/locations} 对属地下拉一致。
     */
    private static final Collator NAME_ORDER = Collator.getInstance(Locale.CHINA);

    @Override
    public List<EquipmentLedgerVO> search(String keyword, String workshop, Integer limit) {
        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        LambdaQueryWrapper<EquipmentLedger> wrapper = new LambdaQueryWrapper<EquipmentLedger>()
                .eq(EquipmentLedger::getEnabled, 1);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 括号必须显式包住 or 段：否则 or 会与 enabled 平级，
            // 变成「停用的 或 名称含关键字」，把停用行一起带出来
            wrapper.and(w -> w.like(EquipmentLedger::getEquipmentCode, kw)
                    .or().like(EquipmentLedger::getEquipmentName, kw)
                    .or().like(EquipmentLedger::getSpec, kw)
                    .or().like(EquipmentLedger::getWorkshop, kw));
        }
        // 空关键字不再直接返回空：录入页的下拉要「点开就列出候选」，见接口契约 变更-012
        if (StringUtils.hasText(workshop)) {
            wrapper.eq(EquipmentLedger::getWorkshop, workshop.trim());
        }
        wrapper.orderByAsc(EquipmentLedger::getEquipmentName)
                .orderByAsc(EquipmentLedger::getId)
                // size 已夹到 [1, MAX_LIMIT] 的 int，不存在注入；SQL 侧截断比取回全表再截更省
                .last("LIMIT " + size);

        // SQL 侧先按名称排一遍，是为了让 LIMIT 截断有个确定结果；
        // 真正给人看的顺序在下面按拼音定 —— 两者对汉字的口径不同（码点 vs 拼音）
        List<EquipmentLedgerVO> rows = baseMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        rows.sort(Comparator.comparing(EquipmentLedgerVO::getEquipmentName, NAME_ORDER)
                .thenComparing(EquipmentLedgerVO::getId));
        return rows;
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
