package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页列表统一返回体：{@code {success, msg, dataList, total, pageNum, pageSize}}。
 *
 * <p>字段名沿用既有列表接口的 {@code dataList} / {@code success} / {@code msg}（与
 * {@link ExcelResult} 一致），这样两个前端里读 {@code res.data.dataList} 的地方
 * 一行都不用改 —— 本次改造要动的是「数据从哪来」，不是「前端怎么读」。
 *
 * <p>为什么不直接给 {@code ExcelResult} 加字段：那个类是
 * {@code hnd_factory ↔ excel-import-service} 的**跨服务契约**（由 shared-contract 定义，
 * 语义是「Excel 解析结果」，totalRow / successRow 指的是「解析出的行数与成功行数」）。
 * 往里塞 pageNum 会把它的语义搅浑，且改它就是改跨服务契约。所以分页体留在这里。
 */
@Data
public class PageResult<T> {

    private Boolean success;
    private String msg;
    /** 当前页数据 */
    private List<T> dataList;
    /** **过滤后**的总条数（不是库里总行数）—— 前端分页器要靠它算总页数 */
    private Integer total;
    /** 当前页码（已归一化） */
    private Integer pageNum;
    /** 每页条数（已归一化） */
    private Integer pageSize;

    /**
     * 成功返回。
     *
     * @param dataList 当前页数据
     * @param total    过滤后的总条数
     */
    public static <T> PageResult<T> of(List<T> dataList, long total, int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setSuccess(true);
        result.setMsg("查询成功");
        result.setDataList(dataList);
        result.setTotal((int) total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    /** 空页（查询条件无匹配，或上游提前返回） */
    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return of(List.of(), 0L, pageNum, pageSize);
    }

    /** 失败。与其它接口一致：业务失败也是 HTTP 200 + {@code success:false}，由前端自判 */
    public static <T> PageResult<T> fail(String msg, int pageNum, int pageSize) {
        PageResult<T> result = empty(pageNum, pageSize);
        result.setSuccess(false);
        result.setMsg(msg);
        return result;
    }
}
