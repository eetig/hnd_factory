package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.example.component.ImageUrlBuilder;
import org.example.constant.WorkOrderTypeEnum;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.ExcelResult;
import org.example.dto.WorkOrderGroupVO;
import org.example.dto.WorkOrderDetailVO;
import org.example.dto.WorkOrderExcelDTO;
import org.example.dto.WorkOrderFilterOptionsVO;
import org.example.dto.WorkOrderImageVO;
import org.example.dto.WorkOrderImportResultVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.dto.WorkOrderListVO;
import org.example.dto.WorkOrderPreviewRowVO;
import org.example.dto.WorkOrderPreviewVO;
import org.example.dto.WorkOrderQueryDTO;
import org.example.dto.WorkOrderSaveDTO;
import org.example.entity.WorkOrder;
import org.example.entity.WorkOrderImage;
import org.example.exception.BusinessException;
import org.example.feign.ExcelParseFeign;
import org.example.mapper.WorkOrderImageMapper;
import org.example.mapper.WorkOrderMapper;
import org.example.service.WorkOrderService;
import org.example.util.WorkOrderImportUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService {

    @Autowired
    private ExcelParseFeign excelParseFeign;

    @Autowired
    private WorkOrderImageMapper workOrderImageMapper;

    @Autowired
    private ImageUrlBuilder imageUrlBuilder;

    // ================= 导入：Excel 文件 =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExcelResult<WorkOrderImportResultVO> importFromFile(MultipartFile file, String templateCode, boolean rollbackOnError) {
        return doImport(parseExcel(file, templateCode), rollbackOnError);
    }

    // ================= 导入：前端解析好的 JSON =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExcelResult<WorkOrderImportResultVO> importFromJson(List<WorkOrderExcelDTO> rows, boolean rollbackOnError) {
        return doImport(rows == null ? List.of() : rows, rollbackOnError);
    }

    // 核心导入：解析+校验 -> 类型识别 -> upsert（order_no 唯一）
    private ExcelResult<WorkOrderImportResultVO> doImport(List<WorkOrderExcelDTO> rows, boolean rollbackOnError) {
        WorkOrderImportResultVO vo = new WorkOrderImportResultVO();
        vo.setTotal(rows.size());
        List<WorkOrderImportResultVO.RowError> errors = new ArrayList<>();
        Map<String, Integer> typeSummary = new LinkedHashMap<>();

        // 1. 逐行映射 + 类型识别 + 校验（同工单号去重，后者覆盖前者）
        Map<String, WorkOrder> validMap = new LinkedHashMap<>();
        int rowNum = 0;
        for (WorkOrderExcelDTO dto : rows) {
            rowNum++;
            WorkOrder w = WorkOrderImportUtil.toEntity(dto);
            typeSummary.merge(WorkOrderImportUtil.typeName(w.getOrderNo()), 1, Integer::sum);

            String err = WorkOrderImportUtil.validate(w);
            if (err != null) {
                errors.add(new WorkOrderImportResultVO.RowError(rowNum, "第" + rowNum + "行：" + err));
                continue;
            }
            validMap.put(w.getOrderNo(), w);
        }

        // 2. 事务策略：全回滚模式且存在错误行 -> 抛异常整体回滚
        if (rollbackOnError && !errors.isEmpty()) {
            WorkOrderImportResultVO.RowError first = errors.get(0);
            throw new BusinessException("共 " + errors.size() + " 行校验失败，已全部回滚。第"
                    + first.getRow() + "行：" + first.getMsg());
        }

        // 3. upsert：存在则更新，不存在则新增
        WorkOrderImportSaveVO save = validMap.isEmpty()
                ? new WorkOrderImportSaveVO()
                : upsertByOrderNo(new ArrayList<>(validMap.values()));
        int insertCount = save.getInsertCount();
        int updateCount = save.getUpdateCount();

        vo.setInsertCount(insertCount);
        vo.setUpdateCount(updateCount);
        vo.setSuccessCount(insertCount + updateCount);
        vo.setErrorCount(errors.size());
        vo.setErrorList(errors);
        vo.setTypeSummary(typeSummary);

        ExcelResult<WorkOrderImportResultVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("导入完成：新增 " + insertCount + " 条，更新 " + updateCount + " 条，失败 " + errors.size() + " 条");
        result.setDataList(List.of(vo));
        result.setTotalRow(vo.getTotal());
        result.setSuccessRow(vo.getSuccessCount());
        return result;
    }

    // 调用 excel 服务解析文件
    private List<WorkOrderExcelDTO> parseExcel(MultipartFile file, String templateCode) {
        ExcelResult<WorkOrderExcelDTO> parsed = excelParseFeign.parseWorkOrderExcel(file, templateCode);
        if (!Boolean.TRUE.equals(parsed.getSuccess())) {
            throw new BusinessException("Excel 解析失败：" + parsed.getMsg());
        }
        return parsed.getDataList() == null ? List.of() : parsed.getDataList();
    }

    // ================= 批量保存导入数据（按 order_no upsert） =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderImportSaveVO saveImported(List<WorkOrder> list) {
        if (list == null || list.isEmpty()) {
            return new WorkOrderImportSaveVO();
        }
        return upsertByOrderNo(list);
    }

    // 共享 upsert：按 order_no 存在则更新、不存在则新增（同批重复工单号后者覆盖前者）
    private WorkOrderImportSaveVO upsertByOrderNo(List<WorkOrder> list) {
        // 同批内按工单号去重，避免重复 order_no 触发唯一键冲突
        Map<String, WorkOrder> uniqueMap = new LinkedHashMap<>();
        for (WorkOrder w : list) {
            uniqueMap.put(w.getOrderNo(), w);
        }

        Map<String, Long> existingIds = baseMapper.selectList(
                        new QueryWrapper<WorkOrder>().in("order_no", new ArrayList<>(uniqueMap.keySet())))
                .stream().collect(Collectors.toMap(WorkOrder::getOrderNo, WorkOrder::getId, (a, b) -> a));

        List<WorkOrder> toInsert = new ArrayList<>();
        List<WorkOrder> toUpdate = new ArrayList<>();
        for (WorkOrder w : uniqueMap.values()) {
            Long id = existingIds.get(w.getOrderNo());
            if (id == null) {
                toInsert.add(w);
            } else {
                w.setId(id);
                toUpdate.add(w);
            }
        }
        if (!toInsert.isEmpty()) {
            saveBatch(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            updateBatchById(toUpdate);
        }

        WorkOrderImportSaveVO vo = new WorkOrderImportSaveVO();
        vo.setInsertCount(toInsert.size());
        vo.setUpdateCount(toUpdate.size());
        vo.setTotal(uniqueMap.size());
        return vo;
    }

    // ================= 列表（工单 + 物料图片 url） =================

    @Override
    public IPage<WorkOrderListVO> listPage(WorkOrderQueryDTO query) {
        Page<WorkOrder> page = new Page<>(query.resolvedPageNum(), query.resolvedPageSize());
        IPage<WorkOrder> result = baseMapper.selectPage(page,
                // ⚠️ 末尾必须再按 id 兜一层：**分页要求排序键唯一**。
                // 批量导入的工单 create_time 会大量相同，只按它排时 MySQL 不保证两次查询的
                // 相对顺序一致 —— 翻页会漏行、也会重复行。改造前只查一次数组再本地 slice，
                // 所以这个问题根本不会暴露，是改后端分页才引入的新要求。
                filterOnly(query).orderByDesc("create_time").orderByDesc("id"));

        // 空页也要原样返回（total / pages 有意义）：不能因为本页没数据就回一个 total=0 的空页，
        // 否则前端分页器会以为整个结果集是空的
        Map<String, List<WorkOrderImage>> imageMap = loadImagesByOrderNo(result.getRecords());
        return result.convert(wo -> toListVO(wo, imageMap));
    }

    @Override
    public WorkOrderFilterOptionsVO filterOptions(DateRangeQueryDTO query) {
        // 按 listWorkOrders 的排序取候选：工单号倒序、产成品按中文排序，
        // 与列表里的先后一致，用户在弹窗里找起来才对得上
        QueryWrapper<WorkOrder> wrapper = dateRangeOnly(query)
                .select("order_no", "material_desc")
                .orderByDesc("plan_start_date").orderByDesc("order_no");

        List<WorkOrder> rows = baseMapper.selectList(wrapper);

        WorkOrderFilterOptionsVO vo = new WorkOrderFilterOptionsVO();
        // distinct() 保留首次出现的顺序 —— 上面已经排好序，这里不要再 sort 一遍把它打乱。
        // 工单号不做 numeric 排序：本工程的单据号是零填充定宽的（如 pick 表的 "0030704"），
        // 字典序与数值序一致
        vo.setOrderNos(rows.stream()
                .map(WorkOrder::getOrderNo)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList()));
        vo.setProductNames(rows.stream()
                .map(WorkOrder::getMaterialDesc)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                // 中文按拼音序：改造前前端用的 localeCompare('zh-CN')，SQL 的 ORDER BY
                // 在 utf8mb4 下是按码点，中文顺序与用户习惯对不上
                .sorted(Collator.getInstance(Locale.CHINA))
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    public List<WorkOrderGroupVO> summarizeByTypeAndMaterial(WorkOrderQueryDTO query) {
        // 按「工单号前缀 + 产成品」分组：工单报工那一页的行主键就是「类型 + 产成品」。
        // 前缀→类型的映射只在 Java 里做一份（WorkOrderTypeEnum），不把前缀当类型吐给前端。
        QueryWrapper<WorkOrder> wrapper = filterOnly(query)
                .select("LEFT(order_no, 4) AS order_no_prefix", "material_code", "material_desc",
                        "SUM(order_qty) AS order_qty", "SUM(confirmed_qty) AS confirmed_qty")
                // 2026-10-21：分组键加上 material_code —— 工单核算要按编码匹配入库行，
                // 只按名称分组时同一个名称可能混着不同编码（改名/换规格留下的历史数据）
                .groupBy("LEFT(order_no, 4)", "material_code", "material_desc")
                // 排序键用普通列（MyBatis-Plus 的 orderBy* 是把字符串原样拼进 ORDER BY 的）；
                // 显示顺序由前端按类型字典排，这里只要结果确定
                .orderByAsc("material_code").orderByAsc("material_desc")
                .orderByAsc("LEFT(order_no, 4)");

        return baseMapper.selectMaps(wrapper).stream().map(row -> {
            WorkOrderGroupVO vo = new WorkOrderGroupVO();
            String prefix = trimToEmpty(row.get("order_no_prefix"));
            WorkOrderTypeEnum type = WorkOrderTypeEnum.byPrefix(prefix);
            // 认不出前缀的类型（脏数据）不下架，只是没有类型名 ——
            // 原实现里前端 getReportOrderType 认不出会 continue 掉整行，
            // 这里一样：类型为空的行由前端跳过（见 index.vue 的 reportRows）
            vo.setTypeKey(type == null ? "" : type.getTypeKey());
            vo.setOrderType(type == null ? "" : type.getTypeName());
            vo.setMaterialCode(trimToEmpty(row.get("material_code")));
            vo.setMaterialName(trimToEmpty(row.get("material_desc")));
            vo.setOrderQty(toBigDecimal(row.get("order_qty")));
            vo.setConfirmedQty(toBigDecimal(row.get("confirmed_qty")));
            return vo;
        }).collect(Collectors.toList());
    }

    /** SUM 在「该组全是 NULL」时回 NULL，不是 0；统一折成 0，前端可以直接相加 */
    private BigDecimal toBigDecimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    /** 关联查询图片（一对多，按 order_no 分组）—— 一次 IN 查询取回整页，不逐条查 */
    private Map<String, List<WorkOrderImage>> loadImagesByOrderNo(List<WorkOrder> workOrders) {
        if (workOrders == null || workOrders.isEmpty()) {
            return Map.of();
        }
        List<String> orderNos = workOrders.stream().map(WorkOrder::getOrderNo).collect(Collectors.toList());
        return workOrderImageMapper.selectList(
                        new QueryWrapper<WorkOrderImage>().in("order_no", orderNos).orderByAsc("id"))
                .stream()
                .collect(Collectors.groupingBy(WorkOrderImage::getOrderNo));
    }

    private WorkOrderListVO toListVO(WorkOrder wo, Map<String, List<WorkOrderImage>> imageMap) {
        WorkOrderListVO vo = new WorkOrderListVO();
        vo.setWorkOrder(wo);
        vo.setImageList(imageMap.getOrDefault(wo.getOrderNo(), List.of())
                .stream().map(this::toImageVO).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 只带筛选条件（日期区间 + 工单类型 / 产成品 / 工单号）的 wrapper，不带排序。
     *
     * <p>日期筛的是 {@code plan_start_date}（基本开始日期）—— 与改造前前端
     * {@code getOrderDate()} 取 {@code planStartDate} 的口径一致。
     * 用 {@code ge}/{@code le} 而不是字符串比较：DATE 列走原生比较，且顺带把日期为 NULL 的
     * 工单排除掉，与改造前前端 {@code if (!planStartDate) return false} 一致。
     */
    private QueryWrapper<WorkOrder> filterOnly(WorkOrderQueryDTO query) {
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        LocalDate start = query.parsedStartDate();
        LocalDate end = query.parsedEndDate();
        if (start != null) {
            wrapper.ge("plan_start_date", start);
        }
        if (end != null) {
            wrapper.le("plan_start_date", end);
        }
        if (StringUtils.hasText(query.getType())) {
            String prefix = WorkOrderTypeEnum.prefixOf(query.getType());
            if (prefix == null) {
                // 认不出的类型标识**不要**当成「不限」——那会把全部工单返回回去，
                // 看起来像筛选失效。沿用改造前的行为：查不到就是空结果。
                wrapper.apply("1 = 0");
            } else {
                wrapper.likeRight("order_no", prefix);
            }
        }
        if (StringUtils.hasText(query.getMaterialCode())) {
            wrapper.eq("material_code", query.getMaterialCode().trim());
        }
        if (StringUtils.hasText(query.getMaterialDesc())) {
            wrapper.eq("material_desc", query.getMaterialDesc().trim());
        }
        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.eq("order_no", query.getOrderNo().trim());
        }
        return wrapper;
    }

    /** 只带日期区间的 wrapper —— 给筛选候选用（候选不能再受其它筛选项影响） */
    private QueryWrapper<WorkOrder> dateRangeOnly(DateRangeQueryDTO query) {
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        LocalDate start = query.parsedStartDate();
        LocalDate end = query.parsedEndDate();
        if (start != null) {
            wrapper.ge("plan_start_date", start);
        }
        if (end != null) {
            wrapper.le("plan_start_date", end);
        }
        return wrapper;
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    // ================= 新增/编辑 =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExcelResult<Void> saveWorkOrder(WorkOrderSaveDTO dto) {
        WorkOrder wo = dto.getWorkOrder();
        if (wo.getId() == null) {
            baseMapper.insert(wo);
        } else {
            baseMapper.updateById(wo);
        }
        return success();
    }

    // ================= 详情（工单 + 物料图片 url） =================
    @Override
    public ExcelResult<WorkOrderDetailVO> getWorkOrderDetail(Long id) {
        WorkOrder wo = baseMapper.selectById(id);
        if (wo == null) {
            ExcelResult<WorkOrderDetailVO> result = new ExcelResult<>();
            result.setSuccess(false);
            result.setMsg("工单不存在");
            result.setDataList(List.of());
            result.setTotalRow(0);
            return result;
        }

        List<WorkOrderImage> images = workOrderImageMapper.selectList(
                new QueryWrapper<WorkOrderImage>().eq("order_no", wo.getOrderNo()).orderByAsc("id"));

        WorkOrderDetailVO vo = new WorkOrderDetailVO();
        vo.setWorkOrder(wo);
        vo.setImageList(images.stream().map(this::toImageVO).collect(Collectors.toList()));
        return buildDetailResult(vo);
    }

    // ================= 私有工具 =================

    private WorkOrderImageVO toImageVO(WorkOrderImage img) {
        WorkOrderImageVO vo = new WorkOrderImageVO();
        vo.setImageId(img.getId());
        vo.setUrl(imageUrlBuilder.urlOf(img.getFileName()));
        vo.setThumbnailUrl(imageUrlBuilder.thumbUrlOf(img.getFileName()));
        return vo;
    }

    private ExcelResult<Void> success() {
        ExcelResult<Void> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("操作成功");
        return result;
    }

    private ExcelResult<WorkOrderDetailVO> buildDetailResult(WorkOrderDetailVO vo) {
        ExcelResult<WorkOrderDetailVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("查询成功");
        result.setDataList(List.of(vo));
        result.setTotalRow(1);
        return result;
    }

}
