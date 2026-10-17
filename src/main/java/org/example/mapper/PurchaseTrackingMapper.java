package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.entity.PurchaseTracking;

/**
 * 采购需求详情 Mapper。
 *
 * <p>本表当前只有单表条件查询（不需要联查物料主数据 —— 名称与编码在这张表里是快照文本，
 * 这正是「采购记录要能独立读懂」那条设计的直接结果），所以没有 XML，
 * 条件都走 {@code LambdaQueryWrapper}（与 EquipmentLedgerService 的做法一致）。
 */
@Mapper
public interface PurchaseTrackingMapper extends BaseMapper<PurchaseTracking> {
}
