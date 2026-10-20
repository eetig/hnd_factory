package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 液位记录录入弹层的**候选字典**（2026-10-20，变更-129）。
 *
 * <p>为什么单独开一个接口，而不是让前端自己拉列表再摘字段：
 * 列表接口按日期分页，候选恰恰要"全库出现过的值"，翻页拼不出来；
 * 而这两个字典都是 `SELECT DISTINCT` 一列，一次往返就够，前端也不必自己拼规则。
 *
 * <p>两个列表都按中文习惯排序（后端 Collator）：库里按码点排是「三 &lt; 前 &lt; 反」，
 * 人扫列表时看不出规律。空值与前后空白已在服务端剔除。
 */
@Data
public class TankLevelSuggestionsVO {
    /** 属地：库里出现过的值（前端再与固定罐组字典取并集） */
    private List<String> locations;
    /** 容器名称：库里出现过的值（前端再与设备台账候选合并） */
    private List<String> tankNames;
}
