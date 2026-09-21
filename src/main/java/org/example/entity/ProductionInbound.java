package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_inbound")
public class ProductionInbound {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer seqNo;           // 序号
    private String documentNo;       // 单据号（业务唯一键）
    private LocalDate inboundDate;   // 日期
    private String materialName;     // 物料名称
    private String materialCode;     // 物料编码
    private BigDecimal inboundQty;   // 入库数量
    private String unit;             // 单位
    private String fileName;         // 单据图片文件名（Excel 内嵌图上传 MinIO 后的名字）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
