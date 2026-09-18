package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("material_pick")
public class MaterialPick {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String pickNo;             // 领料单号 PICK20260806001
    private LocalDateTime pickDate;    // 领料时间
    private String materialName;       // 物料名称
    private Double pickWeight;         // 领料重量
    private String remark;             // 备注
    // 注意：这里**没有图片字段**，沿用方案B，图片交给hnd_file
}
