package org.example.service.impl;

import org.example.dto.PickSummaryVO;
import org.example.dto.ProductionInboundVO;
import org.example.dto.WeeklyStatsVO;
import org.example.service.MaterialPickSummaryService;
import org.example.service.ProductionInboundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 周统计汇总的单测（决策-004）。
 *
 * <p>这段逻辑是「把前端那段循环搬到服务端」，**唯一的验收标准就是数字和搬之前一模一样**。
 * 所以这里钉的不是「算法对不对」，而是那几条容易在搬运途中走样的口径：
 * <ol>
 *   <li>闭区间 —— 起止当天都要算进去；</li>
 *   <li><b>日期为 null 的记录要算进去</b>（前端原本是 {@code if (!pickDate) return true}）
 *       —— 这条最反直觉，也最容易被后来人当成 bug「顺手修掉」，改了数字就对不上；</li>
 *   <li>只汇总点名的物料，且点名但查不到的记 0 而不是缺席。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class WeeklyStatsServiceImplTest {

    @Mock
    private MaterialPickSummaryService materialPickSummaryService;

    @Mock
    private ProductionInboundService productionInboundService;

    private WeeklyStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WeeklyStatsServiceImpl();
        ReflectionTestUtils.setField(service, "materialPickSummaryService", materialPickSummaryService);
        ReflectionTestUtils.setField(service, "productionInboundService", productionInboundService);
    }

    private static PickSummaryVO pick(String code, String date, String qty) {
        PickSummaryVO vo = new PickSummaryVO();
        vo.setMaterialCode(code);
        vo.setPickDate(date == null ? null : LocalDate.parse(date));
        vo.setPickQty(new BigDecimal(qty));
        return vo;
    }

    private static ProductionInboundVO inbound(String code, String date, String qty) {
        ProductionInboundVO vo = new ProductionInboundVO();
        vo.setMaterialCode(code);
        vo.setInboundDate(date == null ? null : LocalDate.parse(date));
        vo.setInboundQty(new BigDecimal(qty));
        return vo;
    }

    @Test
    @DisplayName("★ 闭区间：起止当天都算，区间外的不算")
    void sumsWithinClosedRange() {
        when(materialPickSummaryService.listAll()).thenReturn(List.of(
                pick("A", "2026-09-21", "1"),      // 起点前一天 —— 不算
                pick("A", "2026-09-22", "10"),     // 起点当天 —— 算
                pick("A", "2026-09-25", "20"),
                pick("A", "2026-09-28", "30"),     // 终点当天 —— 算
                pick("A", "2026-09-29", "1")));    // 终点后一天 —— 不算

        WeeklyStatsVO vo = service.summarize(
                LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-28"),
                List.of("A"), List.of());

        assertEquals(0, new BigDecimal("60").compareTo(vo.getPickQty().get("A")));
    }

    @Test
    @DisplayName("★ 日期为 null 的领料记录要算进合计（与改造前的前端口径一致）")
    void countsNullDateRecords() {
        when(materialPickSummaryService.listAll()).thenReturn(List.of(
                pick("A", null, "7"),              // 日期列没值 —— 照样计入
                pick("A", "2026-09-25", "3")));

        WeeklyStatsVO vo = service.summarize(
                LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-28"),
                List.of("A"), List.of());

        assertEquals(0, new BigDecimal("10").compareTo(vo.getPickQty().get("A")));
    }

    @Test
    @DisplayName("★ 日期为 null 的入库记录同样计入")
    void countsNullDateInboundRecords() {
        when(productionInboundService.listAll()).thenReturn(List.of(
                inbound("P", null, "150")));

        WeeklyStatsVO vo = service.summarize(
                LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-28"),
                List.of(), List.of("P"));

        assertEquals(0, new BigDecimal("150").compareTo(vo.getInboundQty().get("P")));
    }

    @Test
    @DisplayName("没点名的物料不进结果；点名但查不到的记 0 而不是缺席")
    void onlyRequestedMaterialsAppear() {
        when(materialPickSummaryService.listAll()).thenReturn(List.of(
                pick("A", "2026-09-25", "5"),
                pick("B", "2026-09-25", "9")));    // B 没点名

        WeeklyStatsVO vo = service.summarize(
                LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-28"),
                List.of("A", "C"), List.of());     // C 点名了但一条记录都没有

        assertEquals(2, vo.getPickQty().size());
        assertEquals(0, new BigDecimal("5").compareTo(vo.getPickQty().get("A")));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getPickQty().get("C")));
        assertTrue(vo.getInboundQty().isEmpty());
    }

    @Test
    @DisplayName("物料编码两侧的空格不影响归组（库里存在带空格的脏数据）")
    void trimsMaterialCode() {
        when(materialPickSummaryService.listAll()).thenReturn(List.of(
                pick(" A ", "2026-09-25", "4")));

        WeeklyStatsVO vo = service.summarize(
                LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-28"),
                List.of("A"), List.of());

        assertEquals(0, new BigDecimal("4").compareTo(vo.getPickQty().get("A")));
    }

    @Test
    @DisplayName("不传日期即不限区间（前端把日期清空时的行为）")
    void nullBoundsMeanUnbounded() {
        when(materialPickSummaryService.listAll()).thenReturn(List.of(
                pick("A", "2000-01-01", "2"),
                pick("A", "2099-12-31", "3")));

        WeeklyStatsVO vo = service.summarize(null, null, List.of("A"), List.of());

        assertEquals(0, new BigDecimal("5").compareTo(vo.getPickQty().get("A")));
    }
}
