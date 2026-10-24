package org.example.service;

import org.example.dto.AttendanceDaySaveDTO;
import org.example.dto.AttendanceMonthVO;
import org.example.dto.AttendanceSettingVO;

import java.time.LocalDate;

/** 个人考勤台账（2026-10-24，变更-171）。 */
public interface AttendanceService {

    /**
     * 取某月的完整考勤（整月每一天都在，没录的按默认规则补）。
     *
     * <p><b>首次打开某月会自动生成该月的工作日记录</b>（按 {@link #settings} 里的默认时间），
     * 之后他改哪条改哪条 —— 使用方要的是"打开就已经填好"，不是每天手动录。
     *
     * @param month 形如 2026-10；为空取当月
     * @param userId 归属用户
     */
    AttendanceMonthVO month(String month, Long userId);

    /** 保存某天（按 userId + 日期 upsert） */
    void save(AttendanceDaySaveDTO dto, Long userId);

    /** 删除某天（回到默认值） */
    void remove(LocalDate workDate, Long userId);

    /** 读默认时间（没设置过就返回默认 8:00 / 17:30、午休 12:00-13:30） */
    AttendanceSettingVO settings(Long userId);

    /** 改默认时间（只影响之后新生成的月份，已生成的不动） */
    void saveSettings(AttendanceSettingVO vo, Long userId);
}
