package org.example.audit;

import org.example.constant.AuditEventType;
import org.example.entity.AuditEvent;
import org.example.mapper.AuditEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计留痕的落库入口（变更-129）。
 *
 * <p><b>为什么走事件 + @Async</b>：审计写失败**绝不能连累业务** —— 业务成功而审计丢了，
 * 记一条日志补；业务因为审计写不进去而失败，是绝对不行的。所以调用方只 publish 一个事件，
 * 真正的 INSERT 在别的线程里做，异常在这里被吞掉并打日志。
 */
@Component
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private AuditEventMapper auditEventMapper;

    /** 调用方用这个：立即返回，不阻塞请求 */
    public void record(AuditRecord record) {
        if (record == null) {
            return;
        }
        publisher.publishEvent(record);
    }

    /** 真正的落库。异常只打日志 —— 审计永远不该让业务失败。 */
    @Async
    @EventListener
    public void onRecord(AuditRecord record) {
        try {
            AuditEvent entity = new AuditEvent();
            entity.setEvent(record.event());
            entity.setUsername(record.username() == null ? "" : record.username());
            entity.setIp(record.ip() == null ? "" : record.ip());
            entity.setUserAgent(truncate(record.userAgent(), 255));
            entity.setMethod(record.method() == null ? "" : record.method());
            entity.setPath(truncate(record.path(), 255));
            entity.setTarget(truncate(record.target(), 128));
            entity.setParamsDigest(record.paramsDigest() == null ? "" : record.paramsDigest());
            entity.setResult(record.result() == null ? "" : record.result());
            entity.setCreatedAt(LocalDateTime.now());
            auditEventMapper.insert(entity);
        } catch (Exception e) {
            // 审计失败只留日志；注意不要把 paramsDigest 之外的内容打出来
            log.warn("审计留痕写入失败（不影响业务）：event={} path={} err={}",
                    record.event(), record.path(), e.toString());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 便捷方法：注册 / 登录 / 退出这类"非 HTTP 写请求"的事件 */
    public void recordAuthEvent(String event, String username, String ip, String userAgent) {
        record(AuditRecord.builder()
                .event(event)
                .username(username)
                .ip(ip)
                .userAgent(userAgent)
                .method("")
                .path("")
                .target("")
                .paramsDigest("")
                .result(AuditEventType.REGISTER.equals(event) ? "ok" : "ok")
                .build());
    }
}