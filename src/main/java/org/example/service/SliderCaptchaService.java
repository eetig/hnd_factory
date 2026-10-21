package org.example.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动验证（2026-10-21，第二版）。
 *
 * <p>第一版是"拼图 + 缺口"（照掘金那篇做的，用 Java2D 画背景和六边形缺口）。
 * 使用方看了实际效果说不好看，点名要 TMUI 的 xSlideVerify 那种形态 —— 那个组件其实
 * **没有图**：一条浅灰轨道 + 白色圆形滑块 + 居中提示文字，拖到目标位置就变成功色。
 * 所以这一版把图整个去掉，只留"滑到某个位置"这件事。
 *
 * <p><b>去掉图之后"位置"从哪来</b>：服务端每次下发一个**随机目标百分比**（40%~85%），
 * 前端只拿到"目标位置"用来画一条淡淡的指示带，**判定仍在服务端**：
 * 传回百分比，误差 ±6% 内算过。这样：
 *   · 看着就是一根干净的滑动条（使用方要的观感）
 *   · 但"该停在哪"是每次变的，不是"拖到底就过"
 *   · 一次性 ticket + 错 3 次作废 + 2 分钟过期，和第一版一致
 *
 * <p>⚠️ 诚实说明它有多强：目标位置会显示成一条指示带，**脚本读得到** ⇒ 它挡的是
 * "随手写个循环去注册"的脚本，挡不住针对性脚本。真要抗刷还得靠限流（见待办 D6）。
 */
@Service
public class SliderCaptchaService {

    /** 目标百分比范围：不从 0 开始（拖到底就过=没有熵），也不到 100（留出滑块的宽） */
    private static final int MIN_TARGET = 40;
    private static final int MAX_TARGET = 85;
    /** 允许的百分比误差（真人拖不到 1% 精度；±6% 大约对应 40px 轨道上的 2.4px） */
    private static final int TOLERANCE = 6;
    private static final int MAX_ATTEMPTS = 3;
    private static final long CHALLENGE_TTL_MS = 2 * 60 * 1000L;
    private static final long TICKET_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_ENTRIES = 2000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    private record Challenge(int targetPercent, long expiresAt, int attempts) {
        Challenge fail() {
            return new Challenge(targetPercent, expiresAt, attempts + 1);
        }
    }

    private record Ticket(String id, long expiresAt) {
    }

    /** 下发给前端的一次挑战：只有目标位置与容差，没有答案之外的任何信息 */
    public record ChallengeView(String id, int targetPercent, int tolerance) {
    }

    public record VerifyResult(boolean ok, String ticket, String message) {
    }

    public ChallengeView create() {
        evictExpired();
        int target = MIN_TARGET + random.nextInt(MAX_TARGET - MIN_TARGET + 1);
        String id = randomId();
        challenges.put(id, new Challenge(target, System.currentTimeMillis() + CHALLENGE_TTL_MS, 0));
        return new ChallengeView(id, target, TOLERANCE);
    }

    /** 校验滑动位置（百分比，0-100）；成功发一次性 ticket */
    public VerifyResult verify(String id, int percent) {
        evictExpired();
        Challenge c = id == null ? null : challenges.get(id);
        if (c == null || c.expiresAt() < System.currentTimeMillis()) {
            return new VerifyResult(false, null, "验证已失效，请重新拖动");
        }

        if (Math.abs(percent - c.targetPercent()) > TOLERANCE) {
            int attempts = c.attempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                challenges.remove(id);
                return new VerifyResult(false, null, "位置不对，已失效，请重新拖动");
            }
            challenges.put(id, c.fail());
            return new VerifyResult(false, null, "没对准，再试一次");
        }

        // 一次性：验过就删，防止同一挑战反复"验通过"
        challenges.remove(id);
        String ticketId = randomId();
        tickets.put(ticketId, new Ticket(ticketId, System.currentTimeMillis() + TICKET_TTL_MS));
        return new VerifyResult(true, ticketId, "验证通过");
    }

    /** 注册时消费 ticket：存在且未过期才放行，用完即删 */
    public boolean consumeTicket(String ticketId) {
        evictExpired();
        if (ticketId == null || ticketId.isBlank()) {
            return false;
        }
        Ticket t = tickets.remove(ticketId);
        return t != null && t.expiresAt() >= System.currentTimeMillis();
    }

    private String randomId() {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        if (challenges.size() > MAX_ENTRIES) {
            challenges.keySet().stream().limit(challenges.size() - MAX_ENTRIES).toList()
                    .forEach(challenges::remove);
        }
    }
}