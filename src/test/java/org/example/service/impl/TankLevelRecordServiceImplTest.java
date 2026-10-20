package org.example.service.impl;

import org.example.dto.TankLevelSaveDTO;
import org.example.entity.TankLevelRecord;
import org.example.exception.TankLevelDuplicateException;
import org.example.mapper.TankLevelRecordMapper;
import org.example.service.TankLevelImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * 液位记录保存的四条不变式（2026-10-20，变更-127）。
 *
 * <p>这次修的线上故障是「同一天只能存一条」，根因有两层，用例分别钉住：
 * <ol>
 *   <li><b>空容器编号必须落 NULL</b> —— 空串会撞唯一键（一天只能一条），NULL 不会。
 *       实体上的 {@code insertStrategy=ALWAYS} 决定"NULL 真的进了 SQL"，
 *       这里能断言的是送进 Mapper 的实体字段确实是 null（SQL 形态由 MP 保证）。</li>
 *   <li><b>同一天同一容器只在"未确认"时拦</b> —— 拦是为了防手滑重复，
 *       不拦的出口是 {@code confirmDuplicate}，否则月底补录第二次就无路可走。</li>
 * </ol>
 *
 * <p>另有两条边界：不同容器/不同日期不拦；编辑自己不拦（否则改个液位就算重复）。
 */
class TankLevelRecordServiceImplTest {

    private TankLevelRecordMapper baseMapper;
    private TankLevelImageService tankLevelImageService;
    private TankLevelRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        baseMapper = mock(TankLevelRecordMapper.class);
        tankLevelImageService = mock(TankLevelImageService.class);
        service = new TankLevelRecordServiceImpl();
        setField(service, "baseMapper", baseMapper);
        setField(service, "tankLevelImageService", tankLevelImageService);
    }

    // ================= 1. 空容器编号 → NULL =================

    @Test
    @DisplayName("容器编号为空白 → 送进 Mapper 的是 null（不是空串），且照常插入")
    void blankTankCodeIsStoredAsNull() {
        when(baseMapper.insert(any(TankLevelRecord.class))).thenReturn(1);

        TankLevelSaveDTO dto = dto(null, "2026-10-20", "包装车间", "171产品包装罐A/B");
        dto.setTankCode("   ");
        service.saveRecord(dto);

        ArgumentCaptor<TankLevelRecord> captor = ArgumentCaptor.forClass(TankLevelRecord.class);
        verify(baseMapper).insert(captor.capture());
        assertNull(captor.getValue().getTankCode(), "空编号必须归一成 null，空串会撞唯一键");
    }

    @Test
    @DisplayName("容器编号有真实值 → 原样 trim 后保存")
    void realTankCodeIsTrimmed() {
        when(baseMapper.insert(any(TankLevelRecord.class))).thenReturn(1);

        TankLevelSaveDTO dto = dto(null, "2026-10-20", "包装车间", "171产品包装罐A/B");
        dto.setTankCode("  V150-A  ");
        service.saveRecord(dto);

        ArgumentCaptor<TankLevelRecord> captor = ArgumentCaptor.forClass(TankLevelRecord.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("V150-A", captor.getValue().getTankCode());
    }

    // ================= 2. 同一天同一容器：先拦，确认后放行 =================

    @Test
    @DisplayName("同一天同一容器（名称只差空格）→ 抛可继续冲突，且一个字都不写库")
    void sameDaySameTankIsRejectedWithoutConfirm() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                existing(7L, "2026-10-20", "171产品 包装罐A/B", "20")));

        TankLevelSaveDTO dto = dto(null, "2026-10-20", "包装车间", "171产品包装罐A/B");

        TankLevelDuplicateException e =
                assertThrows(TankLevelDuplicateException.class, () -> service.saveRecord(dto));
        assertTrue(e.getMessage().contains("已有一条记录"), "文案要能让用户看懂发生了什么");
        assertTrue(e.getMessage().contains("20 mm"), "文案要带上已存那条的液位，便于判断是不是手滑");

        verify(baseMapper, never()).insert(any(TankLevelRecord.class));
    }

    @Test
    @DisplayName("带上 confirmDuplicate → 跳过重复校验，照常插入")
    void confirmDuplicateBypassesTheCheck() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                existing(7L, "2026-10-20", "171产品包装罐A/B", "20")));
        when(baseMapper.insert(any(TankLevelRecord.class))).thenReturn(1);

        TankLevelSaveDTO dto = dto(null, "2026-10-20", "包装车间", "171产品包装罐A/B");
        dto.setConfirmDuplicate(true);
        service.saveRecord(dto);

        verify(baseMapper).insert(any(TankLevelRecord.class));
    }

    @Test
    @DisplayName("同一天不同容器 → 不拦（月底一天记多台是常态）")
    void sameDayDifferentTankIsAllowed() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                existing(7L, "2026-10-20", "171产品包装罐A/B", "20")));
        when(baseMapper.insert(any(TankLevelRecord.class))).thenReturn(1);

        service.saveRecord(dto(null, "2026-10-20", "包装车间", "四甲粗蒸釜 A/B"));

        verify(baseMapper).insert(any(TankLevelRecord.class));
    }

    @Test
    @DisplayName("同一容器不同日期 → 不拦")
    void differentDateSameTankIsAllowed() {
        // 当天查询按 record_date 过滤，这里模拟"当天没有记录"
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.insert(any(TankLevelRecord.class))).thenReturn(1);

        service.saveRecord(dto(null, "2026-10-21", "包装车间", "171产品包装罐A/B"));

        verify(baseMapper).insert(any(TankLevelRecord.class));
    }

    @Test
    @DisplayName("编辑自己 → 不当作重复（否则改个液位都存不进去）")
    void editingSelfIsNotADuplicate() {
        TankLevelRecord stored = existing(7L, "2026-10-20", "171产品包装罐A/B", "20");
        when(baseMapper.selectById(7L)).thenReturn(stored);
        when(baseMapper.selectList(any())).thenReturn(List.of(stored));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(tankLevelImageService.listImages(7L)).thenReturn(List.of());

        TankLevelSaveDTO dto = dto(7L, "2026-10-20", "包装车间", "171产品包装罐A/B");
        dto.setLevelValue(new BigDecimal("25"));
        service.saveRecord(dto);

        verify(baseMapper).update(any(), any());
        verify(baseMapper, times(0)).insert(any(TankLevelRecord.class));
    }

    @Test
    @DisplayName("编辑时改成与另一台容器同日同名 → 仍然要拦")
    void editingIntoAnotherTanksSlotIsRejected() {
        TankLevelRecord mine = existing(7L, "2026-10-20", "171产品包装罐A/B", "20");
        TankLevelRecord other = existing(8L, "2026-10-20", "四甲粗蒸釜 A/B", "1130");
        when(baseMapper.selectById(7L)).thenReturn(mine);
        when(baseMapper.selectList(any())).thenReturn(List.of(mine, other));

        TankLevelSaveDTO dto = dto(7L, "2026-10-20", "包装车间", "四甲粗蒸釜 A/B");

        assertThrows(TankLevelDuplicateException.class, () -> service.saveRecord(dto));
        verify(baseMapper, never()).update(any(), any());
    }

    // ================= 3. 录入候选（变更-129）=================

    @Test
    @DisplayName("容器名称候选：去空白、去重、丢掉空名")
    void listsDistinctTankNames() {
        TankLevelRecord a = new TankLevelRecord();
        a.setTankName("171产品包装罐A/B");
        TankLevelRecord b = new TankLevelRecord();
        b.setTankName(" 171产品包装罐A/B "); // 前后空白视为同一个
        TankLevelRecord c = new TankLevelRecord();
        c.setTankName("150产品罐");
        TankLevelRecord blank = new TankLevelRecord();
        blank.setTankName("   ");
        TankLevelRecord missing = new TankLevelRecord();

        when(baseMapper.selectList(any())).thenReturn(List.of(a, b, c, blank, missing));

        List<String> names = service.listTankNames();

        assertEquals(2, names.size(), "空白与 null 不能进候选，重复名只留一个");
        assertTrue(names.contains("171产品包装罐A/B"));
        assertTrue(names.contains("150产品罐"));
    }

    // ================= helpers =================

    private static TankLevelSaveDTO dto(Long id, String date, String location, String tankName) {
        TankLevelSaveDTO dto = new TankLevelSaveDTO();
        dto.setId(id);
        dto.setRecordDate(LocalDate.parse(date));
        dto.setLocation(location);
        dto.setTankName(tankName);
        dto.setLevelValue(new BigDecimal("20"));
        return dto;
    }

    private static TankLevelRecord existing(Long id, String date, String tankName, String level) {
        TankLevelRecord record = new TankLevelRecord();
        record.setId(id);
        record.setRecordDate(LocalDate.parse(date));
        record.setLocation("包装车间");
        record.setTankName(tankName);
        record.setLevelValue(new BigDecimal(level));
        return record;
    }
}
