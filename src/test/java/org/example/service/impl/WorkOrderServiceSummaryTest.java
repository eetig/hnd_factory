package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.dto.WorkOrderGroupVO;
import org.example.dto.WorkOrderQueryDTO;
import org.example.entity.WorkOrder;
import org.example.mapper.WorkOrderImageMapper;
import org.example.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工单「按 工单类型 + 产成品 汇总」单测 —— 工单报工与工单核算两张聚合表的服务端数据源。
 *
 * <p>为什么要有这个文件：改造前这套「分组求和」是在**前端**做的
 * （{@code useStatsData.buildReportRows}，那时它拿的是全量工单数组，有单测覆盖）。
 * 列表改后端分页之后前端只剩当前页，拿它分组求和会得出「前 10 条的合计」，
 * 所以分组搬到了这里 —— **口径要跟着搬走一份测试**，否则这段逻辑就没有任何守卫了
 * （前端那两个 build* 现在只能钉展示层：排序、空值兜底、名称归一化）。
 *
 * <p>钉住三件事：
 * <ol>
 *   <li><b>分组维度</b>是「工单号前缀 + 产成品」，且两个数量列各自 SUM —— 漏掉前缀就等于
 *       把操作工单和包装工单的同一产成品合成一行，数字看着正常但语义错了；</li>
 *   <li><b>前缀 → 中文类型名</b>的映射只在服务端做一份（前端拿的是中文名与 typeKey，
 *       不是裸前缀）；认不出的前缀给空串而不是硬塞一个类型；</li>
 *   <li><b>SUM 为 NULL 折成 0</b> —— 该组全为 NULL 时 SUM 回 NULL，直接落进 JSON
 *       会让前端 {@code Number(undefined)} 变 NaN，整行数字变成 NaN。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderServiceSummaryTest {

    @Mock
    private WorkOrderMapper baseMapper;

    @Mock
    private WorkOrderImageMapper workOrderImageMapper;

    private WorkOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new WorkOrderServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        ReflectionTestUtils.setField(service, "workOrderImageMapper", workOrderImageMapper);
    }

    /**
     * 跑一次汇总查询，把它生成的 SQL 片段（WHERE / GROUP BY / ORDER BY）取回来。
     *
     * <p>captor 声明成 {@code Wrapper<WorkOrder>} 而不是 {@code QueryWrapper<?>}：
     * 后者过不了 {@code selectMaps(Wrapper<WorkOrder>)} 的类型检查。
     */
    @SuppressWarnings("unchecked")
    private String captureSummarySql() {
        ArgumentCaptor<Wrapper<WorkOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).selectMaps(captor.capture());
        Wrapper<WorkOrder> wrapper = captor.getValue();

        // ⚠️ getSqlSegment() **不含 SELECT 列表**，只有 WHERE / GROUP BY / ORDER BY。
        // 分开查过一次：SUM 那两条断言永远失败，很容易误判成「代码里没写 SUM」。
        // 要校验列与别名就得把 getSqlSelect() 一并拼上。
        String select = wrapper instanceof QueryWrapper
                ? ((QueryWrapper<WorkOrder>) wrapper).getSqlSelect()
                : "";
        return select + " " + wrapper.getSqlSegment();
    }

    /** 一条 selectMaps 结果行（列标签 → 值），键名与 SQL 里的别名一致 */
    private static Map<String, Object> row(String prefix, String materialDesc,
                                           Object orderQty, Object confirmedQty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("order_no_prefix", prefix);
        map.put("material_desc", materialDesc);
        map.put("order_qty", orderQty);
        map.put("confirmed_qty", confirmedQty);
        return map;
    }

    private static WorkOrderQueryDTO query() {
        WorkOrderQueryDTO query = new WorkOrderQueryDTO();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setStartDate("2026-09-01");
        query.setEndDate("2026-10-17");
        return query;
    }

    @Test
    @DisplayName("分组维度：按 LEFT(order_no,4) + material_desc，两个数量列各自 SUM")
    void groupsByOrderNoPrefixAndMaterial() {
        when(baseMapper.selectMaps(any())).thenReturn(List.of(row("1000", "HND-V150", 100, 40)));

        service.summarizeByTypeAndMaterial(query());

        String sql = captureSummarySql();

        assertTrue(sql.contains("LEFT(order_no, 4)"), "分组键少了工单号前缀：" + sql);
        assertTrue(sql.contains("material_desc"), "分组键少了产成品：" + sql);
        assertTrue(sql.toUpperCase().contains("GROUP BY"), "没有 GROUP BY：" + sql);
        // 两个 SUM 都要在 —— 工单报工要 quantity 与 confirmed 两组数
        assertTrue(sql.contains("order_qty"), "少了订单数量的 SUM：" + sql);
        assertTrue(sql.contains("confirmed_qty"), "少了确认产量的 SUM：" + sql);
    }

    @Test
    @DisplayName("筛选条件与列表接口一致（日期区间 / 工单类型 / 产成品 / 工单号）")
    void appliesTheSameFiltersAsTheList() {
        when(baseMapper.selectMaps(any())).thenReturn(List.of());
        WorkOrderQueryDTO query = query();
        query.setType("operate");
        query.setMaterialDesc("HND-V150");
        query.setOrderNo("1000001271");

        service.summarizeByTypeAndMaterial(query);

        String sql = captureSummarySql();

        assertTrue(sql.contains("plan_start_date"), "没按基本开始日期过滤：" + sql);
        assertTrue(sql.contains("order_no"), "没按工单号（类型前缀 / 精确工单号）过滤：" + sql);
        assertTrue(sql.contains("material_desc"), "没按产成品过滤：" + sql);
    }

    @Test
    @DisplayName("前缀映射成中文类型名与 typeKey（前端拿到的是类型，不是裸前缀）")
    void mapsPrefixToTypeNameAndKey() {
        when(baseMapper.selectMaps(any())).thenReturn(List.of(
                row("1000", "HND-V150", 100, 40),
                row("2000", "HND-V171", 5, 3),
                row("3000", "HND-V150", 1, 1),
                row("4000", "HND-V150", 2, 2)));

        List<WorkOrderGroupVO> rows = service.summarizeByTypeAndMaterial(query());

        assertEquals(4, rows.size());
        assertEquals("操作工单", rows.get(0).getOrderType());
        assertEquals("operate", rows.get(0).getTypeKey());
        assertEquals("包装工单", rows.get(1).getOrderType());
        assertEquals("package", rows.get(1).getTypeKey());
        assertEquals("转桶工单", rows.get(2).getOrderType());
        assertEquals("返工工单", rows.get(3).getOrderType());
    }

    @Test
    @DisplayName("认不出的工单号前缀：类型给空串（前端据此跳过整行），不硬塞一个类型")
    void unknownPrefixGetsEmptyType() {
        when(baseMapper.selectMaps(any())).thenReturn(List.of(row("9000", "HND-V150", 10, 10)));

        List<WorkOrderGroupVO> rows = service.summarizeByTypeAndMaterial(query());

        assertEquals(1, rows.size());
        assertEquals("", rows.get(0).getOrderType());
        assertEquals("", rows.get(0).getTypeKey());
        // 数量照常带出来 —— 不因为认不出类型就把整行丢掉
        assertEquals(0, new BigDecimal("10").compareTo(rows.get(0).getOrderQty()));
    }

    @Test
    @DisplayName("SUM 回 NULL（该组数量全是 NULL）折成 0，不能留 null 给前端")
    void nullSumBecomesZero() {
        // 真库里 SUM 在「整组都是 NULL」时回的是 NULL，不是 0 ——
        // 直接把 null 丢给前端，那一行会显示成 NaN
        when(baseMapper.selectMaps(any())).thenReturn(List.of(row("1000", "HND-V150", null, null)));

        WorkOrderGroupVO row = service.summarizeByTypeAndMaterial(query()).get(0);

        assertEquals(0, BigDecimal.ZERO.compareTo(row.getOrderQty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(row.getConfirmedQty()));
    }

    @Test
    @DisplayName("数量按字符串回传时也能解析（JDBC 驱动给 BigDecimal/字符串两种都可能）")
    void parsesStringNumbers() {
        when(baseMapper.selectMaps(any())).thenReturn(List.of(row("1000", "HND-V150", "12.5", "3")));

        WorkOrderGroupVO row = service.summarizeByTypeAndMaterial(query()).get(0);

        assertEquals(0, new BigDecimal("12.5").compareTo(row.getOrderQty()));
        assertEquals(0, new BigDecimal("3").compareTo(row.getConfirmedQty()));
    }

    @Test
    @DisplayName("产成品名称为空的行也要带出类型（分组键可能为 NULL）")
    void emptyMaterialDescIsTolerated() {
        when(baseMapper.selectMaps(any())).thenReturn(List.of(row("1000", null, 1, 1)));

        WorkOrderGroupVO row = service.summarizeByTypeAndMaterial(query()).get(0);

        assertEquals("", row.getMaterialName());
        assertEquals("操作工单", row.getOrderType());
    }
}
