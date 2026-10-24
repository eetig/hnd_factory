package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.dto.AttendanceDaySaveDTO;
import org.example.dto.AttendanceDayVO;
import org.example.dto.AttendanceMonthVO;
import org.example.dto.AttendanceSettingVO;
import org.example.entity.AttendanceDay;
import org.example.entity.AttendanceSetting;
import org.example.exception.BusinessException;
import org.example.mapper.AttendanceDayMapper;
import org.example.mapper.AttendanceSettingMapper;
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
 * <h2>两件核心事</h2>
 * <ol>
 *   <li><b>首次打开某月自动生成工作日记录</b> —— 使用方要"打开就填好、只改例外"。
 *       生成时用 {@link #settings} 的默认时间（08:00 / 17:30，午休 12:00-13:30）。</li>
 *   <li><b>工作日工时扣掉午休</b> —— 08:00→17:30 是 9.5 小时，但他按 8 小时算：
 *       9.5 − 1.5(午休) = 8。加班日 4 段本身不含午休，直接累加。</li>
 * </ol>
 */
@Service
public class AttendanceServiceImpl implements AttendanceService {

    @Autowired
    private AttendanceDayMapper attendanceDayMapper;

    @Autowired
    private AttendanceSettingMapper attendanceSettingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceMonthVO month(String month, Long userId) {
        YearMonth ym = parseMonth(month);
        AttendanceSettingVO setting = settings(userId);

        Map<LocalDate, AttendanceDay> saved = loadMonthRows(ym, userId);
        // 首次打开：把该月工作日按默认时间铺好（已经存在的绝不覆盖）
        autoFill(ym, userId, setting, saved);

        List<AttendanceDayVO> days = new ArrayList<>(ym.lengthOfMonth());
        AttendanceMonthVO vo = new AttendanceMonthVO();
        vo.setMonth(ym.toString());

        int totalMinutes = 0;
        int missCount = 0;
        int incompleteDays = 0;
        int lunch = lunchMinutes(setting);

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
                if (have + miss < needSeg) {
                    incompleteDays++;
                } else {
                    totalMinutes += minutesOf(day, lunch);
                }
            }

            days.add(day);
        }

        vo.setDays(days);
        vo.setTotalMinutes(totalMinutes);
        vo.setMissCount(missCount);
        vo.setIncompleteDays(incompleteDays);
        vo.setSettings(setting);
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

    @Override
    public AttendanceSettingVO settings(Long userId) {
        AttendanceSetting row = attendanceSettingMapper.selectOne(new LambdaQueryWrapper<AttendanceSetting>()
                .eq(AttendanceSetting::getUserId, userId));
        if (row == null) {
            return AttendanceSettingVO.defaults();
        }
        AttendanceSettingVO vo = new AttendanceSettingVO();
        vo.setWorkStart(row.getWorkStart() == null ? LocalTime.of(8, 0) : row.getWorkStart());
        vo.setWorkEnd(row.getWorkEnd() == null ? LocalTime.of(17, 30) : row.getWorkEnd());
        vo.setLunchStart(row.getLunchStart() == null ? LocalTime.of(12, 0) : row.getLunchStart());
        vo.setLunchEnd(row.getLunchEnd() == null ? LocalTime.of(13, 30) : row.getLunchEnd());
        vo.setStandardMinutes(standardMinutes(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSettings(AttendanceSettingVO vo, Long userId) {
        if (vo == null || vo.getWorkStart() == null || vo.getWorkEnd() == null) {
            throw new BusinessException("上班与下班时间不能为空");
        }
        if (!vo.getWorkStart().isBefore(vo.getWorkEnd())) {
            throw new BusinessException("下班时间必须晚于上班时间");
        }
        if (vo.getLunchStart() != null && vo.getLunchEnd() != null
                && !vo.getLunchStart().isBefore(vo.getLunchEnd())) {
            throw new BusinessException("午休结束必须晚于午休开始");
        }

        AttendanceSetting row = attendanceSettingMapper.selectOne(new LambdaQueryWrapper<AttendanceSetting>()
                .eq(AttendanceSetting::getUserId, userId));
        boolean insert = row == null;
        if (insert) {
            row = new AttendanceSetting();
            row.setUserId(userId);
        }
        row.setWorkStart(vo.getWorkStart());
        row.setWorkEnd(vo.getWorkEnd());
        row.setLunchStart(vo.getLunchStart());
        row.setLunchEnd(vo.getLunchEnd());
        if (insert) {
            attendanceSettingMapper.insert(row);
        } else {
            attendanceSettingMapper.updateById(row);
        }
    }

    // ---------------------------------------------------------------- 内部

    private Map<LocalDate, AttendanceDay> loadMonthRows(YearMonth ym, Long userId) {
        Map<LocalDate, AttendanceDay> saved = new HashMap<>();
        List<AttendanceDay> rows = attendanceDayMapper.selectList(new LambdaQueryWrapper<AttendanceDay>()
                .eq(AttendanceDay::getUserId, userId)
                .between(AttendanceDay::getWorkDate, ym.atDay(1), ym.atEndOfMonth()));
        for (AttendanceDay row : rows) {
            saved.put(row.getWorkDate(), row);
        }
        return saved;
    }

    /**
     * 首次打开某月：把该月**默认工作日**按标准时间铺成已打卡记录。
     *
     * <p>只处理库里还没有的日期（不覆盖任何人工修改）；休息日不落库（它本来就是"不录"）。
     */
    private void autoFill(YearMonth ym, Long userId, AttendanceSettingVO setting, Map<LocalDate, AttendanceDay> saved) {
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            if (saved.containsKey(date)) {
                continue;
            }
            if (!AttendanceCalendar.TYPE_WORK.equals(AttendanceCalendar.defaultType(date))) {
                continue;
            }
            AttendanceDay row = new AttendanceDay();
            row.setUserId(userId);
            row.setWorkDate(date);
            row.setDayType(AttendanceCalendar.TYPE_WORK);
            row.setAmIn(setting.getWorkStart());
            row.setAmOut(setting.getWorkEnd());
            row.setMissAmIn(false);
            row.setMissAmOut(false);
            row.setMissPmIn(false);
            row.setMissPmOut(false);
            row.setNote("");
            attendanceDayMapper.insert(row);
            saved.put(date, row);
        }
    }

    private static int lunchMinutes(AttendanceSettingVO setting) {
        LocalTime from = setting.getLunchStart();
        LocalTime to = setting.getLunchEnd();
        if (from == null || to == null) {
            return 0;
        }
        long minutes = Duration.between(from, to).toMinutes();
        return minutes > 0 ? (int) minutes : 0;
    }

    /** 一个标准工作日的有效工时：下班−上班−午休 */
    private static int standardMinutes(AttendanceSettingVO setting) {
        long gross = Duration.between(setting.getWorkStart(), setting.getWorkEnd()).toMinutes();
        int net = (int) gross - lunchMinutes(setting);
        return Math.max(net, 0);
    }

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
     * <p>工作日 = (amOut − amIn) − **午休**（他的账是 08:00→17:30 记 8 小时）；
     * 加班日 = (amOut − amIn) + (pmOut − pmIn)，四段本身不含午休，不再扣。
     * 任一段缺或为负（录反了）就不计该段 —— 宁少不多：少算会被对账发现，多算会变争议。
     */
    private static int minutesOf(AttendanceDayVO day, int lunch) {
        if (AttendanceCalendar.TYPE_OVERTIME.equals(day.getDayType())) {
            return span(day.getAmIn(), day.getAmOut()) + span(day.getPmIn(), day.getPmOut());
        }
        int gross = span(day.getAmIn(), day.getAmOut());
        if (gross <= 0) {
            return 0;
        }
        return Math.max(gross - lunch, 0);
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
