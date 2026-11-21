package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检记录（2026-11-21，变更-255）：照着一篇清单看了一遍，记一行。
 *
 * <p><b>clientUuid 是幂等的锚</b>：泵房没信号时手机端先把记录存本地，联网后重传；
 * 重传可能因为超时重试而发两次，靠这个唯一键在服务端识别「这条已经收过了」，
 * 直接返回原来那条，不会变成两条巡检。
 *
 * <p>不再存逐项结果：清单改成一篇文章后，没有「正常/异常」可点，
 * 现场信息只剩备注与照片，所以记录就是「谁、什么时候、照着哪篇、留了什么」。
 */
@Data
@TableName("inspect_record")
public class InspectRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机端生成的唯一号（uuid），可以为空（老客户端/直接调接口） */
    private String clientUuid;

    private Long templateId;

    /** 提交那一刻的模板名快照：模板后来改名或删了，历史记录也不会变成空 */
    private String templateName;

    private String kind;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String note;

    /** 现场照片 JSON：["/files/xxx.jpg"]，最多 9 张 */
    private String images;

    private LocalDateTime createdAt;
}
