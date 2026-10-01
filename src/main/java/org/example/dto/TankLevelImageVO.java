package org.example.dto;

import lombok.Data;

/**
 * 储罐液位记录的图据对象：imageId + 图片访问路径。
 *
 * <p>字段与 WorkOrderImageVO 保持一致（imageId / url / thumbnailUrl），
 * 前端两处的图片弹窗共用同一个展示组件，字段名一致才能直接喂进去。
 */
@Data
public class TankLevelImageVO {
    private Long imageId;

    /** 原图：相对路径（如 /files/xxx.jpg） */
    private String url;

    /** 列表缩略图：相对路径（如 /thumbs/xxx.jpg） */
    private String thumbnailUrl;
}
