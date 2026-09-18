package org.example.dto;

import lombok.Data;

/**
 * 工单图片对象：imageId + 预签名访问 url。
 */
@Data
public class WorkOrderImageVO {
    private Long imageId;
    private String url;
}
