package org.example.controller;

import org.example.audit.AuditRecord;
import org.example.audit.AuditRecorder;
import org.example.audit.RequestMetadata;
import org.example.constant.AuditEventType;
import org.example.dto.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前端"点了锁定功能"的留痕入口（变更-129）。
 *
 * <p>为什么要单独一个端点：游客点的是**只读页面的导航项**，那不是一个写请求，
 * 拦截器看不到；但它恰恰是这个功能最想留下的证据（谁在什么时候想用哪个功能）。
 *
 * <p>**前端每会话每功能只调一次**（sessionStorage 去重），否则一次乱点就刷出几十条。
 * 这个端点本身在 WRITE_WHITELIST 里 —— 游客必须能调它，否则就记不下来。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private AuditRecorder auditRecorder;

    @PostMapping("/locked-click")
    public Result<Void> lockedClick(@RequestBody(required = false) Map<String, String> body) {
        String pageKey = body == null ? "" : String.valueOf(body.getOrDefault("pageKey", ""));
        auditRecorder.record(AuditRecord.builder()
                .event(AuditEventType.LOCKED_CLICKED)
                .username(RequestMetadata.username())
                .ip(RequestMetadata.currentIp())
                .userAgent(RequestMetadata.currentUserAgent())
                .method("POST")
                .path("/api/audit/locked-click")
                .target(pageKey.length() > 120 ? pageKey.substring(0, 120) : pageKey)
                .paramsDigest("")
                .result("denied")
                .build());
        return Result.success(null);
    }
}