package org.example.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动拼图验证码（2026-10-21）。
 *
 * <p>参考的是网上那篇 vue3 滑动拼图的**外观与交互**，但把"比对"从**前端搬到了后端** ——
 * 那篇文章是纯前端比对（offsetX 与 x 差 2px 内算过），直接调接口就能绕过，只能算演示。
 *
 * <p>**不引第三方依赖**：图形用 JDK 自带的 Java2D 画（渐变 + 若干半透明图形当噪点），
 * 拼图块与缺口用同一条六边形 Path2D。
 *
 * <p>三件事保证它不是摆设：
 *   ① 正确答案只存在服务端（内存，2 分钟过期）
 *   ② 一个挑战**只能用一次**；错 3 次直接作废（防止暴力试 offset）
 *   ③ 验证通过发一张**一次性 ticket**，注册时必须带它 —— 光有 ticket 也不能重复用
 *
 * <p>局限（写清楚，别自欺）：拼图对**真人**友好、对**脚本**不是强防护。要真正抗刷，
 * 还得叠 nginx 限流与全量上限（使用方 2026-10-21 明确说这次不做那三件套，只做验证码）。
 */
@Service
public class SliderCaptchaService {

    /** 画布尺寸：与前端 1:1，手机端按宽度等比缩放 */
    public static final int WIDTH = 320;
    public static final int HEIGHT = 160;
    /** 拼图块边长 */
    public static final int PIECE = 48;
    /** 允许的像素误差（真人拖不到像素级精确；±5 足够宽容又不至于瞎蒙） */
    private static final int TOLERANCE = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final long CHALLENGE_TTL_MS = 2 * 60 * 1000L;
    private static final long TICKET_TTL_MS = 5 * 60 * 1000L;
    /** 上限保护：内存里的挑战/ticket 不能无限涨（也是防"刷验证码把内存撑爆"的第一道） */
    private static final int MAX_ENTRIES = 2000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    private record Challenge(int x, int y, long expiresAt, int attempts) {
        Challenge fail() {
            return new Challenge(x, y, expiresAt, attempts + 1);
        }
    }

    private record Ticket(String id, long expiresAt) {
    }

    /** 一次挑战：两张图都用 data URI 返回，前端直接放进 <img>/<image> */
    public record CaptchaView(
            String id,
            String background,
            String piece,
            int width,
            int height,
            int pieceSize,
            int pieceTop) {
    }

    public record VerifyResult(boolean ok, String ticket, String message) {
    }

    public CaptchaView create() {
        evictExpired();

        // 缺口左上角：x 留出右侧空间，y 留出上下边距
        int x = 60 + random.nextInt(WIDTH - PIECE - 100);
        int y = 10 + random.nextInt(HEIGHT - PIECE - 20);

        BufferedImage art = paintBackground();
        BufferedImage background = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D bg = background.createGraphics();
        bg.drawImage(art, 0, 0, null);
        // 缺口：同一块六边形挖一个半透明黑，让人看得见"要拖到哪"
        Shape hole = hexagon(x, y);
        bg.setColor(new Color(0, 0, 0, 130));
        bg.fill(hole);
        bg.setColor(new Color(255, 255, 255, 90));
        bg.setStroke(new BasicStroke(1.2f));
        bg.draw(hole);
        bg.dispose();

        // 拼图块：从同一张画上裁下来，形状相同
        BufferedImage piece = new BufferedImage(PIECE, PIECE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = piece.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        pg.setClip(hexagon(0, 0));
        pg.drawImage(art, -x, -y, null);
        pg.dispose();

        String id = randomId();
        challenges.put(id, new Challenge(x, y, System.currentTimeMillis() + CHALLENGE_TTL_MS, 0));

        return new CaptchaView(id, dataUri(background), dataUri(piece),
                WIDTH, HEIGHT, PIECE, y);
    }

    /** 校验拖动位置；成功返回一次性 ticket */
    public VerifyResult verify(String id, int offsetX) {
        evictExpired();
        Challenge c = id == null ? null : challenges.get(id);
        if (c == null || c.expiresAt() < System.currentTimeMillis()) {
            return new VerifyResult(false, null, "验证码已失效，请重新滑动");
        }

        if (Math.abs(offsetX - c.x()) > TOLERANCE) {
            int attempts = c.attempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                challenges.remove(id);
                return new VerifyResult(false, null, "位置不对，已失效，请重新滑动");
            }
            challenges.put(id, c.fail());
            return new VerifyResult(false, null, "位置不对，再试一次");
        }

        // 一次性：验过就删，防止同一张图反复"验通过"
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

    // ---------- 画图 ----------

    /** 背景：暖色渐变 + 若干半透明圆形/横线当噪点（不用外部图片，构建产物里没有二进制资源） */
    private BufferedImage paintBackground() {
        BufferedImage art = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = art.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color[] palettes = {
                new Color(0xFD, 0xE6, 0xD3), new Color(0xD9, 0xEC, 0xFF),
                new Color(0xE4, 0xF5, 0xE9), new Color(0xFB, 0xE7, 0xF3),
                new Color(0xFF, 0xF3, 0xD6),
        };
        Color base = palettes[random.nextInt(palettes.length)];
        Color second = new Color(
                Math.min(255, base.getRed() - 30),
                Math.min(255, base.getGreen() - 20),
                Math.min(255, base.getBlue() + 20));
        g.setPaint(new GradientPaint(0, 0, base, WIDTH, HEIGHT, second));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(
                    random.nextInt(256), random.nextInt(256), random.nextInt(256), 28 + random.nextInt(40)));
            int w = 30 + random.nextInt(120);
            int h = 30 + random.nextInt(90);
            g.fillOval(random.nextInt(WIDTH) - w / 2, random.nextInt(HEIGHT) - h / 2, w, h);
        }
        g.setColor(new Color(255, 255, 255, 90));
        for (int i = 0; i < 5; i++) {
            int yy = random.nextInt(HEIGHT);
            g.setStroke(new BasicStroke(1f + random.nextFloat()));
            g.drawLine(0, yy, WIDTH, yy + random.nextInt(20) - 10);
        }
        g.dispose();
        return art;
    }

    /** 六边形拼图块（与那篇文章的 clip-path 同一个形状语言，纯 Java2D 画） */
    private static Shape hexagon(int x, int y) {
        double w = PIECE;
        double h = PIECE;
        Path2D p = new Path2D.Double();
        p.moveTo(x + w * 0.5, y);
        p.lineTo(x + w * 0.91, y + h * 0.23);
        p.lineTo(x + w * 0.80, y + h * 0.76);
        p.lineTo(x + w * 0.5, y + h);
        p.lineTo(x + w * 0.20, y + h * 0.76);
        p.lineTo(x + w * 0.09, y + h * 0.23);
        p.closePath();
        return p;
    }

    private String dataUri(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("生成验证码图片失败", e);
        }
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
        // 兜底：过期清完还超上限，就清掉最早的一批（不做 LRU，够用）
        if (challenges.size() > MAX_ENTRIES) {
            challenges.keySet().stream().limit(challenges.size() - MAX_ENTRIES).toList()
                    .forEach(challenges::remove);
        }
    }
}