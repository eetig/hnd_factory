package org.example.component;

import org.example.dto.MaterialImportRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物料导入的内存缓存：token -> 解析结果（与 WorkOrderImportCache 同一套做法）。
 *
 * <p>为什么缓存而不是让前端把 3 万行再传回来：一次导入 30,434 行，JSON 往返约 10MB，
 * 而且前端手里那份数据与服务端解析出来的可能不是同一份（人可能在两次请求之间改文件）。
 *
 * <p>容量按「行数」算而不是按「任务数」：一个任务就有 3 万行，50 个任务会撑爆堆。
 * 这里只留最近 3 次导入，且 30 分钟过期。
 */
@Component
public class MaterialImportCache {

    /** 缓存有效期：30 分钟（与工单导入一致） */
    private static final long TTL_MILLIS = 30 * 60 * 1000L;
    /** 最多缓存的任务数（每个约 3 万行，3 个 ≈ 20MB） */
    private static final int MAX_SIZE = 3;

    private final Map<String, CachedImport> cache = new ConcurrentHashMap<>();

    /** 公开给服务层读取（同包外也要用）；字段只读 */
    public static final class CachedImport {
        public final List<MaterialImportRow> rows;
        public final String fileName;
        public final long createTime;

        CachedImport(List<MaterialImportRow> rows, String fileName, long createTime) {
            this.rows = rows;
            this.fileName = fileName;
            this.createTime = createTime;
        }
    }

    public String put(List<MaterialImportRow> rows, String fileName) {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now - e.getValue().createTime > TTL_MILLIS);
        if (cache.size() >= MAX_SIZE) {
            cache.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().createTime, b.getValue().createTime))
                    .ifPresent(e -> cache.remove(e.getKey()));
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        cache.put(token, new CachedImport(rows, fileName, now));
        return token;
    }

    /** 取出后删除：导入只能提交一次，避免重复点「确认导入」写两遍 */
    public CachedImport take(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        CachedImport cached = cache.remove(token);
        if (cached == null) {
            return null;
        }
        if (System.currentTimeMillis() - cached.createTime > TTL_MILLIS) {
            return null;
        }
        return cached;
    }

    public String fileNameOf(String token) {
        CachedImport cached = cache.get(token);
        return cached == null ? null : cached.fileName;
    }
}
