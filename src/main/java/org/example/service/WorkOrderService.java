package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.ExcelResult;
import org.example.dto.WorkOrderGroupVO;
import org.example.dto.WorkOrderDetailVO;
import org.example.dto.WorkOrderExcelDTO;
import org.example.dto.WorkOrderFilterOptionsVO;
import org.example.dto.WorkOrderImportResultVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.dto.WorkOrderListVO;
import org.example.dto.WorkOrderPreviewVO;
import org.example.dto.WorkOrderQueryDTO;
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

    /**
     * 工单列表：后端分页 + 后端条件查询（整改 2026-10-10），返回工单信息 + 物料图片 url。
     *
     * <p>改造前是 {@code listWorkOrders(String type)} 把全表返回、筛选分页由前端内存数组完成 ——
     * 前端因此持有一份「页面打开那一刻」的快照，库里的新增在筛选时永远看不到。
     *
     * <p>调用方有两处（都是同一个接口）：工单汇总面板，以及周统计页里那张工单明细表。
     */
    IPage<WorkOrderListVO> listPage(WorkOrderQueryDTO query);

    /**
     * 工单汇总三个筛选弹窗的可选项（工单号 / 产成品），只按日期区间取。
     *
     * <p>分页之后前端不再持有全量工单，候选必须由服务端给。
     */
    WorkOrderFilterOptionsVO filterOptions(DateRangeQueryDTO query);

    /**
     * 按产成品汇总确认产量 —— 工单核算页的「已报工数」来源。
     *
     * <p>筛选条件与 {@link #listPage} 完全一致（工单核算的已报工数就是按「工单汇总当前筛选后」
     * 算的）。入参里的 {@code pageNum} / {@code pageSize} 会被忽略，聚合不分页。
     */
    List<WorkOrderGroupVO> summarizeByTypeAndMaterial(WorkOrderQueryDTO query);

    // 新增/编辑工单
    ExcelResult<Void> saveWorkOrder(WorkOrderSaveDTO dto);

    // 查询工单详情（工单信息 + 物料图片 url）
    ExcelResult<WorkOrderDetailVO> getWorkOrderDetail(Long id);
}
