package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料库存汇总（SAP 库存导出）。
 *
 * <p>业务唯一键：(工厂 + 物料编码 + 存储地点)。源文件里同一物料常有多行
 * （硅粉 111001785 会出现 6,000 / 9,482 / 34,000 三行，其它列完全相同），
 * 导入时按这个键把数量**相加**存成一条。
 *
 * <p>再次导入视为「当前库存快照」：先按本次涉及的工厂把 stock_qty 清零
 * （行保留，物料信息还能查到），再写入本次的行 —— 详见 MaterialStockServiceImpl#saveImported。
 */
@Data
@TableName("material_stock")
public class MaterialStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String plantCode;        // 工厂，如 1503
    private String materialCode;     // 物料，如 111001785
    private String materialDesc;     // 物料描述，如 硅粉
    private String spec;             // 规格型号，如 500KG/袋
    private String storageLocation;  // 存储地点，如 1001
    private String storageDesc;      // 存储地点描述，如 原材料仓
    private String unit;             // 基本计量单位，KG
    private BigDecimal stockQty;     // 非限制使用的库存（同键多行相加）
    private LocalDateTime lastImportTime;  // 最近一次被导入的时间
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
