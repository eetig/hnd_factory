package org.example.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sa-Token 配置：注册拦截器，开启注解式鉴权 + 写操作角色闸门。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /**
     * 写操作白名单：登录/退出写的是会话不是业务库，且必须匿名可达；
     * /error 必须放行，否则 POST 出错后的 ERROR 转发会被拦成 500，盖掉干净的 401/403。
     */
    private static final String[] WRITE_WHITELIST = {"/api/login", "/api/logout", "/api/register", "/api/audit/locked-click", "/error"};

    /**
     * 「只给 admin 看」的台账明细路径（决策-004）：工单 / 领料 / 入库 / 货物移动 / 采购进度。
     *
     * <p>这几组是**明细**，收归 admin；周统计页需要的那两个汇总数走
     * {@code /api/stats/weekly}（不在本清单里，保持免登录），
     * 所以锁了明细也不会把周统计变成空表。
     *
     * <p>⚠️ 加路径前先想清楚：这里锁掉的 GET 会连带影响所有用它的页面，
     * 而决策-003 的「新增只读接口一律免登录」是默认规则 —— 本清单是有意开出的例外。
     * 没在清单里的：/api/stock（物料查询）、/api/tank-level、/api/equipment（设备台账）、
     * /api/material（物料主数据检索，图片解析与文件导入也在用）。
     *
     * <p>⚠️ {@code /api/purchase/**} 是变更-096 加进来的。第 18) 步当时写的是
     * 「采购进度是厂内公开数据，查询免登录」；使用方 2026-10-17 明确「这页只有管理员能看」，
     * 于是改成与工单/领料同一档。**这里不新增权限位**（只用 admin 角色），
     * 免得又踩变更-091 那个坑（新权限位要同时播种 admin + 追加 schema 撤权清单）。
     * 将来要给车间主任/采购开"只看自己那部分"的看板时，**另开一个只读接口**，
     * 不要把这里放开。
     */
    private static final String[] ADMIN_READ_PATTERNS = {"/api/purchase/**"};

    /**
     * 「登录即可读」的接口（变更-129 分层访问）。
     *
     * <p>此前这几组读接口**只允许 admin**（决策-004）。使用方后来要求：工单/领料/入库/核算
     * 对**注册用户**开放只读，游客点它则提示"需要注册"。
     *
     * <p>⚠️ 不能简单把这几条从 ADMIN_READ_PATTERNS 里删掉 —— 删了它们就变成**匿名可读**，
     * 游客直接 curl 就能拿到厂里的台账。所以是**换闸门**，不是**拆闸门**：
     * 从"admin 才能读"降到"登录才能读"，匿名仍然是 401。
     *
     * <p>前端对应地把这几页的 access 从 admin 改成 login；两边必须同时生效
     * （只改前端 = 数据仍公开；只改后端 = 注册用户点进去满屏 401）。
     */
    private static final String[] LOGIN_READ_PATTERNS = {
            "/api/work-order/**", "/api/pick/**", "/api/inbound/**", "/api/goods-move/**"};

    @Autowired
    private org.example.audit.AuditInterceptor auditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 写操作留痕（变更-129）。放在 Sa-Token 之后：它只读取"成功/失败"，不参与鉴权判断。
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
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
            // 闸门 2：台账读接口 —— **登录即可**（变更-129）。匿名 401，任何角色都能读。
            SaRouter.match(SaHttpMethod.GET)
                    .match(LOGIN_READ_PATTERNS)
                    .check(r -> StpUtil.checkLogin());

            // 闸门 3：采购读接口 —— 仍只允许 admin（使用方 2026-10-20 确认：带价格与 OA 单号）。
            SaRouter.match(SaHttpMethod.GET)
                    .match(ADMIN_READ_PATTERNS)
                    .check(r -> StpUtil.checkRole("admin"));
        })).addPathPatterns("/**")
                /*
                 * ⚠️ /error 不走本拦截器（变更-061）。
                 *
                 * 那次派发是「处理某个请求时已经出错」之后的转发，此时 Sa-Token 的上下文并未
                 * 初始化 —— 本拦截器在这里再跑一遍鉴权，只会抛 SaTokenContextException，
                 * 然后被 GlobalExceptionHandler 的 Sa-Token 兜底转成一句 403，
                 * **把真实错误盖掉**（2026-10-15 实测：一条 DB 约束错误被盖成
                 * 「SaTokenContext 上下文尚未初始化」，排查方向完全错）。
                 *
                 * 下面 WRITE_WHITELIST 里的 "/error" 因此成了冗余保险 —— 留着不改，
                 * 两处都表达「/error 不该被鉴权拦」这同一个意思。
                 */
                .excludePathPatterns("/error");
    }
}
