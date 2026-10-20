package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.ArchiveDocument;

/**
 * 资料台账 Mapper。
 *
 * <p>单表条件查询 + 分页，没有联查：分类名由服务层用一次全量分类表在内存里补上
 * （分类只有几十条，比 JOIN 便宜，也避免分页 + JOIN 的 count 失真）。
 */
@Mapper
public interface ArchiveDocumentMapper extends BaseMapper<ArchiveDocument> {
}
