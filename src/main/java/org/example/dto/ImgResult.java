package org.example.dto;

import lombok.Data;

/**
 * 镜像 img-service 的统一返回体 Result<T>（字段名保持一致，供 Feign 反序列化）。
 */
@Data
public class ImgResult<T> {
    private int code;
    private String message;
    private T data;
}
