package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.ExcelResult;
import org.example.dto.WorkOrderGroupVO;
import org.example.dto.PageResult;
import org.example.dto.Result;
import org.example.dto.WorkOrderDetailVO;
import org.example.dto.WorkOrderExcelDTO;
import org.example.dto.WorkOrderFilterOptionsVO;
import org.example.dto.WorkOrderImageVO;
import org.example.dto.WorkOrderImportResultVO;
import org.example.dto.WorkOrderListVO;
import org.example.dto.WorkOrderQueryDTO;
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

    /**
     * 工单列表 —— 后端分页 + 后端条件查询（返回工单信息 + 物料图片 url）。
     *
     * <p>改造前这条接口是「全量返回，筛选/分页由前端本地完成」。前端因此持有一份
     * 「页面打开那一刻」的快照。
     *
     * <p>{@code pageNum} / {@code pageSize} 必传，缺参回 400（理由见 {@link org.example.dto.PageQueryDTO}）。
     *
     * <p>本路径落在 {@code /api/work-order/**} 下，GET 被 {@code SaTokenConfigure} 的只读闸门
     * 收归 admin（决策-004）—— 上面几行旧注释写的「查询类接口，免登录」是决策-004 之前的口径，
     * 别再照抄。
     */
    @GetMapping("/list")
    public PageResult<WorkOrderListVO> list(@Valid WorkOrderQueryDTO query) {
        IPage<WorkOrderListVO> page = workOrderService.listPage(query);
        return PageResult.of(page.getRecords(), page.getTotal(),
                query.resolvedPageNum(), query.resolvedPageSize());
    }

    /**
     * 工单汇总三个筛选弹窗的可选项（工单号 / 产成品）。
     *
     * <p>改造前是前端从全量工单里 {@code new Set(...)} 现算的 —— 分页之后前端不再持有
     * 全量数据，候选必须由服务端给。只按日期区间取，不带其它筛选项（否则选中一个就改选不了）。
     */
    @GetMapping("/filter-options")
    public Result<WorkOrderFilterOptionsVO> filterOptions(DateRangeQueryDTO query) {
        return Result.success(workOrderService.filterOptions(query));
    }

    /**
     * 按「工单类型 + 产成品」汇总 —— 工单报工与工单核算两个聚合页的数据来源。
     *
     * <p>这两页都不是列表：工单报工以「类型 + 产成品」为行、把订单数量与确认产量按组求和；
     * 工单核算只要按产成品汇总的确认产量。列表改成分页之后，聚合必须由服务端按同一套
     * 筛选条件算完再给 —— 拿第 1 页做 group by 只会得到前 10 条的合计。
     *
     * <p>不标 {@code @Valid}：它是聚合不是记录页，不收分页参数（传了也会被忽略）。
     */
    @GetMapping("/summary-by-type-material")
    public Result<List<WorkOrderGroupVO>> summaryByTypeAndMaterial(WorkOrderQueryDTO query) {
        return Result.success(workOrderService.summarizeByTypeAndMaterial(query));
    }

    // 新增/编辑工单
    @SaCheckPermission("work_order:edit")
    @PostMapping("/save")
    public ExcelResult<Void> save(@RequestBody WorkOrderSaveDTO dto) {
        return workOrderService.saveWorkOrder(dto);
    }

    // 查询工单详情（工单信息 + 物料图片 url）—— 查询类接口，免登录
    @GetMapping("/detail")
    public ExcelResult<WorkOrderDetailVO> detail(@RequestParam("id") Long id) {
        return workOrderService.getWorkOrderDetail(id);
    }
}
