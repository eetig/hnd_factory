package org.example.dto;

import lombok.Data;

/**
 * file-service 的上传结果镜像（字段与 {@code org.example.dto.FileUploadResult} 一一对应）。
 *
 * <p>为什么要镜像一份：Feign 反序列化需要本服务自己的类型，跨服务不能直接引对方的 jar
 * （同一套做法见 {@link UploadResult} / {@link VesselDrawingResult}）。
 */
@Data
public class ArchiveFileResult {

    /** 裸对象名：{@code <32hex>.<ext>}，不含 archive/ 前缀 */
    private String fileName;
    /** 上传时的原文件名 */
    private String origName;
    private Long size;
    /** 服务端判定的 Content-Type（按扩展名，不信客户端声明） */
    private String contentType;
}
