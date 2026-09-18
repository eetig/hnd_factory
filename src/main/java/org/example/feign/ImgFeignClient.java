package org.example.feign;

import org.example.dto.ImgResult;
import org.example.dto.UploadResult;
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
}
