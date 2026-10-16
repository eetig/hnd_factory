package org.example.component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 图片对外 URL 的**唯一拼接处**。
 *
 * <p>原先 5 个 Service（{@code WorkOrderServiceImpl}、{@code WorkOrderImageServiceImpl}、
 * {@code ProductionInboundServiceImpl}、{@code MaterialPickSummaryServiceImpl}、
 * {@code TankLevelImageServiceImpl}）各写一份 {@code toUrl} / {@code toThumbUrl}，方法体
 * 逐字节相同，并各带一份 {@code @Value("${image.base-url:}")}。契约一旦变（换 CDN、改成
 * 对象存储签名 URL、加版本号），必须 5 处同时改 —— 留 5 份不带来任何局部性。
 *
 * <p><b>为什么是固定拼接而不是预签名 URL</b>（变更-001 的结论；这条理由原先散在 5 处注释里）：
 * 预签名 URL 每次调用都带新的 {@code X-Amz-Date}，而浏览器按 URL 缓存 —— URL 一变整页图片
 * 重下；且列表组装时会对每张图发一次远程调用。
 *
 * <p>{@code image.base-url} 留空表示与前端同源部署，前端拿到 {@code /files/xxx} 相对路径（契约 2.3）。
 */
@Component
public class ImageUrlBuilder {

    private final String imageBaseUrl;

    public ImageUrlBuilder(@Value("${image.base-url:}") String imageBaseUrl) {
        this.imageBaseUrl = imageBaseUrl;
    }

    /** 原图 URL；文件名为空（该行没有图）返回 null，由前端按 null 处理 */
    public String urlOf(String fileName) {
        return StringUtils.hasText(fileName) ? imageBaseUrl + "/files/" + fileName : null;
    }

    /** 缩略图 URL；同一契约下的 {@code /thumbs/} 前缀 */
    public String thumbUrlOf(String fileName) {
        return StringUtils.hasText(fileName) ? imageBaseUrl + "/thumbs/" + fileName : null;
    }
}
