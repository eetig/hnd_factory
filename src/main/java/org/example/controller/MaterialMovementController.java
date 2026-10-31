package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.dto.ExcelResult;
import org.example.service.MaterialMovementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/movement")
public class MaterialMovementController {

    @Autowired
    private MaterialMovementService materialMovementService;

    /** 投料汇总（变更-172 续）：原辅料核算的「投料数」来源 */
    @Autowired
    private org.example.service.WorkOrderMaterialService workOrderMaterialService;

    // 货物移动导入（写库操作，需登录 + 权限）—— 旧版一次性导入接口
    @SaCheckPermission("goods_move:import")
    @PostMapping("/import")
    public ExcelResult<Void> importGoodsMove(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "templateCode", defaultValue = "goods_move") String templateCode) {
        return materialMovementService.importGoodsMove(file, templateCode);
    }
    /**
     * 按物料汇总净投料（原辅料核算页的「投料数」来源，2026-10-31 变更-172 续）。
     *
     * <p>为什么单独一个接口：原辅料核算现在是「领料 ∪ 投料」并集，
     * 领料侧已有 `/api/pick/summary`，投料侧就缺这一条。
     * 口径与工单投料一致：261 投料 + 262 冲销相抵，取正。
     */
    @GetMapping("/issue-summary")
    public org.example.dto.Result<java.util.List<org.example.dto.MaterialIssueSummaryVO>> issueSummary(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate endDate) {
        return org.example.dto.Result.success(workOrderMaterialService.issueSummaryByMaterial(startDate, endDate));
    }

}
