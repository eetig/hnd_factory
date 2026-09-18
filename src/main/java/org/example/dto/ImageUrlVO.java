package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片 url 返回对象：{ url }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageUrlVO {
    private String url;
}
