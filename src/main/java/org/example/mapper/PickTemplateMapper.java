package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.PickTemplate;

/**
 * 领料模板 Mapper。列表查询**不带 file_data**（见 service 里的 select 列裁剪），
 * 否则一次列表就把所有模板的字节都读上来。
 */
@Mapper
public interface PickTemplateMapper extends BaseMapper<PickTemplate> {
}
