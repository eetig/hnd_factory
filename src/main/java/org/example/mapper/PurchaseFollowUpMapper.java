package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.PurchaseFollowUp;

/**
 * 催货留痕 mapper（2026-11-18）。
 *
 * <p>只有增与查（按 tracking_id 倒序），没有改与删 —— 留痕的意义就在于不可篡改。
 */
@Mapper
public interface PurchaseFollowUpMapper extends BaseMapper<PurchaseFollowUp> {
}
