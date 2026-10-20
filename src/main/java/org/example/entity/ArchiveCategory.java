package org.example.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资料分类（两级）。
 *
 * <p>{@code parentId} 为空 = 大类，否则指向大类 id。只支持两级：三级以上对"没有资料员"的厂子
 * 是负担，分类越深越没人维护（见 docs/资料归档-开工方案.md 的 Q3）。
 *
 * <p>类目**存库、使用方自己维护**，不写死在代码里 —— 各厂类目差异极大，写死必错。
 *
 * <p>⚠️ {@code parentId} 允许 NULL（大类就是没有父亲），所以它必须标
 * {@code updateStrategy = ALWAYS}：否则把子类拖成大类时，MyBatis-Plus 会跳过 null
 * 而让旧的 parent_id 留在库里，界面上显示已变成大类、实际还在某个大类下面。
 */
@Data
@TableName("archive_category")
public class ArchiveCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer sortNo;

    private LocalDateTime createTime;
}
