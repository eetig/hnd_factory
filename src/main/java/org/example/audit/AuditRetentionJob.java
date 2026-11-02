package org.example.audit;

import org.example.controller.AuditController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审计日志的过期清理（2026-11-02，变更-178）。
 *
 * <p>使用方「全按推荐」里定的口径：日志**只读、不提供按条删除**，自动保留 365 天。
 * 这条定时任务就是"自动保留"的实现：每天凌晨 3:40 删掉 365 天以前的，
 * 越存越大不会成为负担，也不会有人跑来说"帮我删一条"。
 *
 * <p>为什么不给手动删除接口：审计日志能删就等于没有 —— 真需要删，
 * 只有运维直接进库这一条路，且会留下运维自己的操作痕迹（改库不在应用日志里）。
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    @Autowired
    private AuditController auditController;

    @Scheduled(cron = "0 40 3 * * ?")
    public void purgeExpiredDaily() {
        try {
            int removed = auditController.purge();
            log.info("审计日志过期清理完成：删除 {} 条（保留 {} 天）", removed, AuditController.RETENTION_DAYS);
        } catch (Exception e) {
            log.warn("审计日志过期清理失败（不影响业务）：{}", e.getMessage());
        }
    }
}
