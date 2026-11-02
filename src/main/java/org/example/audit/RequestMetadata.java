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

    /**
     * 设备画像（2026-11-02）：优先用客户端自带的请求头 {@code X-Device}（"型号|系统|版本"），
     * 拿不到就从 UA 里粗解析（手机端 UA 一般带 Android/iOS 与机型）。
     *
     * <p>只做"够用"的解析，不引 UA 解析库：审计页要回答的是
     * "这个账号是不是在另一台设备上登录过"，不是做设备指纹。
     */
    public static String device(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String custom = request.getHeader("X-Device");
        if (custom != null && !custom.isBlank()) {
            String value = custom.trim();
            return value.length() > 120 ? value.substring(0, 120) : value;
        }
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return "";
        }
        String os = "";
        if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) {
            os = "iOS";
        } else if (ua.contains("Windows")) {
            os = "Windows";
        } else if (ua.contains("Mac OS")) {
            os = "macOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        }
        // Android 机型：UA 里通常是 "...; 机型 Build/..."
        String model = "";
        int androidIdx = ua.indexOf("Android");
        if (androidIdx >= 0) {
            int semi = ua.indexOf(';', androidIdx);
            int build = ua.indexOf("Build/", androidIdx);
            if (semi >= 0 && build > semi) {
                model = ua.substring(semi + 1, build).trim();
                // 去掉 "zh-cn"、分辨率之类的前缀，取最后一段
                int lastSemi = model.lastIndexOf(';');
                if (lastSemi >= 0) {
                    model = model.substring(lastSemi + 1).trim();
                }
            }
        }
        String device = (model.isEmpty() ? "" : model + "|") + (os.isEmpty() ? "未知" : os);
        return device.length() > 120 ? device.substring(0, 120) : device;
    }

    public static String currentDevice() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                            .getRequest();
            return device(request);
        } catch (Exception e) {
            return "";
        }
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