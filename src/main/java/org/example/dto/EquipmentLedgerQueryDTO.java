package org.example.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「设备数据维护」页的查询条件：关键字 + 只看启用 + 分页。
 *
 * <p>⚠️ 这两个筛选项原先在**前端**做（面板注释写着「搜索在前端做（台账 92 行一次拿全）——
 * 现场网络未必好，敲一个字打一次接口会很难用」）。2026-10-10 的统一整改把这条
 * 「前端全量 + 本地搜索」一并否掉了：搜索搬到服务端后，**面板必须改成
 * 「回车 / 键盘搜索键才发请求」**，不能逐字实时过滤 —— 那正是当初担心的「敲一个字打一次接口」。
 * 储罐液位页早就是「回车才发请求」的做法，照它办。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentLedgerQueryDTO extends PageQueryDTO {

    /** 关键字：模糊匹配 位号 / 名称 / 昵称 / 规格 / 车间，空白表示不限 */
    private String keyword;

    /**
     * 只看启用的设备；不传或 false 表示不限制（**停用的行默认也要返回** ——
     * 维护页要能重新启用它们，见 EquipmentLedgerServiceImpl 的同名字段说明）。
     */
    private Boolean onlyEnabled;
}
