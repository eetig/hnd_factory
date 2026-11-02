package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.ElectricityMeter;

/** 电表台账（2026-11-02） */
@Mapper
public interface ElectricityMeterMapper extends BaseMapper<ElectricityMeter> {
}
