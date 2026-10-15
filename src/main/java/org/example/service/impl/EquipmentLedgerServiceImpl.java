package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.EquipmentLedgerQueryDTO;
import org.example.dto.EquipmentLedgerVO;
import org.example.dto.EquipmentSaveDTO;
import org.example.dto.VesselVO;
import org.example.entity.EquipmentLedger;
import org.example.exception.BusinessException;
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
     * 人扫列表里看不出规律。口径与 {@code /api/tank-level/locations} 对属地下拉一致。
     *
     * <p>⚠️ 只有 {@link #search} / {@link #listVessels} 两个「取候选」的接口能用它 ——
     * 它们的数据量小（几十条）、且不分页。维护页的 {@link #listPage} **不能**用：
     * 那边分页在 SQL 层做，排序也必须在 SQL 层，所以改用
     * {@code CONVERT(equipment_name USING gbk)} 取近似拼音序（见该方法的说明）。
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
    public IPage<EquipmentLedgerVO> listPage(EquipmentLedgerQueryDTO query) {
        QueryWrapper<EquipmentLedger> wrapper = new QueryWrapper<>();
        // ⚠️ 与 search / listVessels 的默认口径不同：这里**停用的行默认也要返回** ——
        // 那两个是「给使用者挑东西」（停用的不该出现），这里是「维护」（要能重新启用）。
        // 只有显式传 onlyEnabled=true 才过滤掉停用的行。
        if (Boolean.TRUE.equals(query.getOnlyEnabled())) {
            wrapper.eq("enabled", 1);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            // 五个字段任一含关键字即命中。括号必须显式包住 or 段 ——
            // 不包的话 or 会与上面的 enabled 条件平级，
            // 变成「只看启用的 或 名字含关键字」，把停用的行一起带出来
            wrapper.and(w -> w.like("equipment_code", kw)
                    .or().like("equipment_name", kw)
                    .or().like("nickname", kw)
                    .or().like("spec", kw)
                    .or().like("workshop", kw));
        }
        // 设备名称的**近似拼音序**：GBK 码位序就是中文按拼音排的经典做法，
        // 用来替代原来那个 Java Collator（分页在 SQL 层做之后，排序也必须落到 SQL 里，
        // 只在内存里排会让每页的顺序互不一致、甚至漏行重行）。
        // 与 Collator 的结果在个别字上可能有出入，但比 utf8mb4 默认的码位序（按 Unicode
        // 编码排，中文出来毫无规律）强得多。id 兜底保证顺序唯一确定。
        wrapper.orderByAsc("CONVERT(equipment_name USING gbk)").orderByAsc("id");

        Page<EquipmentLedger> page = new Page<>(query.resolvedPageNum(), query.resolvedPageSize());
        return baseMapper.selectPage(page, wrapper).convert(this::toVO);
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
            // ⚠️ 这条路径是**整行覆写**：调用方必须带齐 {@link #applyEditableFields} 里的全部
            // 可编辑字段；未传的字段会被写成 NULL（可空列 = 静默清空，NOT NULL 列 = 撞约束）。
            //
            // 为什么保留整行语义：实体上那些 `updateStrategy = ALWAYS` 的存在意义就是
            // 「把某列清成 NULL」（如「平底」要把下封头深度清空）。改成"只写非 null 字段"
            // 会让这个已上线的操作**静默失效** —— 那比现在更糟（2026-10-15 code-review
            // 的 Critical 2 复核结论）。所以这里做两件能立刻降低风险的事：
            //   ① 三个 NOT NULL 列在服务端先校验：给业务文案，而不是让 DB 约束异常穿透成 500；
            //   ② 改 0 行时明确报错（原先静默当成功）。
            // 残留风险：区分"没传"与"传了 null"需要改 DTO 契约（JsonNullable / Map），
            // 属接口变更，另行评估。三处字段清单的一致性由单测钉住，见
            // EquipmentLedgerServiceImplTest#editableFieldSetsAgree。
            requireNotNullFields(payload);
            if (baseMapper.updateById(row) == 0) {
                throw new BusinessException("设备不存在或已被删除（id=" + payload.getId() + "）");
            }
        }
        return row.getId();
    }

    /** 库层 NOT NULL 的三列在服务端先校验 —— 报人能看懂的话，而不是抛约束异常 */
    private void requireNotNullFields(EquipmentSaveDTO p) {
        if (!StringUtils.hasText(p.getEquipmentCode())) {
            throw new BusinessException("设备位号不能为空");
        }
        if (!StringUtils.hasText(p.getEquipmentName())) {
            throw new BusinessException("设备名称不能为空");
        }
        if (!StringUtils.hasText(p.getWorkshop())) {
            throw new BusinessException("车间/装置不能为空");
        }
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
        // ⚠️ 不能再用「半实体 + updateById」（变更-061）。
        //
        // 实体上可编辑字段都标了 updateStrategy = ALWAYS（那是为了让「清空某列」能落库，
        // 见 EquipmentLedger 的注释），MyBatis-Plus 于是会把这些列**一并写进 SET**，
        // 半实体里没赋值的就成了 `equipment_code = NULL, equipment_name = NULL, …` ——
        // 撞库的 NOT NULL 约束，整页「停用」全失败。
        //
        // 2026-10-15 实测的事故现场：前端只看到 403「SaTokenContext 上下文尚未初始化」，
        // 真实原因（哪一列不能为空）被 ERROR 派发里的 Sa-Token 上下文异常盖掉了。
        //
        // 停用/启用只该动一列，所以改成定向 UPDATE：SET 段只有 enabled，别的列一概不碰。
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<EquipmentLedger>()
                .eq(EquipmentLedger::getId, id)
                .set(EquipmentLedger::getEnabled, enabled ? 1 : 0));
        // 改 0 行 = 这个 id 不存在（可能刚被别人删了）。原先静默返回成功，
        // 前端看到"停用成功"但列表里那行还在 —— 又一条假信号（变更-065）。
        if (updated == 0) {
            throw new BusinessException("设备不存在或已被删除（id=" + id + "）");
        }
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
