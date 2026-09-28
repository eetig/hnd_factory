package org.example.component;

import org.example.dto.ImgResult;
import org.example.dto.UploadResult;
import org.example.feign.ImgFeignClient;
import org.example.util.InMemoryMultipartFile;
import org.example.util.WpsCellImageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 单据图片上传 —— 抽出来给「Excel 导入」与「图片识别确认」共用。
 *
 * <p>两处都要把单据图片传到 img-service 并拿回 fileName，规则一致（后缀白名单、文件名清洗、
 * 失败即抛），各写一份迟早会出现一处能传一处传不了。
 */
@Component
public class ImgUploader {

    @Autowired
    private ImgFeignClient imgFeignClient;

    /**
     * 上传 xlsx 内嵌单据图。
     *
     * @param img 从 xlsx 里提取出的图片
     * @return img-service 返回的 fileName（存进记录的 file_name 列）
     */
    public String upload(WpsCellImageUtil.ExtractedImage img) {
        return upload(img.getData(), img.getOriginalName(), img.getExtension());
    }

    /**
     * 上传一张图片。
     *
     * @param data         图片字节
     * @param originalName 原始文件名，仅用于生成可读的名字；可为空
     * @param extension    扩展名，非 png 一律按 jpg 提交
     * @return img-service 返回的 fileName
     */
    public String upload(byte[] data, String originalName, String extension) {
        // img-service 只接受 jpg/png，jpeg 统一按 jpg 提交（格式相同）
        String ext = "png".equalsIgnoreCase(extension) ? "png" : "jpg";
        String contentType = "png".equals(ext) ? "image/png" : "image/jpeg";

        String base = StringUtils.hasText(originalName) ? originalName : "document";
        // 去掉原始名里可能带的后缀，统一追加白名单后缀
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\.[A-Za-z0-9]+$", "");

        MultipartFile file = new InMemoryMultipartFile("file", base + "." + ext, contentType, data);
        ImgResult<UploadResult> res = imgFeignClient.upload(file);
        if (res == null || res.getCode() != 200 || res.getData() == null) {
            throw new IllegalStateException("img-service 返回异常");
        }
        return res.getData().getFileName();
    }
}
