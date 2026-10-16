package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.example.dto.PageResult;
import org.example.dto.PurchaseTrackingQueryDTO;
import org.example.dto.PurchaseTrackingSaveDTO;
import org.example.dto.PurchaseTrackingVO;
import org.example.dto.Result;
import org.example.service.PurchaseTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购进度跟踪（需求登记 → 下单 → 到货）。
 *
 * <p>鉴权沿用既有约定：**查询类免登录**（与 /api/stock/list、/api/pick/list、
 * /api/inbound/list 一致 —— 这一页的数据是厂内公开的采购进度，不是敏感数据），
 * 写操作走 {@code @SaCheckPermission}（不变式 1：写接口必须带权限位）。
 *
 * <p>权限位用新造的 {@code purchase:edit}，而不是复用一个含义不符的旧位
 * （比如 work_order:import）—— 那一页将来要单独授权给采购/仓库岗时，
 * 复用会让「谁能改采购进度」说不清楚。新权限位要同时做两件事，
 * 否则会出现「接口有闸门、但没有任何角色拿得到钥匙」的哑火：
 * ① 在 {@code docs/schema.sql} 第 2 部分的角色种子与迁移步骤里各登记一次；
 * ② 对**既有库**执行一次迁移（生产已执行，见变更-091 的部署记录）。
 */
@RestController
@RequestMapping("/api/purchase")
public class PurchaseTrackingController {

    @Autowired
    private PurchaseTrackingService purchaseTrackingService;

    /**
     * 列表：后端分页 + 后端关键字 / 日期筛选。
     *
     * <p>{@code pageNum} / {@code pageSize} 必传，缺参由全局异常处理回 400
     * （理由见 {@link org.example.dto.PageQueryDTO}：给默认值会让老客户端安静地只拿到第 1 页）。
     */
    @GetMapping("/list")
    public PageResult<PurchaseTrackingVO> list(@Valid PurchaseTrackingQueryDTO query) {
        IPage<PurchaseTrackingVO> page = purchaseTrackingService.listPage(query);
        return PageResult.of(page.getRecords(), page.getTotal(),
                query.resolvedPageNum(), query.resolvedPageSize());
    }

    /** 新增或修改（依 {@code id} 是否为空）。保存成功后会把物料编码登记进 material_stock */
    @SaCheckPermission("purchase:edit")
    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody PurchaseTrackingSaveDTO dto) {
        return Result.success(purchaseTrackingService.save(dto));
    }

    @SaCheckPermission("purchase:edit")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        purchaseTrackingService.delete(id);
        return Result.success(null);
    }
}
