package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.example.component.ImgUploader;
import org.example.dto.InspectRecordSaveDTO;
import org.example.dto.InspectRecordVO;
import org.example.dto.InspectTemplateSaveDTO;
import org.example.dto.InspectTemplateVO;
import org.example.dto.Result;
import org.example.exception.BusinessException;
import org.example.service.InspectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 巡检接口（2026-11-19，变更-254）。
 *
 * <p>只在手机用：现场对着清单点「正常/异常」，异常的写说明拍照。
 *
 * <p>权限只要登录：巡检是「自己的活」，不涉及金额；也不按人隔离 ——
 * 谁去查的谁负责，别人也能看到、帮着一块看。
 */
@RestController
@RequestMapping("/api/inspect")
public class InspectController {

    private static final long MAX_BYTES = 8L * 1024 * 1024;

    @Autowired
    private InspectService inspectService;

    @Autowired
    private ImgUploader imgUploader;

    @GetMapping("/templates")
    public Result<List<InspectTemplateVO>> templates() {
        requireLogin();
        return Result.success(inspectService.listTemplates());
    }

    @GetMapping("/templates/{id}")
    public Result<InspectTemplateVO> template(@PathVariable Long id) {
        requireLogin();
        return Result.success(inspectService.getTemplate(id));
    }

    @PostMapping("/templates")
    public Result<InspectTemplateVO> createTemplate(@RequestBody InspectTemplateSaveDTO dto) {
        requireLogin();
        return Result.success(inspectService.createTemplate(dto, StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/templates/{id}")
    public Result<InspectTemplateVO> updateTemplate(@PathVariable Long id,
                                                    @RequestBody InspectTemplateSaveDTO dto) {
        requireLogin();
        return Result.success(inspectService.updateTemplate(id, dto));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        requireLogin();
        inspectService.deleteTemplate(id);
        return Result.success(null);
    }

    /** 提交一次巡检：带同一个 clientUuid 重传只会落一条 */
    @PostMapping("/records")
    public Result<InspectRecordVO> submitRecord(@RequestBody InspectRecordSaveDTO dto) {
        requireLogin();
        return Result.success(inspectService.submitRecord(dto, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/records")
    public Result<List<InspectRecordVO>> records(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        requireLogin();
        return Result.success(inspectService.listRecords(limit));
    }

    @GetMapping("/records/{id}")
    public Result<InspectRecordVO> record(@PathVariable Long id) {
        requireLogin();
        return Result.success(inspectService.getRecord(id));
    }

    /** 异常项拍照：与待办同一个上传器，返回 /files/xxx.jpg */
    @PostMapping("/photo")
    public Result<Map<String, String>> uploadPhoto(@RequestParam("file") MultipartFile file) {
        requireLogin();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("没有收到图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("图片超过 8 MB，请重拍或压缩");
        }
        try {
            // ImgUploader 只认 png/jpg；必须把真实后缀传下去，否则 png 会被标成 image/jpeg 被拒收
            String name = file.getOriginalFilename();
            String ext = (name != null && name.toLowerCase().endsWith(".png")) ? "png" : "jpg";
            String fileName = imgUploader.upload(file.getBytes(), name, ext);
            return Result.success(Map.of("path", "/files/" + fileName, "fileName", fileName));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("图片上传失败：" + e.getMessage());
        }
    }

    private static void requireLogin() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }
    }
}
