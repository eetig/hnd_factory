package org.example.component;

import org.example.entity.WorkOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工单导入内存缓存：taskId -> 解析后的工单列表。
 * 带 TTL 过期 + 容量上限，防止内存溢出。
 */
@Component
public class WorkOrderImportCache {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderImportCache.class);

    /** 缓存有效期：30 分钟 */
    private static final long TTL_MILLIS = 30 * 60 * 1000L;
    /** 最多缓存任务数，超出淘汰最旧的 */
    private static final int MAX_SIZE = 50;

    private final Map<String, CachedImport> cache = new ConcurrentHashMap<>();

    private static class CachedImport {
        final List<WorkOrder> data;
        final long createTime;

        CachedImport(List<WorkOrder> data, long createTime) {
            this.data = data;
            this.createTime = createTime;
        }

        boolean expired(long now) {
            return now - createTime > TTL_MILLIS;
        }
    }

    /** 缓存解析结果，返回 taskId */
    public String put(List<WorkOrder> data) {
        if (cache.size() >= MAX_SIZE) {
            evictOldest();
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        cache.put(taskId, new CachedImport(data, System.currentTimeMillis()));
        log.info("工单导入数据已缓存, taskId={}, 行数={}, 当前缓存任务数={}", taskId, data.size(), cache.size());
        return taskId;
    }

    /** 取出并移除（一次性消费），不存在或已过期返回 null */
    public List<WorkOrder> take(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        CachedImport cached = cache.remove(taskId);
        if (cached == null) {
            return null;
        }
        if (cached.expired(System.currentTimeMillis())) {
            log.warn("工单导入缓存已过期, taskId={}", taskId);
            return null;
        }
        return cached.data;
    }

    /** 定时清理过期任务（每 5 分钟） */
    @Scheduled(fixedRate = 5 * 60 * 1000L)
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().expired(now));
        int after = cache.size();
        if (before != after) {
            log.info("清理过期工单导入缓存, 任务数 {} -> {}", before, after);
        }
    }

    // 淘汰最旧的任务，防止缓存无上限增长
    private void evictOldest() {
        cache.entrySet().stream()
                .min((a, b) -> Long.compare(a.getValue().createTime, b.getValue().createTime))
                .ifPresent(e -> {
                    cache.remove(e.getKey());
                    log.warn("工单导入缓存超出上限, 淘汰最旧任务 taskId={}", e.getKey());
                });
    }
}
