package org.example.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资料台账的一行。
 *
 * <p>必填只有「名称 / 分类 / 文件（或纸质）」三项，其余全选填 —— 必填一多就没人录
 * （变更-085 那次 92 行空值的教训）。而「有效期」与「原件在哪」必须**存在**：
 * 它们是检查时最常被问的两句（开工方案的 Q4）。
 *
 * <p>{@code fileName} 存的是 <b>裸对象名</b>（{@code <32hex>.<ext>}），不带 {@code archive/}
 * 前缀 —— 前缀是 file-service 的实现细节。空串 = 只有纸质、没有电子件。
 *
 * <p>删除是<b>软删</b>（{@code deleted = 1}）：只有 admin 能删，且文件不物理删 ——
 * 防的是"手滑把证照删了"。
 *
 * <p>⚠️ 可编辑字段全部 {@code updateStrategy = ALWAYS}（ADR-0002 整行覆写）：把"有效期"清空
 * 就得真的写 NULL，否则界面显示没了、库里还留着旧日期，到期提醒会照旧报。
 */
@Data
@TableName("archive_document")
public class ArchiveDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资料名称（必填） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String name;

    /** 分类 id（必填） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;

    /** 裸对象名；空串 = 只有纸质 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fileName;

    /** 上传时的原文件名（下载时还原） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String origName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long fileSize;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mimeType;

    /** 有效期至；NULL = 无有效期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expireDate;

    /** 纸质原件在哪：部门/柜子/责任人 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String originPlace;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String owner;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 软删：1 = 已删。由服务设置，不经表单，所以不标 ALWAYS */
    private Integer deleted;

    /** 录入人 */
    private String createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
