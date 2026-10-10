package org.example.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>本工程原先没有任何 MP 插件配置（列表全是「查全表 + 前端分页」，用不上分页插件）。
 * 列表统一改后端分页后，**这个 Bean 是必须的**：{@code selectPage} 在没有
 * {@link PaginationInnerInterceptor} 时既不报错也不生成 {@code LIMIT}，
 * 只是把全表查回来、并且 {@code total} 恒为 0 —— 页面表现成
 * 「分页器写着第 1/1 页，一页却列出几百条」，很难往「少了个 Bean」上想。
 *
 * <p>⚠️ 依赖在 build.gradle 里是单独一条 {@code com.baomidou:mybatis-plus-jsqlparser}：
 * MP 自 3.5.9 起把依赖 JSqlParser 的插件拆出了 mybatis-plus-extension，
 * 只引 starter 是拿不到 {@link PaginationInnerInterceptor} 这个类的（编译期就报找不到符号）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件必须是**第一个**加入的 InnerInterceptor：
        // 它要在最后改写 SQL 追加 LIMIT；排在别的插件后面时，
        // 别的插件改过的 SQL 不再经过它，分页会静默失效。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
