package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 储罐（容器实例）—— 库表 {@code equipment_tank}，见 docs/schema.sql（2026-11-09）。
 *
 * <h2>它和 equipment_ledger 的分工</h2>
 * {@link EquipmentLedger} 是**图纸**：几何、型号、封头参数的唯一来源，由使用方自行维护。
 * 这张表是车间里**真实买回来的罐**：同一张图纸可以有多个罐（150产品罐 A/B），
 * 每个罐装什么料、在哪个罐区、属产品还是原料，各不相同。
 *
 * <p>所以：
 * <ul>
 *   <li>{@code blueprintId} 是**手工指定**的 —— 使用方原话「容器我来指定哪张图纸」。
 *       绝不做按名称自动匹配：配错了没人发现，比空着更糟。</li>
 *   <li>几何（内径/筒长/封头）**不在本表冗余**，一律从图纸只读带出。
 *       冗余一份迟早会和图纸打架。</li>
 *   <li>{@code density} 放在罐上，因为物料随罐走（图纸上的密度只是设计介质）。</li>
 * </ul>
 */
@Data
@TableName("equipment_tank")
public class EquipmentTank {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 罐号/位号（现场叫法，如 1#1L、150-3） */
    private String tankCode;

    /** 罐名（如 150产品罐 A） */
    private String tankName;

    /** 绑定的图纸（equipment_ledger.id） */
    private Long blueprintId;

    /** 车间/装置 */
    private String workshop;

    /** 属地/罐区（1#1L、2#2L、包装车间…） */
    private String location;

    /** 所属：产品 / 原料 */
    private String category;

    private String materialCode;

    /** 罐里实际装的料（可能和图纸介质不同） */
    private String materialName;

    /** 介质密度(t/m3)；空 = 只算体积、算不了质量 */
    private BigDecimal density;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
