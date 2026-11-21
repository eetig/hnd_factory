package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检模板（2026-11-21，变更-255）：一份清单 = 一篇正文。
 *
 * <p>原来是「一堆检查项」，现场要逐项点正常/异常；使用方说清单他们自己已经写成一篇小作文了，
 * 照着看就行，逐项点太繁杂。于是模板只剩「标题 + 类型 + 正文」，
 * 正文按原样保存（换行即分段），手机端整篇渲染成阅读页。
 */
@Data
@TableName("inspect_template")
public class InspectTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板名，例如「车间日常巡检」；手机端列表直接显示它 */
    private String name;

    /** 车间 / 泵房 —— 只用来在手机上分组，不做权限 */
    private String kind;

    /**
     * 正文：一整篇检查内容，保留换行。
     *
     * <p>不做富文本：使用方是在别处写好的稿子整段粘进来，纯文本最稳，
     * 换行就是分段，行首写「一、二、」自然就是小标题。
     */
    private String body;

    /** 1 启用 0 停用；停用后手机端不再出现，但历史记录还留着模板名快照 */
    private Integer enabled;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
