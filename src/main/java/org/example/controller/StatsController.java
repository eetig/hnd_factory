package org.example.controller;

import org.example.dto.Result;
import org.example.dto.WeeklyStatsVO;
import org.example.service.WeeklyStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 统计类接口。
 *
 * <p>本类目前只有周统计一条（决策-004）：它要的是**聚合数**，不是明细，
 * 所以能在明细接口对非 admin 关闭之后，单独把这一页的数据供应出来。
 * 这是刻意的例外 —— 不要把别的页面也照此办理，聚合接口只用在真有需要的页上。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private WeeklyStatsService weeklyStatsService;

    /**
     * 周统计汇总 —— 查询类接口，免登录（与 /api/stock、/api/tank-level 同级）。
     *
     * <p>⚠️ 这条路径**不要**加进 {@code SaTokenConfigure} 的只读闸门：
     * 周统计页对所有人可见，靠的就是它。闸门拦的是明细（/api/pick/list、/api/inbound/list）。
     *
     * @param start            起始日期 YYYY-MM-DD，不传即不限
     * @param end              结束日期 YYYY-MM-DD，不传即不限
     * @param pickMaterials    要汇总的领料物料编码，逗号分隔
     * @param inboundMaterials 要汇总的入库物料编码，逗号分隔
     */
    @GetMapping("/weekly")
    public Result<WeeklyStatsVO> weekly(
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "pickMaterials", required = false) String pickMaterials,
            @RequestParam(value = "inboundMaterials", required = false) String inboundMaterials) {

        WeeklyStatsVO data = weeklyStatsService.summarize(
                parseDate(start), parseDate(end),
                splitCodes(pickMaterials), splitCodes(inboundMaterials));

        return Result.success("查询成功", data);
    }

    /** 空串与解析不了的值一律当「不限」，与 TankLevelController 的同名工具同口径 */
    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 物料编码清单：逗号分隔，去空白、丢空项（前端拼串时难免出现尾逗号） */
    private List<String> splitCodes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (String part : Arrays.asList(value.split(","))) {
            String code = part.trim();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return codes;
    }
}
