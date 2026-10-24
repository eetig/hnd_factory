package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.AttendanceDaySaveDTO;
import org.example.dto.AttendanceDayVO;
import org.example.dto.AttendanceMonthVO;
import org.example.entity.AttendanceDay;
import org.example.exception.BusinessException;
import org.example.mapper.AttendanceDayMapper;
import org.example.service.AttendanceService;
import org.example.util.AttendanceCalendar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人考勤台账实现（2026-10-24，变更-171）。
 *
 * <p>核心一件事：**返回整月每一天**。库里存的是"例外"（只存用户真的录过/改过的日子），
 * 其余天用 {@link AttendanceCalendar#defaultType} 补默认值 ——
 * 这样使用方打开就是一份填好的月历，只改有变动的那几天（他的原话要求）。
 */
@Service
public class AttendanceServiceImpl implements AttendanceService {

    @Autowired
    private AttendanceDayMapper attendanceDayMapper;

    @Override
    public AttendanceMonthVO month(String month, Long userId) {
        YearMonth ym = parseMonth(month);
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();

        Map<LocalDate, AttendanceDay> saved = new HashMap<>();
        List<AttendanceDay> rows = attendanceDayMapper.selectList(new LambdaQueryWrapper<AttendanceDay>()
                .eq(AttendanceDay::getUserId, userId)
                .between(AttendanceDay::getWorkDate, first, last));
        for (AttendanceDay row : rows) {
            saved.put(row.getWorkDate(), row);
        }

        List<AttendanceDayVO> days = new ArrayList<>(ym.lengthOfMonth());
        AttendanceMonthVO vo = new AttendanceMonthVO();
        vo.setMonth(ym.toString());

        int totalMinutes = 0;
        int missCount = 0;
        int incompleteDays = 0;

        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            AttendanceDay row = saved.get(date);

            AttendanceDayVO day = new AttendanceDayVO();
            day.setWorkDate(date);
            if (row == null) {
                day.setDayType(AttendanceCalendar.defaultType(date));
                day.setPersisted(false);
                day.setMissAmIn(false);
                day.setMissAmOut(false);
                day.setMissPmIn(false);
                day.setMissPmOut(false);
            } else {
                day.setDayType(normalizeType(row.getDayType()));
                day.setPersisted(true);
                day.setAmIn(row.getAmIn());
                day.setAmOut(row.getAmOut());
                day.setPmIn(row.getPmIn());
                day.setPmOut(row.getPmOut());
                day.setMissAmIn(Boolean.TRUE.equals(row.getMissAmIn()));
                day.setMissAmOut(Boolean.TRUE.equals(row.getMissAmOut()));
                day.setMissPmIn(Boolean.TRUE.equals(row.getMissPmIn()));
                day.setMissPmOut(Boolean.TRUE.equals(row.getMissPmOut()));
                day.setNote(row.getNote());
            }

            boolean needsTimes = AttendanceCalendar.needsTimes(day.getDayType());
            int needSeg = AttendanceCalendar.segments(day.getDayType());

            if (needsTimes) {
                int miss = countMiss(day);
                int have = countTimes(day);
                missCount += miss;
                // 缺段：该录的段数 > 实际录的段数 + 标忘的段数
                if (have + miss < needSeg) {
                    incompleteDays++;
                } else {
                    totalMinutes += minutesOf(day);
                }
            }

            days.add(day);
        }

        vo.setDays(days);
        vo.setTotalMinutes(totalMinutes);
        vo.setMissCount(missCount);
        vo.setIncompleteDays(incompleteDays);
        vo.setWorkDays((int) days.stream()
                .filter(it -> AttendanceCalendar.TYPE_WORK.equals(it.getDayType()))
                .filter(it -> countTimes(it) + countMiss(it) >= AttendanceCalendar.segments(it.getDayType()))
                .filter(it -> countTimes(it) > 0)
                .count());
        vo.setOvertimeDays((int) days.stream()
                .filter(it -> AttendanceCalendar.TYPE_OVERTIME.equals(it.getDayType()))
                .filter(it -> countTimes(it) > 0)
                .count());
        vo.setRestDays((int) days.stream()
                .filter(it -> AttendanceCalendar.TYPE_REST.equals(it.getDayType()))
                .count());
        vo.setCompDays((int) days.stream()
                .filter(it -> AttendanceCalendar.TYPE_COMP.equals(it.getDayType()))
                .count());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(AttendanceDaySaveDTO dto, Long userId) {
        if (dto == null || dto.getWorkDate() == null) {
            throw new BusinessException("日期不能为空");
        }
        String type = normalizeType(dto.getDayType());
        if (!AttendanceCalendar.TYPE_WORK.equals(type)
                && !AttendanceCalendar.TYPE_OVERTIME.equals(type)
                && !AttendanceCalendar.TYPE_REST.equals(type)
                && !AttendanceCalendar.TYPE_COMP.equals(type)) {
            throw new BusinessException("不识别的类型：" + dto.getDayType());
        }

        AttendanceDay existing = attendanceDayMapper.selectOne(new LambdaQueryWrapper<AttendanceDay>()
                .eq(AttendanceDay::getUserId, userId)
                .eq(AttendanceDay::getWorkDate, dto.getWorkDate()));

        AttendanceDay row = existing == null ? new AttendanceDay() : existing;
        row.setUserId(userId);
        row.setWorkDate(dto.getWorkDate());
        row.setDayType(type);
        row.setAmIn(dto.getAmIn());
        row.setAmOut(dto.getAmOut());
        row.setPmIn(dto.getPmIn());
        row.setPmOut(dto.getPmOut());
        row.setMissAmIn(Boolean.TRUE.equals(dto.getMissAmIn()));
        row.setMissAmOut(Boolean.TRUE.equals(dto.getMissAmOut()));
        row.setMissPmIn(Boolean.TRUE.equals(dto.getMissPmIn()));
        row.setMissPmOut(Boolean.TRUE.equals(dto.getMissPmOut()));
        row.setNote(trim(dto.getNote()));

        if (existing == null) {
            attendanceDayMapper.insert(row);
        } else {
            attendanceDayMapper.updateById(row);
        }
    }

    @Override
    public void remove(LocalDate workDate, Long userId) {
        if (workDate == null) {
            throw new BusinessException("日期不能为空");
        }
        attendanceDayMapper.delete(new LambdaQueryWrapper<AttendanceDay>()
                .eq(AttendanceDay::getUserId, userId)
                .eq(AttendanceDay::getWorkDate, workDate));
    }

    // ---------------------------------------------------------------- 工具

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (RuntimeException e) {
            throw new BusinessException("月份格式应为 YYYY-MM：" + month);
        }
    }

    private static String normalizeType(String type) {
        return type == null ? AttendanceCalendar.TYPE_WORK : type.trim().toLowerCase();
    }

    private static int countTimes(AttendanceDayVO day) {
        int n = 0;
        if (day.getAmIn() != null) {
            n++;
        }
        if (day.getAmOut() != null) {
            n++;
        }
        if (day.getPmIn() != null) {
            n++;
        }
        if (day.getPmOut() != null) {
            n++;
        }
        return n;
    }

    private static int countMiss(AttendanceDayVO day) {
        int n = 0;
        if (Boolean.TRUE.equals(day.getMissAmIn())) {
            n++;
        }
        if (Boolean.TRUE.equals(day.getMissAmOut())) {
            n++;
        }
        if (Boolean.TRUE.equals(day.getMissPmIn())) {
            n++;
        }
        if (Boolean.TRUE.equals(day.getMissPmOut())) {
            n++;
        }
        return n;
    }

    /**
     * 一天的分钟数。
     *
     * <p>工作日 = amOut − amIn；加班日 = (amOut − amIn) + (pmOut − pmIn)。
     * 任一段缺或为负（录反了）就**不计该段**，宁少不多 —— 和人事对账时"少算"会被发现并核对，
     * "多算"会变成争议。
     */
    private static int minutesOf(AttendanceDayVO day) {
        int total = 0;
        total += span(day.getAmIn(), day.getAmOut());
        if (AttendanceCalendar.TYPE_OVERTIME.equals(day.getDayType())) {
            total += span(day.getPmIn(), day.getPmOut());
        }
        return total;
    }

    private static int span(LocalTime from, LocalTime to) {
        if (from == null || to == null) {
            return 0;
        }
        long minutes = Duration.between(from, to).toMinutes();
        return minutes > 0 ? (int) minutes : 0;
    }

    private static String trim(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 255 ? t.substring(0, 255) : t;
    }
}
