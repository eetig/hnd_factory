package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物料主数据（图片解析辅助录入用：按物料名称反查编码与单位）。
 *
 * <p><b>编码与名称各自都不唯一</b>：源数据里 114002501 同时对应两个不同物料，
 * 外贸包材也存在多个编码共用同一名称。故唯一键是二者的组合，见 docs/schema.sql。
 */
@Data
@TableName("material_master")
public class MaterialMaster {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialCode;     // 物料编码
    private String materialName;     // 物料名称（识别结果按此字段反查）
    private String spec;             // 规格
    private String unit;             // 单位（查到时自动带出）
    private String materialType;     // 物料类型
    private String materialGroup;    // 物料组
    private String productGroup;     // 产品组
    private String sourceSheet;      // 来源工作表（可追溯）
    private Integer sourceRow;       // 来源行号（可追溯）
    private Integer enabled;         // 是否启用 1启用 0停用（停用不参与匹配）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
