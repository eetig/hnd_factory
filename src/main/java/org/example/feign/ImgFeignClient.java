package org.example.feign;

import org.example.dto.ImgResult;
import org.example.dto.UploadResult;
import org.example.dto.VesselDrawingResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

// name = img-service 和图片服务 spring.application.name 保持一致
@FeignClient(name = "img-service")
public interface ImgFeignClient {
    // 根据 fileName 生成 MinIO 预签名访问 URL
    @GetMapping("/api/img/getPreviewUrl")
    ImgResult<String> getPresignedUrl(@RequestParam("fileName") String fileName);

    // 删除 MinIO 图片
    @DeleteMapping("/api/img/delete")
    ImgResult<Void> delete(@RequestParam("fileName") String fileName);

    // 上传图片到 MinIO
    @PostMapping(value = "/api/img/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImgResult<UploadResult> upload(@RequestPart("file") MultipartFile file);

    /**
     * 上传容器底图：img-service 会**一张进、两张出**（白纸版 + 亮线版，深浅两个主题各一版）。
     *
     * <p>与上面的 {@code /upload} 分开：那个回一个 URL 供单据图片直接显示，
     * 这个回两个文件名、且文件名要满足前端的 {@code -dark} 后缀约定，产物形状根本不同。
     */
    @PostMapping(value = "/api/img/vessel-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImgResult<VesselDrawingResult> vesselUpload(@RequestPart("file") MultipartFile file);
}
