package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 月底储罐液位记录的图据（库表 tank_level_image，见 docs/schema.sql）。
 *
 * <p>一条记录可挂多张：月底抄表时一个容器往往要拍好几张（不同罐面、仪表读数）。
 * 变更-008 时图据是 {@code tank_level_record.file_name} 单列，一次只能存一张，
 * 本表把它取代掉。
 *
 * <p>只存文件名，对外的 /files、/thumbs 路径由 service 拼 —— 库里存 URL 的话，
 * 换域名或换存储都要刷数据（契约 2.3）。
 */
@Data
@TableName("tank_level_image")
public class TankLevelImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;       // 所属液位记录 id（tank_level_record.id）
    private String fileName;     // MinIO 文件名
    private LocalDateTime createTime;
}
