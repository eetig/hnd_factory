package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.AttendanceSetting;

/** 考勤默认时间设置（变更-171）。 */
@Mapper
public interface AttendanceSettingMapper extends BaseMapper<AttendanceSetting> {
}
