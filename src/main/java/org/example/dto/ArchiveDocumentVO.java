package org.example.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资料台账的一行（页面形状）。
 *
 * <p>与实体一一对应，另加三个<b>派生列</b>：
 * <ul>
 *   <li>{@code categoryName} —— 分类名（服务层用一次全量分类表补齐，不做 JOIN）；</li>
 *   <li>{@code hasFile} —— 有没有电子件（前端据此决定显示"下载"还是"仅纸质"）；</li>
 *   <li>{@code daysToExpire} —— 距有效期还有几天（负数 = 已过期，null = 无有效期）。</li>
 * </ul>
 *
 * <p>⚠️ {@code daysToExpire} 由后端算，前端**不要自己算**：超期/临期的口径
 * （"今天"按哪个时区、过期了算不算临期）只写在一处，两边各算一份迟早不一致。
 */
@Data
public class ArchiveDocumentVO {

    private Long id;
    private String name;
    private Long categoryId;
    /** 分类名（派生） */
    private String categoryName;

    /** 裸对象名；空串 = 只有纸质 */
    private String fileName;
    private String origName;
    private Long fileSize;
    private String mimeType;

    private LocalDate expireDate;
    /** 距有效期还有几天：负数 = 已过期；null = 无有效期（派生） */
    private Long daysToExpire;

    private String originPlace;
    private String docNo;
    private String owner;
    private String remark;

    /** 有没有电子件（派生） */
    private Boolean hasFile;

    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
