package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.AuditEvent;

/** 审计留痕的写入与查询（变更-129） */
@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEvent> {
}