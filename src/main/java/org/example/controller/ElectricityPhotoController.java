package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.example.component.ImgUploader;
import org.example.dto.Result;
import org.example.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 抄表照片上传（2026-11-03）。
 *
 * <p>使用方要求手机抄表时「能保存图片」—— 现场对着电表拍一张，作为读数存证。
 *
 * <p>复用现有的 file-service（`ImgUploader`）：它已经承担工单图片、容器底图，
 * 再引一套存储没有意义；而且 /files/xxx 的读取路径是现成的，前端不用改。
 *
 * <p>只有登录的人能用（抄表就是登录的人在做）；管理员不额外要求 ——
 * 现场抄表的人不一定有管理权限。
 */
@RestController
@RequestMapping("/api/electricity")
public class ElectricityPhotoController {

    @Autowired
    private ImgUploader imgUploader;

    /** 单张照片上限：电表照片一般 1~3 MB，8 MB 足够，再大就是拍错东西了 */
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    @PostMapping("/photo")
    public Result<Map<String, String>> uploadPhoto(@RequestParam("file") MultipartFile file) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("没有收到图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("图片超过 8 MB，请重拍或压缩");
        }
        try {
            String fileName = imgUploader.upload(file.getBytes(), file.getOriginalFilename(), "jpg");
            // 前端存这个路径；读取时拼 /files/xxx（与工单图片一致）
            return Result.success(Map.of("path", "/files/" + fileName, "fileName", fileName));
        } catch (Exception e) {
            throw new BusinessException("图片上传失败：" + e.getMessage());
        }
    }
}
