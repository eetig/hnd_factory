package org.example.audit;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求里取审计要的三样东西：IP、User-Agent、登录名（变更-129）。
 *
 * <p>⚠️ IP 必须认 {@code X-Forwarded-For} —— 本站跑在 OpenResty 后面，
 * 拿 {@code getRemoteAddr()} 只会得到反代的地址。取第一个（最外层客户端）。
 * 生产上前提是 OpenResty 只在一处设置这个头；否则客户端能伪造。
 */
public final class RequestMetadata {

    private RequestMetadata() {
    }

    /** 当前请求（非 Web 线程返回 null）。登录/注册/退出这些场景拿不到入参 request。 */
    private static HttpServletRequest currentRequest() {
        try {
            var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (Exception ignored) {
            // 非 Web 线程
        }
        return null;
    }

    public static String currentIp() {
        return ip(currentRequest());
    }

    public static String currentUserAgent() {
        return userAgent(currentRequest());
    }

    public static String ip(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = comma > 0 ? xff.substring(0, comma) : xff;
            return first.trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ua = request.getHeader("User-Agent");
        return ua == null ? "" : ua;
    }

    /** 登录名（优先会话里的 username，其次 loginId）；未登录返回空串 */
    public static String username() {
        try {
            Object name = StpUtil.getSession().get("username");
            if (name != null && !String.valueOf(name).isBlank()) {
                return String.valueOf(name);
            }
            return StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            return "";
        }
    }
}