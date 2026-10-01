package org.example.service;

import org.example.dto.MaterialMatchVO;
import org.example.dto.MaterialVO;
import org.example.entity.MaterialMaster;

import java.util.List;
import java.util.Map;

/**
 * 物料主数据查询（图片解析辅助录入用）。
 *
 * <p>只提供「查」，不提供「改」：主数据一次性导入后手工维护（见 docs/schema.sql），
 * 本期不做管理接口。
 */
public interface MaterialMasterService {

    /**
     * 按识别出的物料名称匹配编码。
     *
     * <p>归一化后<b>全等且唯一命中</b>才返回 matched；查不到或多条命中时 matched 为 null，
     * 同时给出候选供人工选择。绝不为了「提高命中率」放宽到模糊匹配 ——
     * 主数据里 271 对名称互为子串，模糊匹配会把「电石」判成「电石渣」。
     */
    MaterialMatchVO match(String name);

    /**
     * 人工检索候选（前端编码单元格的搜索框用）。
     *
     * @param keyword 关键词；按名称与规格匹配
     * @param limit   返回条数上限；null 或非正数时取默认值
     */
    List<MaterialVO> search(String keyword, Integer limit);

    /**
     * 按物料编码取主数据（页面「物料查询」按编码联查名称与规格用）。
     *
     * <p>同一编码在主数据里可能对应多个名称（唯一键是「编码 + 名称」，源数据里有一对多），
     * 这种只取一条：优先 enabled=1，其次 id 最小的 —— 只求和「确定性」，
     * 页面上只需要一个能看的名字。
     *
     * <p>与 {@link #match} / {@link #search} 不同，这里**不过滤停用行**：
     * 停用的物料仍然存在于主数据里，页面按编码查到时该显示它的名字，
     * 显示成「没有这个物料」会让人以为主数据缺了它。
     */
    Map<String, MaterialMaster> mapFirstByCode();
}
