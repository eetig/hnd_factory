package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.audit.AuditRecord;
import org.example.audit.AuditRecorder;
import org.example.audit.RequestMetadata;
import org.example.constant.AuditEventType;
import org.example.dto.Result;
import org.example.exception.BusinessException;
import org.example.entity.AuditEvent;
import org.example.entity.SysUser;
import org.example.mapper.AuditEventMapper;
import org.example.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计留痕入口（变更-129 起）+ **管理员查询**（2026-11-02，变更-178）。
 *
 * <h2>使用方要求（2026-11-02）</h2>
 * 「做一个管控，只有管理员能看到：注册时间、登录 IP、设备信息（风控）、
 *   每次上下线时间、做了哪些动作等等」，并「全按推荐」：
 * <ul>
 *   <li>只记**写操作** + 登录/登出/注册；读请求不记（否则一天几万条）</li>
 *   <li>上下线 = login / logout 两个事件</li>
 *   <li>设备信息 = IP + UA + 设备型号 / 系统（客户端带 X-Device 时优先用它）</li>
 *   <li>日志**只读、不提供按条删除**，自动保留 180 天</li>
 *   <li>只有 admin 能看：页面 + 接口双闸</li>
 * </ul>
 *
 * <p>⚠️ 关于「超时下线」：Sa-Token 的会话过期是惰性的（没人来请求就没有事件点），
 * 要精确只能加心跳上报，收益不值，所以按"登录 / 退出两条"给出，
 * 页面上用「登录时长」列把两者相减展示。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    /**
     * 日志保留天数：**365 天**（2026-11-02 从 180 天上调）。
     *
     * 原因：《网络安全法》第 21 条要求网络日志留存**不少于六个月**，
     * 180 天约等于 5.9 个月，卡在红线下面；改成 365 天既满足要求，也不会无限膨胀。
     * 使用方明确要求「不触犯任何法律」，这一条就是落在这个要求上的。
     */
    public static final int RETENTION_DAYS = 365;

    @Autowired
    private AuditRecorder auditRecorder;

    /**
     * 只有管理员能看管控日志（页面 + 接口双闸）。
     *
     * <p>为什么不用 {@code @SaCheckPermission("admin")}：这个仓的 admin 角色
     * （role_id=1）有 17 条 permission_key，但没有一条叫 "admin"，用权限串必然 403。
     * 也不能只靠前端隐藏入口 —— 接口必须自己挡。
     */
    private static void requireAdmin() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }
        if (!StpUtil.hasRole("admin")) {
            throw new BusinessException("只有管理员能查看管控日志");
        }
    }

    @Autowired
    private AuditEventMapper auditEventMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * 前端"点了锁定功能"的留痕入口（变更-129）。
     *
     * <p>为什么要单独一个端点：游客点的是**只读页面的导航项**，那不是一个写请求，
     * 拦截器看不到；但它恰恰是这个功能最想留下的证据（谁在什么时候想用哪个功能）。
     *
     * <p>**前端每会话每功能只调一次**（sessionStorage 去重），否则一次乱点就刷出几十条。
     * 这个端点本身在 WRITE_WHITELIST 里 —— 游客必须能调它，否则就记不下来。
     */
    @PostMapping("/locked-click")
    public Result<Void> lockedClick(@RequestBody(required = false) Map<String, String> body) {
        String pageKey = body == null ? "" : String.valueOf(body.getOrDefault("pageKey", ""));
        auditRecorder.record(AuditRecord.builder()
                .event(AuditEventType.LOCKED_CLICKED)
                .username(RequestMetadata.username())
                .ip(RequestMetadata.currentIp())
                .userAgent(RequestMetadata.currentUserAgent())
                .device(RequestMetadata.currentDevice())
                .method("POST")
                .path("/api/audit/locked-click")
                .target(pageKey.length() > 120 ? pageKey.substring(0, 120) : pageKey)
                .paramsDigest("")
                .result("denied")
                .build());
        return Result.success(null);
    }

    /**
     * 审计日志分页查询（管理员）。
     *
     * @param username 账号（精确）；空表示全部
     * @param event    事件类型；空表示全部
     * @param keyword  在 path / target / ip / device 里模糊匹配
     * @param from     起始日期（含），yyyy-MM-dd
     * @param to       结束日期（含）
     */
    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String event,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "50") long pageSize) {
        requireAdmin();

        LambdaQueryWrapper<AuditEvent> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.eq(AuditEvent::getUsername, username.trim());
        }
        if (event != null && !event.isBlank()) {
            wrapper.eq(AuditEvent::getEvent, event.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AuditEvent::getPath, kw)
                    .or().like(AuditEvent::getTarget, kw)
                    .or().like(AuditEvent::getIp, kw)
                    .or().like(AuditEvent::getDevice, kw));
        }
        LocalDateTime fromTime = parseStart(from);
        LocalDateTime toTime = parseEnd(to);
        if (fromTime != null) {
            wrapper.ge(AuditEvent::getCreatedAt, fromTime);
        }
        if (toTime != null) {
            wrapper.le(AuditEvent::getCreatedAt, toTime);
        }
        wrapper.orderByDesc(AuditEvent::getId);

        Page<AuditEvent> page = auditEventMapper.selectPage(
                new Page<>(Math.max(1, pageNum), Math.min(200, Math.max(1, pageSize))), wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        data.put("rows", page.getRecords());
        return Result.success(data);
    }

    /**
     * 账号清单（管理员）：注册时间 + 最近登录时间/IP/设备 + 累计登录次数。
     *
     * <p>这是风控最常看的一屏：某个账号是不是换设备了、是不是在异常时间登录过。
     */
    @GetMapping("/accounts")
    public Result<List<Map<String, Object>>> accounts() {
        requireAdmin();
        List<SysUser> users = sysUserMapper.selectList(null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser user : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("username", user.getUsername());
            row.put("realName", user.getRealName());
            row.put("roleId", user.getRoleId());
            row.put("registerTime", user.getCreateTime());

            AuditEvent lastLogin = auditEventMapper.selectOne(new LambdaQueryWrapper<AuditEvent>()
                    .eq(AuditEvent::getUsername, user.getUsername())
                    .eq(AuditEvent::getEvent, AuditEventType.LOGIN)
                    .orderByDesc(AuditEvent::getId)
                    .last("limit 1"));
            row.put("lastLoginAt", lastLogin == null ? null : lastLogin.getCreatedAt());
            row.put("lastLoginIp", lastLogin == null ? "" : lastLogin.getIp());
            row.put("lastLoginDevice", lastLogin == null ? "" : lastLogin.getDevice());

            Long loginCount = auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                    .eq(AuditEvent::getUsername, user.getUsername())
                    .eq(AuditEvent::getEvent, AuditEventType.LOGIN));
            row.put("loginCount", loginCount);
            rows.add(row);
        }
        return Result.success(rows);
    }

    /** 事件类型清单（管理员）：前端筛选下拉用，避免前端写死字符串 */
    @GetMapping("/events")
    public Result<List<Map<String, String>>> events() {
        requireAdmin();
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of("value", AuditEventType.REGISTER, "label", "注册"));
        rows.add(Map.of("value", AuditEventType.LOGIN, "label", "登录"));
        rows.add(Map.of("value", AuditEventType.LOGOUT, "label", "退出"));
        rows.add(Map.of("value", AuditEventType.WRITE_OK, "label", "写操作成功"));
        rows.add(Map.of("value", AuditEventType.WRITE_DENIED, "label", "写操作被拒"));
        rows.add(Map.of("value", AuditEventType.LOCKED_CLICKED, "label", "点锁定功能"));
        return Result.success(rows);
    }

    /**
     * 清理过期日志（管理员手动触发；平时由 {@code AuditRetentionJob} 每天跑）。
     *
     * <p>只能删**过期的**：没有"删指定记录"的接口 —— 审计日志可改可删就失去意义。
     */
    @PostMapping("/purge-expired")
    public Result<Integer> purgeExpired() {
        requireAdmin();
        return Result.success(purge());
    }

    /** 删除 180 天以前的日志，返回删除条数 */
    public int purge() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        return auditEventMapper.delete(new LambdaQueryWrapper<AuditEvent>()
                .lt(AuditEvent::getCreatedAt, threshold));
    }

    private static LocalDateTime parseStart(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date.trim()).atStartOfDay();
    }

    private static LocalDateTime parseEnd(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date.trim()).atTime(LocalTime.of(23, 59, 59));
    }
}
