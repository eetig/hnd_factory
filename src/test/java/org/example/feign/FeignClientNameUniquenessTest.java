package org.example.feign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫：同一个服务名的 {@code @FeignClient} 必须各有**不同**的 {@code contextId}。
 *
 * <p>为什么需要它（2026-10-20 事故，变更-126）：
 * 把 img-service 更名成 file-service 时，{@code ImgFeignClient} 与新建的 {@code FileFeignClient}
 * 都写了 {@code name = "file-service"} —— 于是 Spring 注册第二个
 * {@code file-service.FeignClientSpecification} 时抛
 * {@code BeanDefinitionOverrideException}，**应用直接起不来**（生产上表现为容器
 * Restarting，整套 API 挂掉）。
 *
 * <p>为什么单元测试抓不到：本项目没有 {@code @SpringBootTest}，测试不启动 Spring 上下文，
 * bean 定义冲突只在**真正启动**时才暴露 —— 而"能不能启动"恰恰是最该在上线前知道的事。
 * 所以这里退一步，扫源码做静态守卫：只认一个名字只能出现一次，出现多次必须各有 contextId。
 *
 * <p>局限（如实记）：正则只认 `@FeignClient(` 后到第一个 `)` 之间的内容，写多行的注解
 * （本项目都是这种）没问题，但如果将来有人在注解里嵌括号，这里可能读漏 —— 宁可漏报也不误报，
 * 误报会让人习惯性跳过它。
 */
class FeignClientNameUniquenessTest {

    private static final Pattern FEIGN = Pattern.compile("@FeignClient\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern NAME = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern CONTEXT_ID = Pattern.compile("contextId\\s*=\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("同 name 的 FeignClient 必须有互不相同的 contextId（否则启动即 BeanDefinitionOverrideException）")
    void sameNameRequiresDistinctContextId() throws Exception {
        Path dir = Path.of("src", "main", "java", "org", "example", "feign");
        assertTrue(Files.isDirectory(dir), "找不到 feign 包目录：" + dir.toAbsolutePath());

        // name -> [(文件, contextId)]
        Map<String, List<String[]>> byName = new HashMap<>();
        List<String> scanned = new ArrayList<>();

        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = FEIGN.matcher(source);
                while (m.find()) {
                    String body = m.group(1);
                    Matcher n = NAME.matcher(body);
                    if (!n.find()) {
                        continue;
                    }
                    Matcher c = CONTEXT_ID.matcher(body);
                    String contextId = c.find() ? c.group(1) : "";
                    byName.computeIfAbsent(n.group(1), k -> new ArrayList<>())
                            .add(new String[] {file.getFileName().toString(), contextId});
                    scanned.add(file.getFileName() + " → " + n.group(1)
                            + (contextId.isEmpty() ? "（无 contextId）" : " / " + contextId));
                }
            }
        }

        assertTrue(!scanned.isEmpty(), "一个 @FeignClient 都没扫到 —— 锚点断了，这个守卫已经失效");

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<String[]>> entry : byName.entrySet()) {
            List<String[]> clients = entry.getValue();
            if (clients.size() < 2) {
                continue;
            }
            /*
             * Spring 的规则：同一服务名的**第一个**客户端可以用默认 contextId，
             * 之后每多一个都必须写一个**互不相同**的 contextId。
             * 所以判据是「没写 contextId 的最多一个」+「写了的不许重复」，
             * 而不是"每个都得写"（第一版写严了，会把正确状态也判红）。
             */
            Map<String, String> seen = new HashMap<>();
            int withoutContextId = 0;
            String firstWithout = "";
            for (String[] client : clients) {
                String file = client[0];
                String contextId = client[1];
                if (contextId.isEmpty()) {
                    withoutContextId++;
                    firstWithout = file;
                    continue;
                }
                String previous = seen.put(contextId, file);
                if (previous != null) {
                    problems.add(file + " 与 " + previous + " 的 contextId 都是 " + contextId
                            + "（服务名 " + entry.getKey() + "）");
                }
            }
            if (withoutContextId > 1) {
                problems.add("服务名 " + entry.getKey() + " 有 " + withoutContextId
                        + " 个客户端没写 contextId（" + firstWithout + " 等）—— "
                        + "只有一个能用默认 contextId，其余必须各自指定");
            }
        }

        assertTrue(problems.isEmpty(),
                "同一个服务名有多个 FeignClient 时，必须各自的 contextId 互不相同，"
                        + "否则 Spring 注册 '<service>.FeignClientSpecification' 冲突、应用启动失败：\n"
                        + String.join("\n", problems)
                        + "\n（扫到的客户端：" + String.join("、", scanned) + "）");
    }
}
