package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("material_movement")
public class MaterialMovement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;          // 生产工单号，关联 work_order.order_no
    private String materialCode;     // 物料编码
    private String materialDesc;     // 物料描述
    private Integer movementItem;    // 货物移动项目行号
    private Integer materialDocItem; // 物料文档项目号
    private String batchNo;          // 批次号
    private String storageLocation;  // 存储地点，如5001/7101/5004
    private String unit;             // 基本计量单位 KG
    private BigDecimal quantity;     // 数量(带符号)：正数=入库，负数=出库/消耗
    private String movementType;     // 移动类型：261=领料消耗,262=退料,101=成品入库
    private String materialDoc;      // SAP物料凭证号，如4903520829
    private String creditFlag;       // 借/贷标记：H=借方(入库), S=贷方(消耗)
    private LocalDate postingDate;   // 过账日期
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
