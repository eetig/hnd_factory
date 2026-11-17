package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域白名单（2026-11-11，桌面版接入）。
 *
 * <p>为什么需要它：网页版的页面就在 {@code hbbhnd.cloud} 名下，{@code /api} 是同源请求，
 * 从来不需要 CORS。桌面版（Tauri）的页面来源是 {@code tauri.localhost}（exe 里的本地文件），
 * 请求打到 {@code https://hbbhnd.cloud/api} 就变成跨域 —— 不放开的话浏览器内核会在
 * **预检（OPTIONS）**这一关直接拦掉，表现为「界面打开了、但登录没反应、列表一片空白」。
 *
 * <p>为什么只放这几个来源：{@code tauri.localhost} 是 Tauri 在 Windows/Linux 上给本地页面
 * 分配的固定域名（macOS 是 {@code tauri://localhost}），不是任意网站，所以这不是「放开 *」。
 * 真要放开 *，任何网页都能拿用户的 satoken 调我们的接口。
 *
 * <p>鉴权走的是请求头 {@code satoken}（见前端 api/request.js），不是 Cookie，
 * 所以这里**不设 allowCredentials** —— 设了反而要求前端带凭据、且与 "*" 互斥。
 *
 * <p>2026-11-17 追加本机两个开发地址（{@code 127.0.0.1:9092} 是 App 的 H5 预览、
 * {@code localhost:9091} 是 PC 的 vite dev）。加它们的理由是：这两个端口只在开发者
 * 本机上，外部网页拿不到；不加的话本地一切写请求（POST/PUT/DELETE）都会被预检拦成
 * 403，只能靠把请求体抄到线上重放来验证，慢且容易抄错。
 * 生产域名同源、本来就不走 CORS，因此这几条对线上没有任何影响。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://tauri.localhost",
                        "https://tauri.localhost",
                        "tauri://localhost",
                        "http://127.0.0.1:9092",
                        "http://localhost:9092",
                        "http://127.0.0.1:9091",
                        "http://localhost:9091")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
