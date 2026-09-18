package org.example.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：注册拦截器，开启注解式鉴权。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 无参构造 = 只做注解鉴权（@SaCheckLogin / @SaCheckPermission / @SaCheckRole）
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
