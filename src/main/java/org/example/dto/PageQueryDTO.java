package org.example.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 列表查询统一入参 —— 全工程所有**分页列表**接口共用（整改 2026-10-10：
 * 列表一律「后端分页 + 后端条件查询」）。
 *
 * <p>背景：改造前所有列表都是「不带任何参数地把全表查出来返回」，筛选、排序、分页全在前端
 * 内存数组上做。后果是前端持有一份「页面打开那一刻」的快照，库里的新增/修改在筛选时永远
 * 看不到，非重启 App 不可。本类是把「分页 + 条件」搬到服务端的统一入口。
 *
 * <p>各业务查询 DTO 继承本类后追加自己的筛选字段（{@code materialName} / {@code documentNo} /
 * {@code keyword} …），不要另起一套 pageNum / pageSize 命名。
 *
 * <p>⚠️ {@code pageNum} / {@code pageSize} 标 {@code @NotNull} 是**有意的**，不要改成
 * 「缺省即第 1 页」：老版 App 的请求里没有这两个参数，给默认值会让它安静地只拿到第 1 页
 * （10 条），使用方看到的是「数据丢了」，比一个明确的 400 难排查得多。宁可响亮地失败。
 * 缺参由 {@code GlobalExceptionHandler#handleBind} 统一回 400。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageQueryDTO extends DateRangeQueryDTO {

    /** 每页条数上限：挡掉手搓 {@code pageSize=100000} 把全表拉回来的用法 */
    public static final int MAX_PAGE_SIZE = 200;

    /** 当前页码，从 1 开始 */
    @NotNull(message = "缺少分页参数 pageNum")
    private Integer pageNum;

    /** 每页条数 */
    @NotNull(message = "缺少分页参数 pageSize")
    private Integer pageSize;

    /**
     * 归一化后的页码：非法值（0 / 负数）一律当第 1 页。
     *
     * <p>不抛异常是因为这属于「调用方把页码算错了」，不是「协议用错了」——
     * 删掉某页最后一条后前端可能带着越界页码回来，回第 1 页比回 400 合理。
     */
    public int resolvedPageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /** 归一化后的每页条数：非法值取 10，超上限截到 {@link #MAX_PAGE_SIZE} */
    public int resolvedPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
