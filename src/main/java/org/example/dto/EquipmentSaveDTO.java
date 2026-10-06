package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 「设备数据维护」页的保存入参（{@code POST /api/equipment/ledger/save}）。
 *
 * <p><b>这个类本身就是字段白名单</b>：只放使用方要维护的那 15 项，其余列（`source_sheet` /
 * `source_row` / `image_bounds` / `head_volume` / `mass_per_mm` / `thickness` / 时间戳…）
 * 压根不在类型里，调用方想传也传不进来 —— 比在 service 里逐个 if 判断可靠，
 * 也不会因为以后往实体加字段而意外放开。
 *
 * <p>几个刻意的取舍：
 * <ul>
 *   <li>{@code id} 为空 = 新增，非空 = 更新。不给单独的 create/update 两个接口 ——
 *       页面是同一个弹窗两种入口，分开反而要在两处各写一遍字段搬运。</li>
 *   <li>**没有 `enabled`**：停用/启用走另一个接口（{@code /ledger/enable}），
 *       免得「保存表单」顺手把停用状态改了。</li>
 *   <li>{@code containerType} **可以改**（使用方明确要求）。它本来是由 spec 派生的输出列，
 *       但 2026-10-06 起该列可空、且 schema.sql 的回填只填 NULL 的行 —— 所以人工设的值
 *       会被保留、不会被下次重跑刷回去。这也意味着「改了型式」不必连 spec 一起改。</li>
 * </ul>
 */
@Data
public class EquipmentSaveDTO {
    /** null = 新增 */
    private Long id;

    private String equipmentCode;     // 设备位号
    private String equipmentName;     // 设备名称
    private String nickname;          // 设备昵称
    private String workshop;          // 车间/装置
    private String spec;              // 设备规格
    private Integer containerType;    // 容器类型 1卧式 2平底立式 3立式 4再沸器（可人工改，见类注释）

    private BigDecimal innerDiameter;   // 内径(mm)
    private BigDecimal shellLength;     // 筒体直段长(mm)，不含封头直边
    private BigDecimal straightFlange;  // 封头直边(mm)
    private BigDecimal topHeadDepth;    // 上/左封头曲面深度(mm)
    private BigDecimal bottomHeadDepth; // 下/右封头曲面深度(mm)；null = 平底

    private BigDecimal volumePerMm;   // 每毫米液位对应的体积(m3/mm)
    private BigDecimal density;       // 介质密度(t/m3)
    private String medium;            // 介质
    private String remark;            // 备注

    private String imageFile;         // 容器底图：内置底图写文件名，上传的写 /files/xxx.png
}
