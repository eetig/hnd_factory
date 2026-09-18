package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order_image")
public class WorkOrderImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;      // 工单号（唯一，一张工单一张图）
    private String fileName;     // MinIO 文件名
    private LocalDateTime createTime;
}
