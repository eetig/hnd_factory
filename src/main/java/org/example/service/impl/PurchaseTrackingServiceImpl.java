package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.dto.PurchaseTrackingQueryDTO;
import org.example.dto.PurchaseTrackingSaveDTO;
import org.example.dto.PurchaseTrackingVO;
import org.example.entity.MaterialStock;
import org.example.entity.PurchaseTracking;
import org.example.exception.BusinessException;
import org.example.mapper.MaterialStockMapper;
import org.example.mapper.PurchaseTrackingMapper;
import org.example.service.PurchaseTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购进度跟踪实现。
 *
 * <p>三件事：后端分页查询、保存（新增/修改）、删除，外加**物料登记**（下面单列一节）。
 */
@Service
public class PurchaseTrackingServiceImpl
        extends ServiceImpl<PurchaseTrackingMapper, PurchaseTracking>
        implements PurchaseTrackingService {

    /**
     * 采购登记固定落在这个「工厂 / 库位」。
     *
     * <p>1503 的真实库位是 9001 五金仓 / 1001 原材料仓 / 3001 包材仓 / 9301 安环物资仓 /
     * 2001 乳液成品仓（2026-10-16 实测），0000 不与之冲突 —— 一眼看出是采购登记进来的。
     */
    static final String REGISTER_PLANT = "1503";
    static final String REGISTER_LOCATION = "0000";
    static final String REGISTER_LOCATION_DESC = "采购登记（无库存）";

    /** 采购登记行只读这一张表；写它的是「编码登记」，不是库存 */
    @Autowired
    private MaterialStockMapper materialStockMapper;

    @Override
    public IPage<PurchaseTrackingVO> listPage(PurchaseTrackingQueryDTO query) {
        Page<PurchaseTracking> page = new Page<>(query.resolvedPageNum(), query.resolvedPageSize());
        return baseMapper.selectPage(page, filterOf(query)
                        // ⚠️ 排序键必须以**唯一列** id 收尾。同一天的采购需求可能有好几条，
                        // 只按日期排时 MySQL 不保证两次查询的相对顺序一致 ⇒ 翻页会漏行、也会重复行。
                        // 这个坑入库汇总改后端分页时踩过一次（见 ProductionInboundServiceImpl）。
                        // 注意这里是 LambdaQueryWrapper，orderByDesc 要吃**方法引用**，
                        // 不是 QueryWrapper 那种列名字符串（那处写法照抄会编译不过）。
                        .orderByDesc(PurchaseTracking::getRequestDate)
                        .orderByDesc(PurchaseTracking::getId))
                .convert(this::toVO);
    }

    /** 列表筛选：关键字（模糊，跨展示列）+ 提出需求日期区间 */
    private LambdaQueryWrapper<PurchaseTracking> filterOf(PurchaseTrackingQueryDTO query) {
        LambdaQueryWrapper<PurchaseTracking> wrapper = new LambdaQueryWrapper<>();

        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            // 整组 OR 必须用 and(...) 包起来：否则第一个 or 会把前面的条件（日期区间）
            // 一起短路掉，变成「日期不限」—— 这类括号 bug 在 SQL 里不报错、只是结果不对。
            wrapper.and(w -> w.like(PurchaseTracking::getRequester, keyword)
                    .or().like(PurchaseTracking::getMaterialName, keyword)
                    .or().like(PurchaseTracking::getMaterialCode, keyword)
                    .or().like(PurchaseTracking::getSpec, keyword)
                    .or().like(PurchaseTracking::getPurchaseOrderNo, keyword)
                    .or().like(PurchaseTracking::getDeliveryNote, keyword));
        }

        // 日期区间作用在**提出需求日期**上 —— 页面的日期筛选框标的就是它。
        // 解析失败/为空一律当「不限」（见 DateRangeQueryDTO#parseDate 的注释）。
        LocalDate start = query.parsedStartDate();
        if (start != null) {
            wrapper.ge(PurchaseTracking::getRequestDate, start);
        }
        LocalDate end = query.parsedEndDate();
        if (end != null) {
            wrapper.le(PurchaseTracking::getRequestDate, end);
        }
        return wrapper;
    }

    @Override
    public Long save(PurchaseTrackingSaveDTO dto) {
        // ⚠️ 只有两条硬要求：**需求人**和**物料名称** —— 一条没有"谁要的/要什么"的记录没有意义。
        // 其余一律可空：采购订单号与送货单号本来就要后面才有（需求登记时还没下单），
        // 要求到货日期也可能先不定，物料编码也可能后补（编码由采购/仓库定）。
        // 这条分寸是照「变更-085 的教训」定的：那时后端把两条库里 92 行全为空的列当必填，
        // 结果每一次保存都被拒、界面上还看不出来。加必填之前先问「这条记录真的可能没有它吗」。
        if (!StringUtils.hasText(dto.getRequester())) {
            throw new BusinessException("需求人不能为空");
        }
        if (!StringUtils.hasText(dto.getMaterialName())) {
            throw new BusinessException("物料名称不能为空");
        }

        PurchaseTracking row = new PurchaseTracking();
        row.setRequester(dto.getRequester().trim());
        row.setRequestDate(dto.getRequestDate());
        row.setMaterialName(dto.getMaterialName().trim());
        // 库定义 NOT NULL DEFAULT '' 的两列：null 归一成空串（"还没下单/还没到货"就是空串）
        row.setMaterialCode(blankIfNull(dto.getMaterialCode()));
        row.setPurchaseOrderNo(blankIfNull(dto.getPurchaseOrderNo()));
        row.setDeliveryNote(blankIfNull(dto.getDeliveryNote()));
        // 可空的两列保持 null 语义（清空时真的写 NULL，靠实体的 ALWAYS 策略）
        row.setSpec(trimToNull(dto.getSpec()));
        row.setRemark(trimToNull(dto.getRemark()));
        row.setRequiredArrivalDate(dto.getRequiredArrivalDate());

        if (dto.getId() == null) {
            baseMapper.insert(row);
        } else {
            row.setId(dto.getId());
            if (baseMapper.updateById(row) == 0) {
                // 0 行 = 该 id 不存在。明确报错，不要静默当成功
                // （维护页那条接口原先就是这个毛病，见 ADR 0002）。
                throw new BusinessException("要修改的记录不存在（可能已被别人删除）");
            }
        }

        registerMaterial(row);
        return row.getId();
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException("缺少 id，无法删除");
        }
        if (baseMapper.deleteById(id) == 0) {
            throw new BusinessException("要删除的记录不存在（可能已被别人删除）");
        }
        // ⚠️ 刻意**不**回滚 material_stock 里的登记行：登记的是「这个编码存在过」，
        // 与某一条采购记录在不在无关 —— 与「旧编码即使库存为 0 也保留」同一条口径
        // （使用方 2026-10-16 的要求）。删一条采购记录不该让一个编码从总表里消失。
    }

    // ===== 物料登记（使用方 2026-10-16 的要求）=====

    /**
     * 把采购记录上的物料编码登记进 {@code material_stock}，使总表里能按编码查到它。
     *
     * <p><b>只写 material_stock，不写 material_master</b> —— 后者是
     * <b>SAP 物料主数据</b>（SAP 侧导入 + 手工维护，图片解析按名称反查编码/单位用）。
     * 把采购登记写进去会污染 SAP 主数据、也会干扰那条反查通路。
     * 采购用的编码若本来就是 SAP 编码，它已经在 material_stock 里了，下面的"已存在即跳过"会兜住。
     *
     * <p>落在 {@code 1503 / 0000 / 采购登记（无库存）}、库存记 0，好处有四条：
     * <ol>
     *   <li>不在任何真实仓里（真实库位是 9001/1001/3001/9301/2001），不会和真库存混在一起；</li>
     *   <li>一眼看出是采购登记进来的（库位描述写着），也方便用「库位 = 0000」一次查出全部登记项；</li>
     *   <li>因为 {@code (plant_code, material_code, storage_location)} 本来就是唯一键，
     *       非 SAP 的采购编码在这里<b>天然一码一行</b> —— 使用方要的"查编码能查到、不重复"
     *       由表结构保证，<b>不需要</b>再加什么全局唯一约束
     *       （SAP 源数据里编码本来就不唯一：material_master 246 行 / 241 个编码）；</li>
     *   <li>下一次库存导入会把该工厂所有行的数量清零、<b>保留行</b>
     *       （见 MaterialStockServiceImpl「清零只清数量，保留行」）⇒ 登记行的库存保持 0 但永不删除。</li>
     * </ol>
     *
     * <p>两条保守规则，都是为了不动别人的数据：
     * <ul>
     *   <li><b>该编码在 material_stock 里已存在（任何库位）就跳过</b> —— 它已经能查到了，
     *       不必再插一行 0000；这样 0000 档的含义也就清清楚楚：
     *       「库存表里还没有、由采购登记补进来的编码」。</li>
     *   <li>没有编码就什么都不做 —— 采购记录允许只写名称（编码可能还没定）。</li>
     * </ul>
     */
    private void registerMaterial(PurchaseTracking row) {
        String code = trimToNull(row.getMaterialCode());
        if (code == null) {
            return;
        }

        Long existing = materialStockMapper.selectCount(
                new LambdaQueryWrapper<MaterialStock>().eq(MaterialStock::getMaterialCode, code));
        if (existing != null && existing > 0) {
            return;
        }

        MaterialStock reg = new MaterialStock();
        reg.setPlantCode(REGISTER_PLANT);
        reg.setMaterialCode(code);
        reg.setMaterialDesc(trimToNull(row.getMaterialName()));
        reg.setSpec(trimToNull(row.getSpec()));
        reg.setStorageLocation(REGISTER_LOCATION);
        reg.setStorageDesc(REGISTER_LOCATION_DESC);
        reg.setStockQty(BigDecimal.ZERO);
        // 刻意不设 unit（采购记录里没有单位这一列）与 lastImportTime（它不是导入来的）
        materialStockMapper.insert(reg);
    }

    // ===== 小工具 =====

    private PurchaseTrackingVO toVO(PurchaseTracking row) {
        PurchaseTrackingVO vo = new PurchaseTrackingVO();
        vo.setId(row.getId());
        vo.setRequester(row.getRequester());
        vo.setRequestDate(row.getRequestDate());
        vo.setMaterialName(row.getMaterialName());
        vo.setMaterialCode(row.getMaterialCode());
        vo.setSpec(row.getSpec());
        vo.setRequiredArrivalDate(row.getRequiredArrivalDate());
        vo.setPurchaseOrderNo(row.getPurchaseOrderNo());
        vo.setDeliveryNote(row.getDeliveryNote());
        vo.setRemark(row.getRemark());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    /** 去空白；空/空白串 → null */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 去空白；null → 空串（给库里 NOT NULL DEFAULT '' 的列用） */
    private static String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }
}
