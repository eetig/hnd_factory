package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.dto.MaterialStockVO;
import org.example.entity.MaterialStock;

import java.util.List;

@Mapper
public interface MaterialStockMapper extends BaseMapper<MaterialStock> {

    /**
     * 「物料查询」页的分页查询（双源合并，见 resources/mapper/MaterialStockMapper.xml）。
     *
     * <p>⚠️ 这里**刻意不用 MyBatis-Plus 的 {@code selectPage}**，而是自己传
     * {@code offset} / {@code size}，并且配一个显式的 {@link #countStock}：
     * 本查询是 {@code UNION ALL} 出来的派生表，分页插件为它自动生成的 count SQL
     * 是否被正确包成 {@code SELECT COUNT(*) FROM (...)} 依赖它的 SQL 解析结果 ——
     * 一旦解析成「把 count 塞进 union 的某一支」，返回的 total 就会错，
     * 而错的 total 只会表现成「分页器页数不对」，极难往这里想。显式写死两个查询没有这个不确定性。
     *
     * @param keyword     关键字（编码 / 名称 / 规格），null 或空串表示不限
     * @param onlyInStock true 时只返回 {@code stock_qty > 0} 的行
     * @param offset      跳过条数
     * @param size        本页条数
     */
    List<MaterialStockVO> selectStockPage(@Param("keyword") String keyword,
                                          @Param("onlyInStock") boolean onlyInStock,
                                          @Param("offset") long offset,
                                          @Param("size") long size);

    /** 与 {@link #selectStockPage} 同一套筛选条件命中的**总条数**（不是库里总行数） */
    long countStock(@Param("keyword") String keyword,
                    @Param("onlyInStock") boolean onlyInStock);
}
