package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.WorkOrder;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
}
