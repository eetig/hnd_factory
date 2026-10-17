package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.PurchaseTracking;
import org.example.mapper.PurchaseTrackingMapper;
import org.example.util.PurchaseTrackingStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采购到货预警：每天早 8:30 把「超期 / 临期」的采购需求推送到企业微信群机器人（变更-096）。
 *
 * <h2>范围与口径</h2>
 * <p>只推**已提单但还没到货**的行，并且只挑出超期与临期两种状态 ——
 * 「待提单」不该催采购（是我们自己还没提单），「已到货」没什么可催的。
 * 状态口径**完全复用** {@link PurchaseTrackingStatus}，不在这里另写一套判断。
 *
 * <h2>为什么一天只推一条汇总，而不是状态一变就推</h2>
 * <p>使用方 2026-10-17 定的（原话大意：先每天汇总一次）。超期是个持续状态，
 * 按条推会在第二天变成双倍、第三天三倍，很快就没人看了；
 * 一条汇总每天固定时间到，反而会真的被读到。
 *
 * <h2>失败怎么处理</h2>
 * <p><b>只记日志、不重试、不抛异常</b>：webhook 挂了是配置问题，重试只会刷屏日志；
 * 抛异常会让 Spring 的调度线程把这次执行标红，但同样救不回消息。
 * 需要「有没有送出去」的可观测性时请看日志里的 {@code 采购预警推送失败}。
 *
 * <p>⚠️ 提醒使用方：这段跑在**后端所在的那台机器**上，不是浏览器那台。
 * 生产上如果后端在 192.168.3.7，要保证**那台机器**能访问 {@code qyapi.weixin.qq.com}。
 */
@Slf4j
@Service
public class PurchaseAlertService {

    /** 企业微信群机器人单条消息的 content 上限是 4096 字节，留出余量按条数截断 */
    private static final int MAX_ROWS_IN_MESSAGE = 20;

    /** 企业微信群机器人可能返回的错误码字段（不同版本分别用 errcode / errcode+errmsg） */
    private static final Pattern ERRCODE = Pattern.compile("\"errcode\"\\s*:\\s*(-?\\d+)");

    private final PurchaseTrackingMapper purchaseTrackingMapper;

    private final boolean enabled;

    private final String webhook;

    public PurchaseAlertService(
            PurchaseTrackingMapper purchaseTrackingMapper,
            @Value("${purchase.alert.enabled:false}") boolean enabled,
            @Value("${purchase.alert.webhook:}") String webhook) {
        this.purchaseTrackingMapper = purchaseTrackingMapper;
        this.enabled = enabled;
        this.webhook = webhook == null ? "" : webhook.trim();
        // 启动时就把配置状态说清楚：改了配置没重启、或者配了但没开，都能一眼看出来
        if (this.enabled && this.webhook.isEmpty()) {
            log.warn("采购预警已启用，但 purchase.alert.webhook 为空 —— 每天 8:30 不会真的推送");
        } else if (!this.enabled) {
            log.info("采购预警未启用（purchase.alert.enabled=false）");
        } else {
            log.info("采购预警已启用，每天 08:30 推送超期/临期汇总");
        }
    }

    /**
     * 每天 08:30 推一次汇总。
     *
     * <p>cron 用 6 段（Spring 风格，末段 day-of-week 用 {@code ?}）。
     * 想改时间只改这里 —— 没有把 cron 做成配置项，是因为多一个能配错的地方不值得：
     * 真要改就改这一行，顺便让人看见。
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void dailyDigest() {
        if (!enabled) {
            return;
        }
        try {
            sendDigest(LocalDate.now());
        } catch (Exception e) {
            // 调度方法里逃出去的异常会被 Spring 记为 error，下次照常执行；
            // 这里主动兜住只为把日志写成业务语义，便于排查
            log.error("采购预警推送异常（本次跳过，明天再试）", e);
        }
    }

    /**
     * 组装并推送汇总。抽成 public 方法是为了能手动触发（本地验证、或将来加个"立即推送"按钮）。
     *
     * @return 实际推送的条数；0 表示没有超期/临期（那种情况**不发空消息**）
     */
    public int sendDigest(LocalDate today) throws IOException, InterruptedException {
        if (webhook.isEmpty()) {
            log.warn("采购预警跳过：purchase.alert.webhook 未配置");
            return 0;
        }

        List<PurchaseTracking> alerts = findAlerts(today);
        if (alerts.isEmpty()) {
            log.info("采购预警：今天没有超期/临期，不推送");
            return 0;
        }

        String content = buildMarkdown(alerts, today);
        boolean ok = postToWeCom(content);
        if (ok) {
            log.info("采购预警已推送：{} 条", alerts.size());
        }
        return alerts.size();
    }

    /**
     * 查出「已提单、未到货、且状态是超期或临期」的行，按紧急程度排序。
     *
     * <p>先用 SQL 把范围缩到「有 OA 单号 + 没有送货单」，再用
     * {@link PurchaseTrackingStatus} 判超期/临期 —— 派生口径只此一处。
     */
    public List<PurchaseTracking> findAlerts(LocalDate today) {
        LambdaQueryWrapper<PurchaseTracking> wrapper = new LambdaQueryWrapper<>();
        // 两列都是 NOT NULL DEFAULT ''，所以"没有"就是空串而不是 NULL
        wrapper.ne(PurchaseTracking::getOaNo, "");
        wrapper.eq(PurchaseTracking::getDeliveryNote, "");
        wrapper.isNotNull(PurchaseTracking::getRequiredArrivalDate);

        List<PurchaseTracking> rows = purchaseTrackingMapper.selectList(wrapper);
        List<PurchaseTracking> alerts = new ArrayList<>();
        for (PurchaseTracking row : rows) {
            PurchaseTrackingStatus status = PurchaseTrackingStatus.of(row, today);
            if (status == PurchaseTrackingStatus.OVERDUE || status == PurchaseTrackingStatus.DUE_SOON) {
                alerts.add(row);
            }
        }
        // 超期天数多的排前面；同一天数的按要求到货日期早的在前
        alerts.sort(Comparator
                .comparing((PurchaseTracking r) -> PurchaseTrackingStatus.of(r, today) == PurchaseTrackingStatus.DUE_SOON)
                .thenComparing(r -> r.getRequiredArrivalDate() == null ? LocalDate.MAX : r.getRequiredArrivalDate()));
        return alerts;
    }

    /**
     * 企业微信 markdown 消息。**故意做得短**：一条消息里能一眼看完该催谁，
     * 长消息在手机上要滑三屏，等于没看。
     */
    private String buildMarkdown(List<PurchaseTracking> alerts, LocalDate today) {
        long overdueCount = alerts.stream()
                .filter(r -> PurchaseTrackingStatus.of(r, today) == PurchaseTrackingStatus.OVERDUE)
                .count();
        long dueSoonCount = alerts.size() - overdueCount;

        StringBuilder sb = new StringBuilder();
        sb.append("## 采购到货提醒 ").append(today).append('\n');
        sb.append("**超期 ").append(overdueCount).append(" 条");
        if (dueSoonCount > 0) {
            sb.append(" ｜ 临期 ").append(dueSoonCount).append(" 条");
        }
        sb.append("**\n");

        int shown = 0;
        for (PurchaseTracking row : alerts) {
            if (shown >= MAX_ROWS_IN_MESSAGE) {
                sb.append("\n> 还有 ").append(alerts.size() - shown).append(" 条未列出，请到「采购需求详情」页查看\n");
                break;
            }
            PurchaseTrackingStatus status = PurchaseTrackingStatus.of(row, today);
            sb.append('\n');
            if (status == PurchaseTrackingStatus.OVERDUE) {
                Integer days = PurchaseTrackingStatus.overdueDays(row, today);
                sb.append("🔴 **超期 ").append(days == null ? "?" : days).append(" 天**  ");
            } else {
                Integer days = PurchaseTrackingStatus.daysToDue(row, today);
                sb.append("🟡 **临期");
                if (days != null) {
                    sb.append(days == 0 ? "（今天到期）" : "（还有 " + days + " 天）");
                }
                sb.append("**  ");
            }
            sb.append(nullSafe(row.getMaterialName()));
            String qty = formatQuantity(row.getQuantity(), row.getUnit());
            if (!qty.isEmpty()) {
                sb.append(" ×").append(qty);
            }
            sb.append('\n');
            sb.append("需求人：").append(nullSafe(row.getRequester()));
            sb.append(" ｜ 要求到货：").append(row.getRequiredArrivalDate());
            if (hasText(row.getOaNo())) {
                sb.append(" ｜ OA单号：").append(row.getOaNo());
            }
            sb.append('\n');
            shown++;
        }
        return sb.toString();
    }

    /** POST 到企业微信群机器人；返回是否成功（失败只记日志） */
    private boolean postToWeCom(String markdownContent) throws IOException, InterruptedException {
        String body = "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":"
                + jsonString(markdownContent) + "}}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.warn("采购预警推送失败：HTTP {} body={}", response.statusCode(), abbreviate(response.body()));
            return false;
        }
        // 企业微信即使 HTTP 200 也可能在 body 里报错（webhook 失效、关键词不匹配、超频等），
        // 必须看 errcode，否则会一直以为推成功了
        Matcher matcher = ERRCODE.matcher(response.body() == null ? "" : response.body());
        if (matcher.find() && !"0".equals(matcher.group(1))) {
            log.warn("采购预警推送失败：errcode={} body={}", matcher.group(1), abbreviate(response.body()));
            return false;
        }
        return true;
    }

    /** 把一个字符串转成 JSON 字符串字面量（含转义）；只用于拼这一个 body，不引 JSON 库 */
    private static String jsonString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** 「3 个」；数量为空时返回空串（历史行没有数量） */
    private static String formatQuantity(BigDecimal quantity, String unit) {
        if (quantity == null) {
            return "";
        }
        String number = quantity.stripTrailingZeros().toPlainString();
        return hasText(unit) ? number + " " + unit.trim() : number;
    }

    private static String nullSafe(String value) {
        return hasText(value) ? value.trim() : "—";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }
}
