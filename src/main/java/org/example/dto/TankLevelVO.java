package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 月底储罐液位记录项（前端列表一行）。
 *
 * <p>图据是**数组**（变更-011）：一条记录可挂多张现场照片。列表用第一张的缩略图
 * 加一个数量角标，点开弹窗看全部。每张仍是「双字段」契约（变更-001）——
 * {@code thumbnailUrl} 供小图、{@code url} 供大图与回退。
 *
 * <p>没有图据时 {@code images} 是空数组而不是 null：前端处处要判 `length`，
 * 多一个 null 分支就多一处漏判。
 */
@Data
public class TankLevelVO {
    private Long id;
    private LocalDate recordDate;          // 记录日期
    private String location;               // 属地
    private String category;               // 所属：产品 / 原料
    private String materialCode;           // 物料编码
    private String materialName;           // 物料
    private String tankName;               // 容器名称
    private String tankCode;               // 容器编号(设备位号)
    private BigDecimal levelValue;         // 容器液位
    private BigDecimal theoreticalWeight;  // 理论质量(KG)
    private List<TankLevelImageVO> images; // 图据（可多张，按上传顺序）
}
