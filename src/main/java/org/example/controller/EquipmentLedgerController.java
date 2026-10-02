package org.example.controller;

import org.example.dto.EquipmentLedgerVO;
import org.example.dto.Result;
import org.example.service.EquipmentLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备台账查询（只读）。
 *
 * <p>典型用法：输入「三甲」→ 返回设备名称/位号/规格/车间里含「三甲」的设备及其参数
 * （位号、规格、容积或换热面积、封头容积、每 mm 液位对应体积与质量）。
 *
 * <p>现已有前端入口：月底储罐液位记录录入时的「容器名称」候选 —— 那边点开下拉就调本接口
 * （空关键字取「全部候选」，见下方 {@code keyword} 说明），所以**不能**再把空关键字当无意义输入拒掉。
 *
 * <p>查询类接口，免登录 —— 与 /api/pick/list、/api/inbound/list 的既有约定一致
 * （本工程用注解式鉴权，未加 @SaCheck* 即为放行）。本次没有任何写接口，
 * 将来若加录入/维护接口，必须补 @SaCheckPermission（见《前后端改动统筹》不变式 1）。
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentLedgerController {

    @Autowired
    private EquipmentLedgerService equipmentLedgerService;

    /**
     * 按关键字模糊检索设备台账；结果按设备名称**拼音顺序**（首字母）返回。
     *
     * @param keyword  关键字，匹配位号/名称/规格/车间；**留空返回前 limit 条**
     *                 （即「全部候选」，供下拉框点开即列出），变更-012 起如此
     * @param workshop 车间，可选，精确匹配
     * @param limit    条数上限，可选，默认 20、最多 100
     */
    @GetMapping("/search")
    public Result<List<EquipmentLedgerVO>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "workshop", required = false) String workshop,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(equipmentLedgerService.search(keyword, workshop, limit));
    }
}
