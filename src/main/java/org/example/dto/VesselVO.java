package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 「压力容器体积计算」页的一台容器（{@code GET /api/equipment/vessels}）。
 *
 * <p>为什么另开一个 VO、不复用 {@link EquipmentLedgerVO}：那个 VO 的约定是
 * 「字段与库表一一对应、不做裁剪」，它同时服务「容器名称」输入框的候选。
 * 把内径 / 筒体长 / 底图坐标这些塞进去，会让那个查询背上一堆它用不着的字段
 * （变更-025 新增的 7 列就是为了这个页面才加的）。
 *
 * <p>本 VO 只回该页**画图与算体积**要用的东西：
 * <ul>
 *   <li>几何五件套 —— 该页按闭式解自己算，不用 {@code headVolume} / {@code volumePerMm}
 *       那套线性近似（那套在卧式罐、以及液面落在封头里时不成立）；</li>
 *   <li>{@code containerType} —— 兼作朝向：1 = 卧式，2/3 = 立式。它由 {@code spec} 派生
 *       （见 schema.sql 第 2 部分），所以新增卧式容器时 spec 里必须带「卧式」二字；</li>
 *   <li>{@code imageFile} + {@code imageBounds} —— 底图与坐标。坐标是**按图片宽度归一化**的
 *       （{@code width} 恒为 1、{@code height} 是长宽比），这样两套前端各自用不同分辨率的
 *       同一张图（电脑端用原图、uni-app 用 1/2 压图）也能共用同一组坐标；</li>
 *   <li>{@code headVolume} —— 回传库里那个**单个封头的「曲面 + 直边」**容积，**只给前端做对账**：
 *       前端比对自己按几何算出的封头容积与它，差得多就说明几何填错了。
 *       它不参与该页的体积计算。</li>
 * </ul>
 *
 * <p>{@code id} 是前端下拉的 key：名称会重名（台账里就有两行都叫「150产品罐」），id 不会。
 */
@Data
public class VesselVO {
    private Long id;
    private String name;              // 设备名称（下拉显示名）
    private String nickname;          // 设备昵称（短名，可空）—— 前端优先用它当下拉显示名
    private String code;              // 设备位号
    private String workshop;          // 车间/装置
    private Integer containerType;    // 1卧式 2平底立式 3立式 4再沸器（兼作朝向：1 或 4 = 卧式）
    private BigDecimal diameter;      // 内径(mm)
    private BigDecimal shellLength;   // 筒体直段长(mm)，不含封头直边
    private BigDecimal straightFlange;// 封头直边(mm)
    private BigDecimal topHeadDepth;  // 上/左封头曲面深度(mm)
    private BigDecimal bottomHeadDepth; // 下/右封头曲面深度(mm)；null = 平底
    private String imageFile;         // 底图文件名
    private String imageBounds;       // 底图坐标 JSON（按图片宽度归一化）
    private String medium;            // 介质
    private BigDecimal density;       // 介质密度(t/m3)，用于把体积换算成吨位
    private BigDecimal headVolume;    // 单个封头容积(m3)，仅作前端的对账基准
    private BigDecimal volume;        // 容积(m3)，源台账 V= 口径；本页只作**对照值**展示（如再沸器的铭牌全容积），不参与计算
}
