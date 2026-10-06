package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.EquipmentLedgerVO;
import org.example.dto.EquipmentSaveDTO;
import org.example.dto.VesselVO;
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

    @Override
    public List<VesselVO> listVessels() {
        // 只取「几何与底图填齐」的行：台账 91 行里绝大多数没有这些参数，
        // 它们不会出现在这里 —— 这是预期（照图纸核过几何的容器才该参与精算）。
        // 注意没有 LIMIT：本列表就是该页下拉的全部候选，当前只有个位数，将来也不会多。
        LambdaQueryWrapper<EquipmentLedger> wrapper = new LambdaQueryWrapper<EquipmentLedger>()
                .eq(EquipmentLedger::getEnabled, 1)
                .isNotNull(EquipmentLedger::getInnerDiameter)
                .isNotNull(EquipmentLedger::getShellLength)
                // 上封头曲面深可以为 0（平顶），但必须有值 —— 它是分段的依据
                .isNotNull(EquipmentLedger::getTopHeadDepth)
                .isNotNull(EquipmentLedger::getImageFile)
                // 空串也算没填；`<> ''` 对 NULL 求值为 NULL，顺带把 NULL 也排掉了
                .ne(EquipmentLedger::getImageFile, "")
                // image_bounds 是 JSON 列，空串不是合法 JSON，所以 isNotNull 就够
                .isNotNull(EquipmentLedger::getImageBounds);

        List<VesselVO> rows = baseMapper.selectList(wrapper).stream()
                .map(this::toVesselVO)
                .collect(Collectors.toList());
        // 与 search 同一个拼音序：两处下拉（容器名称输入框 / 体积计算页）的顺序保持一致
        rows.sort(Comparator.comparing(VesselVO::getName, NAME_ORDER)
                .thenComparing(VesselVO::getId));
        return rows;
    }

    // ===== 以下三个服务「设备数据维护」页 =====

    /**
     * 维护页新增行时用的来源工作表名。
     *
     * <p>与源台账《容器封头参数表2025.06》**分开**，这样 `uk_source_row` 这个唯一键在两边
     * 各自递增、互不撞车；将来重新导入源台账也不会与手工新增的行冲突。
     */
    private static final String MANUAL_SOURCE_SHEET = "manual";

    @Override
    public List<EquipmentLedgerVO> listAll() {
        // ⚠️ 与 search / listVessels 的口径不同：**停用的行也要返回** ——
        // 那两个是「给使用者挑东西」（停用的不该出现），这里是「维护」（要能重新启用）
        List<EquipmentLedgerVO> rows = baseMapper.selectList(null).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        rows.sort(Comparator.comparing(EquipmentLedgerVO::getEquipmentName, NAME_ORDER)
                .thenComparing(EquipmentLedgerVO::getId));
        return rows;
    }

    @Override
    public Long save(EquipmentSaveDTO payload) {
        EquipmentLedger row = new EquipmentLedger();
        row.setId(payload.getId());
        applyEditableFields(row, payload);

        if (payload.getId() == null) {
            row.setEnabled(1);
            row.setSourceSheet(MANUAL_SOURCE_SHEET);
            row.setSourceRow(nextManualSourceRow());
            baseMapper.insert(row);
        } else {
            // 用 updateById 就够 —— 可编辑字段在实体上标了
            // `@TableField(updateStrategy = ALWAYS)`，所以「清空某列」（如把下封头深度清成 NULL
            // 表示平底）也写得进去。默认策略只写非 null 字段，那种操作会静默不生效。
            baseMapper.updateById(row);
        }
        return row.getId();
    }

    /**
     * 把可维护的字段从 DTO 搬到实体（**字段清单只在这一处**）。
     *
     * <p>逐字段列出来是刻意的：用 BeanUtils 之类整体拷贝会连 DTO 将来新增的字段一起带过去，
     * 白名单就形同虚设。
     */
    private void applyEditableFields(EquipmentLedger row, EquipmentSaveDTO p) {
        row.setEquipmentCode(p.getEquipmentCode());
        row.setEquipmentName(p.getEquipmentName());
        row.setNickname(p.getNickname());
        row.setWorkshop(p.getWorkshop());
        row.setSpec(p.getSpec());
        row.setContainerType(p.getContainerType());
        row.setInnerDiameter(p.getInnerDiameter());
        row.setShellLength(p.getShellLength());
        row.setStraightFlange(p.getStraightFlange());
        row.setTopHeadDepth(p.getTopHeadDepth());
        row.setBottomHeadDepth(p.getBottomHeadDepth());
        row.setVolumePerMm(p.getVolumePerMm());
        row.setDensity(p.getDensity());
        row.setMedium(p.getMedium());
        row.setRemark(p.getRemark());
        row.setImageFile(p.getImageFile());
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        EquipmentLedger row = new EquipmentLedger();
        row.setId(id);
        row.setEnabled(enabled ? 1 : 0);
        baseMapper.updateById(row);
    }

    /** 新增行的行号 = 该工作表当前最大值 + 1（唯一键的一半，不能重复） */
    private int nextManualSourceRow() {
        List<Object> max = baseMapper.selectObjs(new QueryWrapper<EquipmentLedger>()
                .select("IFNULL(MAX(source_row), 0)")
                .eq("source_sheet", MANUAL_SOURCE_SHEET));
        Object value = max.isEmpty() ? null : max.get(0);
        return (value == null ? 0 : ((Number) value).intValue()) + 1;
    }

    private VesselVO toVesselVO(EquipmentLedger ledger) {
        VesselVO vo = new VesselVO();
        vo.setId(ledger.getId());
        vo.setName(ledger.getEquipmentName());
        vo.setNickname(ledger.getNickname());
        vo.setCode(ledger.getEquipmentCode());
        vo.setWorkshop(ledger.getWorkshop());
        vo.setContainerType(ledger.getContainerType());
        vo.setDiameter(ledger.getInnerDiameter());
        vo.setShellLength(ledger.getShellLength());
        vo.setStraightFlange(ledger.getStraightFlange());
        vo.setTopHeadDepth(ledger.getTopHeadDepth());
        vo.setBottomHeadDepth(ledger.getBottomHeadDepth());
        vo.setImageFile(ledger.getImageFile());
        vo.setImageBounds(ledger.getImageBounds());
        vo.setMedium(ledger.getMedium());
        vo.setDensity(ledger.getDensity());
        // 只作前端的对账基准（比对按几何算出的封头容积），不参与该页的体积计算
        vo.setHeadVolume(ledger.getHeadVolume());
        // 同理，只作对照值展示（再沸器的铭牌全容积就放这一列）
        vo.setVolume(ledger.getVolume());
        return vo;
    }

    private EquipmentLedgerVO toVO(EquipmentLedger ledger) {
        EquipmentLedgerVO vo = new EquipmentLedgerVO();
        vo.setId(ledger.getId());
        vo.setEquipmentCode(ledger.getEquipmentCode());
        vo.setEquipmentName(ledger.getEquipmentName());
        vo.setNickname(ledger.getNickname());
        vo.setWorkshop(ledger.getWorkshop());
        vo.setSpec(ledger.getSpec());
        vo.setContainerType(ledger.getContainerType());
        vo.setInnerDiameter(ledger.getInnerDiameter());
        vo.setShellLength(ledger.getShellLength());
        vo.setStraightFlange(ledger.getStraightFlange());
        vo.setTopHeadDepth(ledger.getTopHeadDepth());
        vo.setBottomHeadDepth(ledger.getBottomHeadDepth());
        vo.setImageFile(ledger.getImageFile());
        vo.setImageBounds(ledger.getImageBounds());
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
        vo.setEnabled(ledger.getEnabled());
        // 质量优先用库里已核定的值；库里没填、但体积与密度都有时现算，避免同一列两种口径
        vo.setMassPerMm(ledger.getMassPerMm() != null
                ? ledger.getMassPerMm()
                : MassPerMmCalculator.massPerMm(ledger.getVolumePerMm(), ledger.getDensity()));
        return vo;
    }
}
