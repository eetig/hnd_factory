package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.dto.ExcelResult;
import org.example.dto.MaterialStockVO;
import org.example.dto.Result;
import org.example.entity.MaterialStock;
import org.example.service.MaterialStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物料库存（SAP 库存导出汇总）。
 *
 * <p>数据来源是「文件导入」的**库存汇总**类型（见 WorkOrderImportController 的 detectType
 * 与 MaterialStockServiceImpl#saveImported），页面只读展示，这里的增删改查接口先备齐。
 *
 * <p>鉴权沿用既有约定：查询类免登录（与 /api/pick/list、/api/inbound/list 一致），
 * 写操作沿用 {@code work_order:import} —— 库存数据本来就由导入维护，不另造权限位
 * （新权限位要同时改角色种子与既有库，代价大且眼下用不上）。
 */
@RestController
@RequestMapping("/api/stock")
public class MaterialStockController {

    @Autowired
    private MaterialStockService materialStockService;

    /** 全量返回，排序 / 筛选 / 分页由前端本地完成；物料名称与规格联查 material_master 带出 */
    @GetMapping("/list")
    public ExcelResult<MaterialStockVO> list() {
        List<MaterialStockVO> list = materialStockService.listAll();
        ExcelResult<MaterialStockVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("查询成功");
        result.setDataList(list);
        result.setTotalRow(list.size());
        result.setSuccessRow(list.size());
        return result;
    }

    @SaCheckPermission("work_order:import")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody MaterialStock row) {
        materialStockService.add(row);
        return Result.success(null);
    }

    @SaCheckPermission("work_order:import")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody MaterialStock row) {
        if (row == null || row.getId() == null) {
            return Result.fail("缺少 id，无法修改");
        }
        materialStockService.update(row);
        return Result.success(null);
    }

    @SaCheckPermission("work_order:import")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        materialStockService.remove(id);
        return Result.success(null);
    }
}
