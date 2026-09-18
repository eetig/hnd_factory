package org.example.dto;

import lombok.Data;

/**
 * 镜像 img-service 的 UploadResult（上传返回 url/fileName/size）。
 */
@Data
public class UploadResult {
    private String url;
    private String fileName;
    private long size;
}
