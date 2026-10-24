package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.example.dto.AttendanceDaySaveDTO;
import org.example.dto.AttendanceMonthVO;
import org.example.dto.AttendanceSettingVO;
import org.example.dto.Result;
import org.example.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 个人考勤台账（2026-10-24，变更-171）。
 *
 * <p>数据**按登录人隔离**：接口里一律用 {@code StpUtil.getLoginIdAsLong()} 取归属，
 * 不接受前端传 userId（否则一个人能看到/改到别人的考勤）。
 *
 * <p>鉴权：查询要求登录即可（只返回自己的数据，没有越权面）；
 * 写操作要求 {@code attendance:edit}，与全站"写操作单独授权"的口径一致。
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    /** 某月整月考勤（含默认值 + 统计）；month 形如 2026-10，缺省为当月 */
    @GetMapping("/month")
    public Result<AttendanceMonthVO> month(@RequestParam(required = false) String month) {
        return Result.success(attendanceService.month(month, currentUserId()));
    }

    /** 保存某天（按日期 upsert） */
    @PostMapping("/save")
    @SaCheckPermission("attendance:edit")
    public Result<Void> save(@RequestBody @Valid AttendanceDaySaveDTO dto) {
        attendanceService.save(dto, currentUserId());
        return Result.success(null);
    }

    /** 删除某天，回到默认值 */
    @DeleteMapping("/day")
    @SaCheckPermission("attendance:edit")
    public Result<Void> remove(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        attendanceService.remove(date, currentUserId());
        return Result.success(null);
    }

    /** 默认时间（上班/下班/午休）；没设置过返回 8:00 / 17:30、午休 12:00-13:30 */
    @GetMapping("/settings")
    public Result<AttendanceSettingVO> settings() {
        return Result.success(attendanceService.settings(currentUserId()));
    }

    /** 改默认时间：只影响之后新生成的月份，已生成的不动 */
    @PostMapping("/settings")
    @SaCheckPermission("attendance:edit")
    public Result<Void> saveSettings(@RequestBody AttendanceSettingVO vo) {
        attendanceService.saveSettings(vo, currentUserId());
        return Result.success(null);
    }

    private Long currentUserId() {
        return Long.valueOf(StpUtil.getLoginIdAsString());
    }
}
