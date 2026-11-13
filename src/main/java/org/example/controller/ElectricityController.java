package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.example.audit.RequestMetadata;
import org.example.dto.Result;
import org.example.entity.ElectricityMeter;
import org.example.exception.BusinessException;
import org.example.service.ElectricityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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

    @Autowired
    private org.example.service.ElectricityImportService importService;

    /** 电表台账（全部 38 块：37 块有编号 + 150车间 1AN28 不编号） */
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
     * 抄表板：某一天 38 块表（37 块有编号 + 150车间 1AN28 不编号）各自的上次读数与当天已填值。
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

    /**
     * 粘贴导入（2026-11-03）：body { rows: "日期\t读数1\t读数2…" 的多行文本 }。
     *
     * 使用方的 Excel 带锁、导出来是乱码，但复制单元格→粘贴通常还能用，
     * 所以入口是文本框粘贴，而不是上传 xlsx。
     */
    @PostMapping("/readings/import")
    public Result<Map<String, Object>> importRows(@RequestBody Map<String, Object> body) {
        requireAdmin();
        String text = body.get("text") == null ? "" : String.valueOf(body.get("text"));
        if (text.isBlank()) {
            throw new BusinessException("没有收到要导入的内容");
        }
        List<String> dates = new ArrayList<>();
        List<List<String>> values = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) continue;
            // Excel 复制是制表符；也容忍逗号/多个空格
            String[] cells = line.split("\\t|,\\s*|\\s{2,}");
            if (cells.length < 2) continue;
            dates.add(cells[0]);
            List<String> row = new ArrayList<>();
            for (int i = 1; i < cells.length; i++) row.add(cells[i]);
            values.add(row);
        }
        if (dates.isEmpty()) {
            throw new BusinessException("没解析出数据行（第一列要是日期）");
        }
        return Result.success(importService.importRows(dates, values, RequestMetadata.username()));
    }

    /**
     * 按车间汇总（2026-11-03）：使用方要的"各个车间求和"。
     * 只算用电量，不含电费。
     */
    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        requireLogin();
        return Result.success(importService.summaryBySection(parseDateOrNull(from), parseDateOrNull(to)));
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

    /**
     * 改一条已抄的读数（抄错字用）。
     *
     * <p>body: { "reading": 608 }
     *
     * <p>为什么读数谁都能改：现场抄错的是抄表的人自己发现的，让他去找管理员才能改，
     * 只会让错数据多留几天。改完会连带重算同表下一条的用量（见 Service 注释），
     * 且这次修改会进审计日志。
     */
    @PutMapping("/readings/{id}")
    public Result<Map<String, Object>> updateReading(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireLogin();
        BigDecimal reading = electricityService.toDecimal(body == null ? null : body.get("reading"));
        return Result.success(electricityService.updateReading(id, reading));
    }

    /** 删一条抄错的读数（那天根本没抄，手滑多录了一条）。删掉会重算下一条，只给管理员 */
    @DeleteMapping("/readings/{id}")
    public Result<Map<String, Object>> deleteReading(@PathVariable Long id) {
        requireAdmin();
        return Result.success(electricityService.deleteReading(id));
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
        if (!ElectricityClerkController.canWriteReadings()) {
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
