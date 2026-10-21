package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.dto.Result;
import org.example.exception.BusinessException;
import org.example.service.OcrProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片识别（myocr）的**服务端转发**端点（2026-10-21，变更-137）。
 *
 * <p>为什么不让前端直接调 {@code /api/ocr/recognize}：公网 443 前面还有一道 relay，
 * 它的 {@code /api/ocr} 上游是死的（实测 502）。而 {@code /api/**} 的其余路径 relay 会
 * 原样转发到本站点，所以由本服务再去访问 myocr（容器名 {@code http://myocr:8085}）。
 *
 * <p>为什么是 {@code /api/ocr-proxy/} 而不是复用 {@code /api/ocr/}：后者在 relay 那层
 * 已经被劫走，永远到不了这里。这个是**新前缀**，relay 不认识 ⇒ 按 {@code /api} 通配转发。
 *
 * <p>权限：写操作闸门（管理员）。识别要花腾讯云的钱，不给匿名用户用。
 */
@RestController
@RequestMapping("/api/ocr-proxy")
public class OcrProxyController {

    @Autowired
    private OcrProxyService ocrProxyService;

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<JsonNode> recognize(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "fileName", required = false) String fileName) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "没有收到图片");
        }
        try {
            return Result.success(ocrProxyService.recognize(file.getBytes(),
                    fileName == null || fileName.isBlank() ? file.getOriginalFilename() : fileName));
        } catch (BusinessException e) {
            return Result.error(400, e.getMessage());
        } catch (IOException e) {
            return Result.error(400, "读取上传图片失败");
        }
    }
}
