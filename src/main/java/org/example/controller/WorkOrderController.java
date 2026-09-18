package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.dto.ExcelResult;
import org.example.dto.WorkOrderDetailVO;
import org.example.dto.WorkOrderExcelDTO;
import org.example.dto.WorkOrderImportResultVO;
import org.example.dto.WorkOrderListVO;
import org.example.dto.WorkOrderSaveDTO;
import org.example.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/work-order")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    // 导入：Excel 文件（rollbackOnError=true 时有错误行则全部回滚）
    @SaCheckPermission("work_order:import")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExcelResult<WorkOrderImportResultVO> importWorkOrder(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "templateCode", defaultValue = "work_order") String templateCode,
            @RequestParam(value = "rollbackOnError", defaultValue = "false") boolean rollbackOnError) {
        return workOrderService.importFromFile(file, templateCode, rollbackOnError);
    }

    // 导入：前端解析好的工单列表 JSON
    @SaCheckPermission("work_order:import")
    @PostMapping("/import/json")
    public ExcelResult<WorkOrderImportResultVO> importJson(
            @RequestBody List<WorkOrderExcelDTO> rows,
            @RequestParam(value = "rollbackOnError", defaultValue = "false") boolean rollbackOnError) {
        return workOrderService.importFromJson(rows, rollbackOnError);
    }

    // 导入分页预览移动到 WorkOrderImportController（/api/work-order/import/preview）

    // 查询工单列表（type 可选，返回工单信息 + 物料图片 url）
    @SaCheckPermission("work_order:view")
    @GetMapping("/list")
    public ExcelResult<WorkOrderListVO> list(@RequestParam(value = "type", required = false) String type) {
        return workOrderService.listWorkOrders(type);
    }

    // 新增/编辑工单
    @SaCheckPermission("work_order:edit")
    @PostMapping("/save")
    public ExcelResult<Void> save(@RequestBody WorkOrderSaveDTO dto) {
        return workOrderService.saveWorkOrder(dto);
    }

    // 查询工单详情（工单信息 + 物料图片 url）
    @SaCheckPermission("work_order:view")
    @GetMapping("/detail")
    public ExcelResult<WorkOrderDetailVO> detail(@RequestParam("id") Long id) {
        return workOrderService.getWorkOrderDetail(id);
    }
}
