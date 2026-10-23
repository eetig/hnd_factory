package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物料主数据。
 *
 * <p>2026-10-23（变更-159）换成 SAP 全量导出后，语义变了三处：
 * <ol>
 *   <li><b>一个编码可以有多行</b>：评估类型（罐体状态/等级）维度，主档行 {@code valuationType=''}，
 *       罐类物料另有 新/A/B/C/D/翻新/内衬 七行，价格各不相同；</li>
 *   <li>主键是独立自增 id，唯一键是 {@code (materialCode, valuationType)}；</li>
 *   <li>{@code status} 才是状态（1 正常 / 2 待停用 / 0 停用），{@code enabled} 只是兼容旧接口的派生列。</li>
 * </ol>
 */
@Data
@TableName("material_master")
public class MaterialMaster {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialCode;     // 物料编码
    private String valuationType;    // 评估类型：空=主档；A/B/C/D/内衬/新/翻新
    private String materialName;     // 物料名称（识别结果按此字段反查）
    private String nameNorm;         // 归一化名称（导入时生成，走索引反查）
    private String spec;             // 规格（SAP 导出无此列，保留兼容）
    private String unit;             // 基本计量单位
    private String materialType;     // 物料类型 Z001–Z010
    private String materialGroup;    // 物料组
    private String productGroup;     // 产品组（SAP 导出无此列，保留兼容）
    private String procurementGroup; // 采购组
    private String mrpType;          // MRP 类型 PD/M0/ND
    private String valuationClass;   // 评估类（ValCl）
    private String priceControl;     // 价格控制 V/S
    private BigDecimal price;        // 价格（按 priceUnit）
    private Integer priceUnit;       // 价格单位 1/1000
    private String currency;         // 货币
    private String plant;            // 工厂（当前恒 1503）
    private String creator;          // 创建人
    private LocalDate changedAt;     // 上次更改
    private Integer status;          // 1 正常 / 2 待停用 / 0 停用
    private Integer enabled;         // 兼容旧接口：status<>0 即 1
    private String sourceSheet;      // 来源工作表（可追溯）
    private Integer sourceRow;       // 来源行号（可追溯）
    private String sourceFile;       // 来源文件名
    private LocalDateTime importedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
