package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.ArchiveCategory;

/**
 * 资料分类 Mapper。
 *
 * <p>只有单表条件查询（分类树一次性全取，行数几十条），所以没有 XML，
 * 条件走 {@code LambdaQueryWrapper}（与 PurchaseTrackingMapper 一致）。
 */
@Mapper
public interface ArchiveCategoryMapper extends BaseMapper<ArchiveCategory> {
}
