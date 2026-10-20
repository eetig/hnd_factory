package org.example.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.constant.AuditEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 写操作的留痕（变更-129）。
 *
 * <p><b>为什么用拦截器而不是 AOP 切面</b>：项目没有 spring-boot-starter-aop 依赖，
 * 为这一件事引一个新依赖不划算；而且"被拒绝的写请求"根本走不到业务方法，
 * 切面是切不到的 —— 拦截器在 {@code afterCompletion} 里既能看到成功也能看到异常，
 * 一处覆盖 write_ok / write_denied 两种情况。
 *
 * <p>只记**写方法**：GET 不记（否则一个列表页刷三次就是三条，表很快会爆）。
 * 登录/注册/退出由 AuthServiceImpl 自己记（那时 handler 还没进来）。
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** 这几个接口由 AuthServiceImpl 记，避免重复 */
    private static final Set<String> SKIP_PATHS = Set.of(
            "/api/login", "/api/logout", "/api/register");

    @Autowired
    private AuditRecorder auditRecorder;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        if (method == null || !WRITE_METHODS.contains(method.toUpperCase())) {
            return;
        }
        String path = request.getRequestURI();
        if (path != null && SKIP_PATHS.contains(path)) {
            return;
        }

        boolean ok = ex == null && response.getStatus() < 400;
        auditRecorder.record(AuditRecord.builder()
                .event(ok ? AuditEventType.WRITE_OK : AuditEventType.WRITE_DENIED)
                .username(RequestMetadata.username())
                .ip(RequestMetadata.ip(request))
                .userAgent(RequestMetadata.userAgent(request))
                .method(method)
                .path(path)
                .target("")
                // ⚠️ 只记摘要，不记正文：资料内容/密码类字段绝不能进审计表
                .paramsDigest(AuditRecord.digest(request.getQueryString()))
                .result(ok ? "ok" : "denied")
                .build());
    }
}