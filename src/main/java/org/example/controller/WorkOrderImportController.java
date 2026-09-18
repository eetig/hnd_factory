package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.alibaba.excel.EasyExcel;
import org.example.component.WorkOrderImportCache;
import org.example.dto.Result;
import org.example.dto.WorkOrderExcelDTO;
import org.example.dto.WorkOrderImportPreviewVO;
import org.example.dto.WorkOrderImportResultVO;
import org.example.dto.WorkOrderImportSaveDTO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.dto.WorkOrderPreviewItemVO;
import org.example.entity.WorkOrder;
import org.example.listener.WorkOrderImportExcelListener;
import org.example.service.WorkOrderService;
import org.example.util.ExcelCleanUtil;
import org.example.util.WorkOrderImportUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单导入（两阶段）：preview 解析并缓存 -> save 从缓存批量落库。
 */
@RestController
@RequestMapping("/api/work-order/import")
public class WorkOrderImportController {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderImportController.class);

    @Autowired
    private WorkOrderImportCache importCache;

    @Autowired
    private WorkOrderService workOrderService;

    // ================= 接口1：解析预览（只解析，不入库） =================
    @SaCheckPermission("work_order:import")
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<WorkOrderImportPreviewVO> preview(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的Excel文件");
        }
        log.info("工单导入解析开始, 文件名={}, 大小={}字节", file.getOriginalFilename(), file.getSize());

        WorkOrderImportExcelListener listener = new WorkOrderImportExcelListener();
        try {
            // 清洗：SAP 导出数值单元格可能带首尾空格（<v>0 </v>），先去掉再解析
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename();
            if (filename != null && filename.toLowerCase().endsWith(".xlsx")) {
                bytes = ExcelCleanUtil.clean(bytes);
            }
            EasyExcel.read(new ByteArrayInputStream(bytes), listener).sheet().doRead();
        } catch (Exception e) {
            log.error("工单Excel解析失败, 文件名={}", file.getOriginalFilename(), e);
            return Result.fail(describeError(e));
        }

        List<WorkOrderExcelDTO> dtoList = listener.getDataList();
        List<WorkOrderPreviewItemVO> itemList = new ArrayList<>();
        List<WorkOrderImportResultVO.RowError> errorRows = new ArrayList<>();
        List<WorkOrder> validList = new ArrayList<>();   // 只缓存通过校验的行
        String billType = "工单汇总";                     // 单据大类：工单汇总 / 货物移动

        int rowNum = 0;
        for (WorkOrderExcelDTO dto : dtoList) {
            rowNum++;
            // 存在移动类型字段 -> 货物移动，否则 -> 工单汇总
            if (StringUtils.hasText(dto.getMoveType())) {
                billType = "货物移动";
            }

            WorkOrder w = WorkOrderImportUtil.toEntity(dto);
            String err = WorkOrderImportUtil.validate(w);
            if (err != null) {
                errorRows.add(new WorkOrderImportResultVO.RowError(rowNum, "第" + rowNum + "行：" + err));
                continue;
            }
            validList.add(w);
            itemList.add(toItemVO(w));
        }

        String taskId = importCache.put(validList);

        WorkOrderImportPreviewVO vo = new WorkOrderImportPreviewVO();
        vo.setTaskId(taskId);
        vo.setWorkOrderType(billType);
        vo.setTotal(dtoList.size());
        vo.setList(itemList);
        vo.setErrorRows(errorRows);

        log.info("工单导入解析完成, taskId={}, 总行数={}, 有效={}, 错误={}, 类型={}",
                taskId, dtoList.size(), validList.size(), errorRows.size(), vo.getWorkOrderType());
        return Result.success("解析成功", vo);
    }

    // ================= 接口2：确认保存（从缓存批量落库，按 order_no upsert） =================
    @SaCheckPermission("work_order:import")
    @PostMapping("/save")
    public Result<Map<String, Object>> save(@RequestBody WorkOrderImportSaveDTO body) {
        String taskId = body == null ? null : body.getTaskId();
        if (!StringUtils.hasText(taskId)) {
            return Result.fail("taskId 不能为空");
        }
        log.info("工单导入保存开始, taskId={}", taskId);

        List<WorkOrder> list = importCache.take(taskId);
        if (list == null) {
            log.warn("工单导入保存失败, taskId 不存在或已过期, taskId={}", taskId);
            return Result.fail("taskId 不存在或已过期，请重新上传解析");
        }
        if (list.isEmpty()) {
            return Result.fail("没有可保存的数据");
        }

        try {
            WorkOrderImportSaveVO save = workOrderService.saveImported(list);
            log.info("工单导入保存完成, taskId={}, 新增={}, 更新={}", taskId, save.getInsertCount(), save.getUpdateCount());
            Map<String, Object> data = new HashMap<>();
            data.put("addCount", save.getInsertCount());
            data.put("updateCount", save.getUpdateCount());
            data.put("failRows", List.of());
            return Result.success("导入完成", data);
        } catch (Exception e) {
            log.error("工单导入保存失败, taskId={}", taskId, e);
            return Result.fail("保存失败：" + describeError(e));
        }
    }

    // 工单实体 -> 预览行
    private WorkOrderPreviewItemVO toItemVO(WorkOrder w) {
        WorkOrderPreviewItemVO vo = new WorkOrderPreviewItemVO();
        vo.setOrderNo(w.getOrderNo());
        vo.setMaterialCode(w.getMaterialCode());
        vo.setMaterialDesc(w.getMaterialDesc());     // 物料描述（产成品）
        vo.setOrderQty(w.getOrderQty());             // 订单数量
        vo.setPlanStartDate(w.getPlanStartDate());   // 基本开始日期
        vo.setConfirmedQty(w.getConfirmedQty());     // 确认的产量
        return vo;
    }

    // EasyExcel 底层异常会层层包装，取最里层 cause 才是真正原因
    private String describeError(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof NumberFormatException) {
            return "文件中存在含非法字符的数字单元格（常见于数量/重量列混入空格或千分位），请检查该列数据格式。原始错误："
                    + root.getMessage();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
