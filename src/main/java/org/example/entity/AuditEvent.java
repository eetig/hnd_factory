package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条审计留痕（变更-129，对应 docs/schema.sql 第 24 步的 audit_event 表）。
 *
 * <p>⚠️ 这里**绝不存参数正文** —— 只存 {@code paramsDigest}（摘要）。资料内容、密码类字段
 * 一旦进了审计表就等于长期明文留存，而且再没人会去清理。
 */
@Data
@TableName("audit_event")
public class AuditEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** register / login / logout / write_denied / write_ok / locked_clicked */
    private String event;

    /** 登录名；匿名留空串 */
    private String username;

    private String ip;

    private String userAgent;

    private String method;

    private String path;

    /** 表名或文件前缀，例如 archive/ */
    private String target;

    /** 参数摘要（sha256 前 16 位），不存正文 */
    private String paramsDigest;

    /** ok / denied */
    private String result;

    private LocalDateTime createdAt;
}