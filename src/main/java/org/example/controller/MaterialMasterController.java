package org.example.controller;

import org.example.dto.MaterialMatchVO;
import org.example.dto.MaterialVO;
import org.example.dto.Result;
import org.example.service.MaterialMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物料主数据查询（图片解析辅助录入用）。
 *
 * <p>查询类接口，免登录 —— 与 /api/pick/list、/api/inbound/list 的既有约定一致
 * （本工程用注解式鉴权，未加 @SaCheckLogin 即为放行）。
 */
@RestController
@RequestMapping("/api/material")
public class MaterialMasterController {

    @Autowired
    private MaterialMasterService materialMasterService;

    /**
     * 按识别出的物料名称匹配编码。
     *
     * <p>返回 { matched, candidates }：matched 仅在唯一严格命中时非空；
     * 其余情况下编码留空，由前端展示 candidates 供人工选择。
     */
    @GetMapping("/match")
    public Result<MaterialMatchVO> match(@RequestParam("name") String name) {
        return Result.success(materialMasterService.match(name));
    }

    /**
     * 人工检索物料（前端编码单元格的搜索框用）。
     */
    @GetMapping("/search")
    public Result<List<MaterialVO>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(materialMasterService.search(keyword, limit));
    }
}
