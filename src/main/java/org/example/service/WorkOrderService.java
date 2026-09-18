package org.example.service;

import org.example.dto.ExcelResult;
import org.example.dto.WorkOrderDetailVO;
import org.example.dto.WorkOrderExcelDTO;
import org.example.dto.WorkOrderImportResultVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.dto.WorkOrderListVO;
import org.example.dto.WorkOrderPreviewVO;
import org.example.dto.WorkOrderSaveDTO;
import org.example.entity.WorkOrder;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface WorkOrderService {
    // 批量保存导入的工单（按 order_no upsert：存在则更新，不存在则新增）
    WorkOrderImportSaveVO saveImported(List<WorkOrder> list);

    // 导入：Excel 文件（rollbackOnError=true 时有错误行则全部回滚）
    ExcelResult<WorkOrderImportResultVO> importFromFile(MultipartFile file, String templateCode, boolean rollbackOnError);

    // 导入：前端解析好的工单列表 JSON
    ExcelResult<WorkOrderImportResultVO> importFromJson(List<WorkOrderExcelDTO> rows, boolean rollbackOnError);

    // 导入分页预览（只解析不入库）
    ExcelResult<WorkOrderPreviewVO> previewImport(MultipartFile file, String templateCode, long page, long size);

    // 查询工单列表（type 可选，返回工单信息 + 物料图片 url）
    ExcelResult<WorkOrderListVO> listWorkOrders(String type);

    // 新增/编辑工单
    ExcelResult<Void> saveWorkOrder(WorkOrderSaveDTO dto);

    // 查询工单详情（工单信息 + 物料图片 url）
    ExcelResult<WorkOrderDetailVO> getWorkOrderDetail(Long id);
}
