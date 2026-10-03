package org.example.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：注册拦截器，开启注解式鉴权 + 写操作角色闸门。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /**
     * 写操作白名单：登录/退出写的是会话不是业务库，且必须匿名可达；
     * /error 必须放行，否则 POST 出错后的 ERROR 转发会被拦成 500，盖掉干净的 401/403。
     */
    private static final String[] WRITE_WHITELIST = {"/api/login", "/api/logout", "/error"};

    /**
     * 「只给 admin 看」的台账明细路径（决策-004）：工单 / 领料 / 入库 / 货物移动。
     *
     * <p>这几组是**明细**，收归 admin；周统计页需要的那两个汇总数走
     * {@code /api/stats/weekly}（不在本清单里，保持免登录），
     * 所以锁了明细也不会把周统计变成空表。
     *
     * <p>⚠️ 加路径前先想清楚：这里锁掉的 GET 会连带影响所有用它的页面，
     * 而决策-003 的「新增只读接口一律免登录」是默认规则 —— 本清单是有意开出的例外。
     * 没在清单里的：/api/stock（物料查询）、/api/tank-level、/api/equipment（设备台账）、
     * /api/material（物料主数据检索，图片解析与文件导入也在用）。
     */
    private static final String[] ADMIN_READ_PATTERNS = {
            "/api/work-order/**", "/api/pick/**", "/api/inbound/**", "/api/goods-move/**"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 传入 auth 函数不会关掉注解鉴权（SaInterceptor.isAnnotation 默认仍为 true），
            // preHandle 先跑 @SaCheck* 再跑这里，两层是叠加关系。
            //
            // 闸门 1：所有写操作（增删改）一律只允许 admin 角色。
            // 查询接口（GET）默认不在此列，照旧免登录 —— 例外见闸门 2。
            // 各写接口自身的 @SaCheckPermission 仍然保留：它决定「前端显示哪些入口」，
            // 这里决定「非 admin 一律 403」，即便将来有人把写权限误授给别的角色也拦得住。
            SaRouter.match(SaHttpMethod.POST, SaHttpMethod.PUT, SaHttpMethod.PATCH, SaHttpMethod.DELETE)
                    .notMatch(WRITE_WHITELIST)
                    .check(r -> StpUtil.checkRole("admin"));

            // 闸门 2：台账明细的**读**也只允许 admin（决策-004）。
            // 前端对应地把这几个 Tab 按角色藏起来，两边必须同时生效：
            // 只藏前端 = 数据仍然公开，只锁后端 = 非 admin 点进去满屏 403。
            // /api/work-order/** 一并盖住 /list、/detail、/image/list
            // （/import/**、/ocr/** 是 POST，已被闸门 1 覆盖）。
            SaRouter.match(SaHttpMethod.GET)
                    .match(ADMIN_READ_PATTERNS)
                    .check(r -> StpUtil.checkRole("admin"));
        })).addPathPatterns("/**");
    }
}
