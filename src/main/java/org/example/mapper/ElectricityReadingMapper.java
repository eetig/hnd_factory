package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.ElectricityReading;

/** 电表抄表记录（2026-11-02） */
@Mapper
public interface ElectricityReadingMapper extends BaseMapper<ElectricityReading> {
}
