package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.dto.ExcelResult;
import org.example.dto.ProductionInboundVO;
import org.example.service.ProductionInboundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inbound")
public class ProductionInboundController {

    @Autowired
    private ProductionInboundService productionInboundService;

    // 生产入库单列表（全量返回，日期筛选/排序/分页由前端本地完成）
    @SaCheckPermission("inbound:view")
    @GetMapping("/list")
    public ExcelResult<ProductionInboundVO> list() {
        List<ProductionInboundVO> list = productionInboundService.listAll();
        ExcelResult<ProductionInboundVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("查询成功");
        result.setDataList(list);
        result.setTotalRow(list.size());
        result.setSuccessRow(list.size());
        return result;
    }
}
