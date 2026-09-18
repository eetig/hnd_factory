package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.constant.WorkOrderTypeEnum;
import org.example.dto.ExcelResult;
import org.example.dto.ImgResult;
import org.example.dto.WorkOrderDetailVO;
import org.example.dto.WorkOrderExcelDTO;
import org.example.dto.WorkOrderImageVO;
import org.example.dto.WorkOrderImportResultVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.dto.WorkOrderListVO;
import org.example.dto.WorkOrderPreviewRowVO;
import org.example.dto.WorkOrderPreviewVO;
import org.example.dto.WorkOrderSaveDTO;
import org.example.entity.WorkOrder;
import org.example.entity.WorkOrderImage;
import org.example.exception.BusinessException;
import org.example.feign.ExcelParseFeign;
import org.example.feign.ImgFeignClient;
import org.example.mapper.WorkOrderImageMapper;
import org.example.mapper.WorkOrderMapper;
import org.example.service.WorkOrderService;
import org.example.util.WorkOrderImportUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService {

    @Autowired
    private ExcelParseFeign excelParseFeign;

    @Autowired
    private ImgFeignClient imgFeignClient;

    @Autowired
    private WorkOrderImageMapper workOrderImageMapper;

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

    // ================= 导入预览（分页，不入库） =================
    @Override
    public ExcelResult<WorkOrderPreviewVO> previewImport(MultipartFile file, String templateCode, long page, long size) {
        List<WorkOrderExcelDTO> rows = parseExcel(file, templateCode);

        long p = page < 1 ? 1 : page;
        long s = size < 1 ? 20 : size;
        int from = (int) Math.min((p - 1) * s, rows.size());
        int to = (int) Math.min(from + s, rows.size());

        List<WorkOrderPreviewRowVO> list = new ArrayList<>();
        List<WorkOrderExcelDTO> pageRows = rows.subList(from, to);
        for (int i = 0; i < pageRows.size(); i++) {
            WorkOrder w = mapToEntity(pageRows.get(i));
            WorkOrderPreviewRowVO row = new WorkOrderPreviewRowVO();
            row.setRowNum(from + i + 1);
            row.setOrderNo(w.getOrderNo());
            row.setWorkOrderType(typeName(w.getOrderNo()));
            row.setWorkOrder(w);
            list.add(row);
        }

        WorkOrderPreviewVO vo = new WorkOrderPreviewVO();
        vo.setPage(p);
        vo.setSize(s);
        vo.setTotal(rows.size());
        vo.setTotalPage(s == 0 ? 0 : (rows.size() + s - 1) / s);
        vo.setRows(list);

        ExcelResult<WorkOrderPreviewVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("解析成功");
        result.setDataList(List.of(vo));
        result.setTotalRow(rows.size());
        return result;
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
            WorkOrder w = mapToEntity(dto);
            typeSummary.merge(typeName(w.getOrderNo()), 1, Integer::sum);

            String err = validate(w, rowNum);
            if (err != null) {
                errors.add(new WorkOrderImportResultVO.RowError(rowNum, err));
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

    // 工单号 -> 类型中文名（识别不出为"未知类型"）
    private static String typeName(String orderNo) {
        return WorkOrderImportUtil.typeName(orderNo);
    }

    // ================= 列表（工单 + 物料图片 url） =================
    @Override
    public ExcelResult<WorkOrderListVO> listWorkOrders(String type) {
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(type)) {
            String prefix = WorkOrderTypeEnum.prefixOf(type);
            if (prefix == null) {
                return buildListResult(List.of());
            }
            wrapper.likeRight("order_no", prefix);
        }
        wrapper.orderByDesc("create_time");
        List<WorkOrder> workOrders = baseMapper.selectList(wrapper);
        if (workOrders.isEmpty()) {
            return buildListResult(List.of());
        }

        // 关联查询图片（一对多，按 order_no 分组）
        List<String> orderNos = workOrders.stream().map(WorkOrder::getOrderNo).collect(Collectors.toList());
        List<WorkOrderImage> images = workOrderImageMapper.selectList(
                new QueryWrapper<WorkOrderImage>().in("order_no", orderNos).orderByAsc("id"));
        Map<String, List<WorkOrderImage>> imageMap = images.stream()
                .collect(Collectors.groupingBy(WorkOrderImage::getOrderNo));

        List<WorkOrderListVO> vos = workOrders.stream().map(wo -> {
            WorkOrderListVO vo = new WorkOrderListVO();
            vo.setWorkOrder(wo);
            vo.setImageList(imageMap.getOrDefault(wo.getOrderNo(), List.of())
                    .stream().map(this::toImageVO).collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());

        return buildListResult(vos);
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
    private String toUrl(String fileName) {
        ImgResult<String> r = imgFeignClient.getPresignedUrl(fileName);
        return r != null && r.getCode() == 200 ? r.getData() : null;
    }

    private WorkOrderImageVO toImageVO(WorkOrderImage img) {
        WorkOrderImageVO vo = new WorkOrderImageVO();
        vo.setImageId(img.getId());
        vo.setUrl(toUrl(img.getFileName()));
        return vo;
    }

    private ExcelResult<Void> success() {
        ExcelResult<Void> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("操作成功");
        return result;
    }

    private ExcelResult<WorkOrderListVO> buildListResult(List<WorkOrderListVO> list) {
        ExcelResult<WorkOrderListVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("查询成功");
        result.setDataList(list);
        result.setTotalRow(list.size());
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

    private WorkOrder mapToEntity(WorkOrderExcelDTO dto) {
        return WorkOrderImportUtil.toEntity(dto);
    }

    private String validate(WorkOrder w, int row) {
        String err = WorkOrderImportUtil.validate(w);
        return err == null ? null : "第" + row + "行：" + err;
    }
}
