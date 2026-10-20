package org.example.feign;

import org.example.dto.ArchiveFileResult;
import org.example.dto.ImgResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务（file-service，原 img-service）的**原样存取**通路（变更-126）。
 *
 * <p>与 {@link ImgFeignClient} 的分工：那个走 {@code /api/img/**}，图片专用、会重编码成 PNG；
 * 这个走 {@code /api/file/**}，**字节原样**存，给资料归档用。
 *
 * <p>⚠️ {@code name} 必须与 file-service 的 {@code spring.application.name} 一致
 * —— 2026-10-20 该服务由 img-service 更名为 file-service，两个 Feign 客户端都要跟着改，
 * 漏一个就是启动即 {@code Load balancer does not contain an instance}。
 *
 * <p>与 {@link ImgFeignClient} 一样：上传一律由本服务转发（那个服务不对外暴露，
 * 也没有鉴权注解），权限位关在本服务的 Controller 上。
 */
@FeignClient(
    name = "file-service",
    /*
     * ⚠️ contextId 必须写，而且**不能**和 ImgFeignClient 相同。
     *
     * Spring Cloud OpenFeign 给每个客户端注册一个
     * `<name>.FeignClientSpecification` bean；同一个服务名有**两个**客户端而不区分
     * contextId，第二个注册时就会 BeanDefinitionOverrideException 直接把应用启不起来：
     *   "The bean 'file-service.FeignClientSpecification' could not be registered"
     *
     * 2026-10-20 改名当天就踩了这个 —— **单元测试不启动 Spring 上下文，抓不到**，
     * 只有真启动才炸。所以补了 FeignClientNameUniquenessTest 扫源码兜底。
     */
    contextId = "fileServiceArchive"
)
public interface FileFeignClient {

    /** 上传：file-service 原样存字节，回裸对象名 + 原文件名 + 大小 + Content-Type */
    @PostMapping(value = "/api/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImgResult<ArchiveFileResult> upload(@RequestPart("file") MultipartFile file);

    /**
     * 下载：拿回原始字节。
     *
     * <p>为什么直接回 {@code byte[]} 而不是转发 URL：资料是内部文件，做公共 URL
     * （或预签名 URL）等于把访问权交给"知道链接的人"；走本服务转发才能每次校验登录态
     * 与权限。代价是流量过一遍本服务 —— 资料不是高频大流量，可以接受。
     */
    @GetMapping("/api/file/{fileName}")
    byte[] download(@PathVariable("fileName") String fileName);

    /** 删除（换文件 / 清空 / 删记录时成对调用） */
    @DeleteMapping("/api/file/{fileName}")
    ImgResult<Void> delete(@PathVariable("fileName") String fileName);
}
