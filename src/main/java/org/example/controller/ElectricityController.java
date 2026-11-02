package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.example.audit.RequestMetadata;
import org.example.dto.Result;
import org.example.entity.ElectricityMeter;
import org.example.exception.BusinessException;
import org.example.service.ElectricityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 电表台账与抄表（2026-11-02，第一阶段：只记录）。
 *
 * <h2>为什么分成两组接口</h2>
 * <ul>
 *   <li><b>读</b>（台账、抄表板、历史）：任何登录用户都能看 —— 抄表的人不一定是管理员；</li>
 *   <li><b>写台账</b>（改归属、改倍率、停用）：只有管理员，因为它影响以后所有记录的口径；</li>
 *   <li><b>写读数</b>：任何登录用户 —— 现场谁抄谁录。</li>
 * </ul>
 *
 * <p>写读数会自动进审计日志（AuditInterceptor 拦 POST/PUT），所以"谁在哪天录了读数"可追溯。
 */
@RestController
@RequestMapping("/api/electricity")
public class ElectricityController {

    @Autowired
    private ElectricityService electricityService;

    /** 电表台账（全部 37 块） */
    @GetMapping("/meters")
    public Result<List<ElectricityMeter>> meters() {
        requireLogin();
        return Result.success(electricityService.listMeters());
    }

    /** 改一块表：改归属/倍率是"口径级"改动，只给管理员 */
    @PutMapping("/meters/{id}")
    public Result<ElectricityMeter> updateMeter(@PathVariable Long id, @RequestBody ElectricityMeter patch) {
        requireAdmin();
        return Result.success(electricityService.updateMeter(id, patch));
    }

    /**
     * 抄表板：某一天 37 块表各自的上次读数与当天已填值。
     *
     * @param date yyyy-MM-dd，缺省今天
     */
    @GetMapping("/sheet")
    public Result<List<Map<String, Object>>> sheet(@RequestParam(required = false) String date) {
        requireLogin();
        return Result.success(electricityService.sheet(parseDate(date)));
    }

    /**
     * 批量保存某一天的读数。
     *
     * <p>body: { "date": "2026-11-06", "items": [ { "meterId": 1, "reading": 12345.6 } ] }
     */
    @PostMapping("/readings/batch")
    public Result<Map<String, Object>> saveBatch(@RequestBody Map<String, Object> body) {
        requireLogin();
        LocalDate date = parseDate(body.get("date") == null ? null : String.valueOf(body.get("date")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        String operator = RequestMetadata.username();
        return Result.success(electricityService.saveReadings(date, operator, items));
    }

    /** 历史查询 */
    @GetMapping("/readings")
    public Result<List<Map<String, Object>>> readings(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) Long meterId) {
        requireLogin();
        return Result.success(electricityService.history(parseDateOrNull(from), parseDateOrNull(to), section, meterId));
    }

    /** 车间清单（筛选下拉用） */
    @GetMapping("/sections")
    public Result<List<String>> sections() {
        requireLogin();
        return Result.success(electricityService.sections());
    }

    private static void requireLogin() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }
    }

    private static void requireAdmin() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }
        if (!StpUtil.hasRole("admin")) {
            throw new BusinessException("只有管理员能改电表台账");
        }
    }

    private static LocalDate parseDate(String value) {
        LocalDate parsed = parseDateOrNull(value);
        return parsed == null ? LocalDate.now() : parsed;
    }

    private static LocalDate parseDateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
