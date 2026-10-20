package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 打开 @Async（变更-129 的审计落库要用）。
 *
 * <p>审计写入放到独立线程：写失败只打日志、绝不连累业务请求。
 * 用默认的 SimpleAsyncTaskExecutor 就够（一天几十条），不额外配线程池。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}