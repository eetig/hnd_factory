package org.example.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 资料分类树节点（两级）。
 *
 * <p>{@code children} 只有大类才有内容；子类的 {@code children} 恒为空 List（不返回 null，
 * 前端可以无脑 {@code v-for}，省一处判空）。
 */
@Data
public class ArchiveCategoryVO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sortNo;

    /** 该分类下（含子类）未删除的资料条数 —— 列表页左树要显示的数字，也是"能不能删"的依据 */
    private Long documentCount;

    private List<ArchiveCategoryVO> children = new ArrayList<>();
}
