package org.example.service;

import org.example.dto.AttendanceDaySaveDTO;
import org.example.dto.AttendanceMonthVO;

import java.time.LocalDate;

/** 个人考勤台账（2026-10-24，变更-171）。 */
public interface AttendanceService {

    /**
     * 取某月的完整考勤（整月每一天都在，没录的按默认规则补）。
     *
     * @param month 形如 2026-10；为空取当月
     * @param userId 归属用户
     */
    AttendanceMonthVO month(String month, Long userId);

    /** 保存某天（按 userId + 日期 upsert） */
    void save(AttendanceDaySaveDTO dto, Long userId);

    /** 删除某天（回到默认值） */
    void remove(LocalDate workDate, Long userId);
}
