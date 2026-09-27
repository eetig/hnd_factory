package org.example.dto;

import lombok.Data;

/**
 * 工单图片对象：imageId + 图片访问路径。
 */
@Data
public class WorkOrderImageVO {
    private Long imageId;

    /** 原图：相对路径（如 /files/xxx.jpg） */
    private String url;

    /** 列表缩略图：相对路径（如 /thumbs/xxx.jpg） */
    private String thumbnailUrl;
}
