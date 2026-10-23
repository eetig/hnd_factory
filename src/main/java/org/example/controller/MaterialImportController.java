package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import org.example.dto.MaterialImportPreviewVO;
import org.example.dto.MaterialImportResultVO;
import org.example.dto.Result;
import org.example.service.MaterialImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 物料主数据导入（两阶段，2026-10-23，变更-159）。
 *
 * <p>与其他物料查询接口不同，这两个接口要 <b>admin</b> —— 它们会改主数据。
 * 用角色而不是权限：本工程没有给「物料导入」单独配权限点，硬写一个不存在的
 * permission 会把管理员也挡在外面。
 */
@RestController
@RequestMapping("/api/material/import")
public class MaterialImportController {

    @Autowired
    private MaterialImportService materialImportService;

    /** 第一步：上传并比对，不落库；返回 token 与差异统计 */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckRole("admin")
    public Result<MaterialImportPreviewVO> preview(@RequestParam("file") MultipartFile file) {
        return Result.success(materialImportService.preview(file));
    }

    /** 第二步：凭 token 落库（token 一次性，提交后即失效） */
    @PostMapping("/commit")
    @SaCheckRole("admin")
    public Result<MaterialImportResultVO> commit(@RequestParam("token") String token) {
        return Result.success(materialImportService.commit(token));
    }
}
