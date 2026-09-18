package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;          // 订单号
    private String storageTank;      // 储罐号
    private String plantCode;        // 工厂代码
    private String materialCode;     // 物料编码
    private String materialDesc;     // 物料描述
    private String teamName;         // 班组名称
    private String customerBrand;    // 客户牌号
    private String orderType;        // 订单类型
    private String mrpController;    // MRP控制员
    private Integer producerCount;   // 生产主人员数
    private String batchNo;          // 批次号
    private BigDecimal orderQty;     // 订单数量(KG)
    private String unit;             // 计量单位
    private String prodVersion;      // 生产版本
    private LocalDate planStartDate; // 基本开始日期
    private LocalDate planFinishDate;// 基本完成日期
    private BigDecimal confirmedQty; // 确认的产量(KG)
    private BigDecimal confQty;      // 确认产量CONF_
    private BigDecimal deliveredQty; // 已交货数量GMPS
    private LocalDate actualFinishDate; // 实际完成日期
    private String lastChangedBy;    // 最后更改人
    private String sysStatus;        // 系统状态
    private String storageTankName;  // 储罐名称
    private LocalDate changeDate;    // 更改日期
    private LocalDateTime changeTime;// 更改时间
    private BigDecimal perBarrelWeight; // 每桶重量AMEIN
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
