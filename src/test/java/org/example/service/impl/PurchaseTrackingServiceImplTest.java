package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dto.PurchaseTrackingQueryDTO;
import org.example.dto.PurchaseTrackingSaveDTO;
import org.example.entity.MaterialStock;
import org.example.entity.PurchaseTracking;
import org.example.exception.BusinessException;
import org.example.mapper.MaterialStockMapper;
import org.example.mapper.PurchaseTrackingMapper;
import org.example.feign.ImgFeignClient;
import org.example.util.PurchaseTrackingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购需求详情的服务层单测（变更-091）。
 *
 * <p>覆盖三类：① 必填校验只卡「需求人 + 物料名称」；② 写库前的归一化
 * （null → 空串给 NOT NULL DEFAULT '' 的列，清空可空列写 NULL）；③ **物料登记**的四条规则
 * —— 新编码插进 1503/0000 档、已有编码跳过、没编码不动、删记录不回滚登记。
 *
 * <p>这些断言守的都是"分工"，不是实现细节：例如「已有编码跳过」这条一旦破，
 * 每次保存都会往总表里塞重复的 0000 行，而这在界面上看不出来（总表按编码查仍然查得到）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseTrackingServiceImplTest {

    @Mock
    private PurchaseTrackingMapper baseMapper;

    @Mock
    private MaterialStockMapper materialStockMapper;

    @Mock
    private ImgFeignClient imgFeignClient;

    @InjectMocks
    private PurchaseTrackingServiceImpl service;

    @BeforeEach
    void injectBaseMapper() {
        // ServiceImpl 的 baseMapper 是**父类**的 protected 字段，@InjectMocks 认不出继承来的字段，
        // 这里显式塞一次（与 EquipmentLedgerServiceImplTest 同一做法）。
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private static PurchaseTrackingSaveDTO dto() {
        PurchaseTrackingSaveDTO dto = new PurchaseTrackingSaveDTO();
        dto.setRequester("张三");
        dto.setMaterialName("硅粉");
        dto.setRequestDate(LocalDate.of(2026, 10, 16));
        return dto;
    }

    // ===== 必填 =====

    @Test
    @DisplayName("缺需求人 → 拒（给业务文案，且不写库）")
    void rejectsMissingRequester() {
        PurchaseTrackingSaveDTO dto = dto();
        dto.setRequester("   ");
        BusinessException e = assertThrows(BusinessException.class, () -> service.save(dto));
        assertEquals("需求人不能为空", e.getMessage());
        verify(baseMapper, never()).insert(any(PurchaseTracking.class));
    }

    @Test
    @DisplayName("缺物料名称 → 拒；其余字段（编码/订单号/送货单/到货日期）都可以留空")
    void rejectsMissingMaterialNameOnly() {
        PurchaseTrackingSaveDTO dto = dto();
        dto.setMaterialName(null);
        assertEquals("物料名称不能为空",
                assertThrows(BusinessException.class, () -> service.save(dto)).getMessage());

        // 只留两个必填，其余全空 —— 必须能存进去（编码可以后补、订单号还没下）
        PurchaseTrackingSaveDTO minimal = dto();
        when(baseMapper.insert(any(PurchaseTracking.class))).thenReturn(1);
        when(materialStockMapper.selectCount(any())).thenReturn(0L);
        service.save(minimal);

        ArgumentCaptor<PurchaseTracking> captor = ArgumentCaptor.forClass(PurchaseTracking.class);
        verify(baseMapper).insert(captor.capture());
        PurchaseTracking written = captor.getValue();
        assertEquals("", written.getMaterialCode(), "NOT NULL DEFAULT '' 的列必须是空串而不是 null");
        assertEquals("", written.getOaNo());
        assertEquals("", written.getDeliveryNote());
        assertNull(written.getSpec());
        assertNull(written.getRemark());
        assertNull(written.getRequiredArrivalDate());
        // 变更-096 的 5 个新列：留空时必须是 NULL（页面显示「—」靠它），不是 0 或空串
        assertNull(written.getCategory());
        assertNull(written.getQuantity());
        assertNull(written.getUnit());
        assertNull(written.getReceivedQuantity());
        assertNull(written.getActualArrivalDate());
    }

    @Test
    @DisplayName("★ 数量校验：需求数量必须 > 0；到货数量不能超过需求数量")
    void rejectsInvalidQuantities() {
        PurchaseTrackingSaveDTO zero = dto();
        zero.setQuantity(new BigDecimal("0"));
        assertEquals("需求数量必须大于 0",
                assertThrows(BusinessException.class, () -> service.save(zero)).getMessage());

        PurchaseTrackingSaveDTO negative = dto();
        negative.setReceivedQuantity(new BigDecimal("-1"));
        assertEquals("实际到货数量不能是负数",
                assertThrows(BusinessException.class, () -> service.save(negative)).getMessage());

        PurchaseTrackingSaveDTO over = dto();
        over.setQuantity(new BigDecimal("3"));
        over.setReceivedQuantity(new BigDecimal("5"));
        assertTrue(assertThrows(BusinessException.class, () -> service.save(over))
                .getMessage().startsWith("实际到货数量不能大于需求数量"));

        // 部分到货是合法的：不做拦截
        PurchaseTrackingSaveDTO partial = dto();
        partial.setQuantity(new BigDecimal("3"));
        partial.setReceivedQuantity(new BigDecimal("2"));
        when(baseMapper.insert(any(PurchaseTracking.class))).thenReturn(1);
        when(materialStockMapper.selectCount(any())).thenReturn(0L);
        service.save(partial);
        ArgumentCaptor<PurchaseTracking> captor = ArgumentCaptor.forClass(PurchaseTracking.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(new BigDecimal("2"), captor.getValue().getReceivedQuantity());
    }

    // ===== 物料登记 =====

    @Test
    @DisplayName("★ 新编码：插一行 1503/0000「采购登记（无库存）」，库存 0、带上名称与规格")
    void registersNewMaterialCode() {
        PurchaseTrackingSaveDTO dto = dto();
        dto.setMaterialCode(" 999000111 ");
        dto.setSpec("500kg/袋");
        when(baseMapper.insert(any(PurchaseTracking.class))).thenReturn(1);
        when(materialStockMapper.selectCount(any())).thenReturn(0L);

        service.save(dto);

        ArgumentCaptor<MaterialStock> captor = ArgumentCaptor.forClass(MaterialStock.class);
        verify(materialStockMapper).insert(captor.capture());
        MaterialStock reg = captor.getValue();
        assertEquals("1503", reg.getPlantCode());
        assertEquals("0000", reg.getStorageLocation(), "必须落在采购登记档，不能进任何真实仓");
        assertEquals("采购登记（无库存）", reg.getStorageDesc());
        assertEquals("999000111", reg.getMaterialCode(), "前后空白要去掉");
        assertEquals("硅粉", reg.getMaterialDesc());
        assertEquals("500kg/袋", reg.getSpec());
        assertEquals(0, BigDecimal.ZERO.compareTo(reg.getStockQty()));
        assertNull(reg.getUnit(), "采购记录里没有单位这一列，不许瞎填");
        assertNull(reg.getLastImportTime(), "它不是库存导入来的，不该盖导入时间戳");
    }

    @Test
    @DisplayName("★ 编码已存在（任何库位）→ 不再插 0000 行：总表里已经查得到它了")
    void skipsRegistrationWhenCodeAlreadyKnown() {
        PurchaseTrackingSaveDTO dto = dto();
        dto.setMaterialCode("111001785");
        when(baseMapper.insert(any(PurchaseTracking.class))).thenReturn(1);
        when(materialStockMapper.selectCount(any())).thenReturn(1L);

        service.save(dto);

        verify(materialStockMapper, never()).insert(any(MaterialStock.class));
    }

    @Test
    @DisplayName("没填编码 → 什么都不登记（采购记录允许只写名称）")
    void registersNothingWithoutCode() {
        when(baseMapper.insert(any(PurchaseTracking.class))).thenReturn(1);
        service.save(dto());
        verify(materialStockMapper, never()).insert(any(MaterialStock.class));
        verify(materialStockMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("★ 删除采购记录**不回滚**登记行：编码存在过就不该从总表里消失")
    void deleteKeepsRegistration() {
        when(baseMapper.deleteById(7L)).thenReturn(1);
        service.delete(7L);
        verify(materialStockMapper, never()).delete(any());
    }

    // ===== 两处从评审查出来的加固（变更-093）=====

    @Test
    @DisplayName("★ save 必须带 @Transactional：它写两张表，没事务会出现「行已写入、接口报失败」")
    void saveIsTransactional() throws Exception {
        Method save = PurchaseTrackingServiceImpl.class.getMethod("save", PurchaseTrackingSaveDTO.class);
        assertTrue(
                save.isAnnotationPresent(Transactional.class),
                "save 写两张表（purchase_tracking 插入 + material_stock 采购登记），必须带 @Transactional："
                        + "否则第二张表失败会让采购行已提交却报失败，使用方以为没存上、再录一遍就多一条重复记录。"
                        + "（这条断言的存在意义就是：把这个注解删掉时会有测试变红。）");
    }

    @Test
    @DisplayName("★ 登记撞唯一键（并发登记同一编码）→ 吞掉异常，采购行照样保存成功")
    void toleratesConcurrentRegistration() {
        PurchaseTrackingSaveDTO dto = dto();
        dto.setMaterialCode("NEW-CODE-1");
        when(baseMapper.insert(any(PurchaseTracking.class))).thenReturn(1);
        when(materialStockMapper.selectCount(any())).thenReturn(0L);
        // 模拟：selectCount 与 insert 之间，另一次保存/库存导入刚把这个编码登记进去了
        when(materialStockMapper.insert(any(MaterialStock.class)))
                .thenThrow(new DuplicateKeyException(
                        "Duplicate entry '1503-NEW-CODE-1-0000' for key 'uk_plant_material_location'"));

        assertDoesNotThrow(() -> service.save(dto));
        verify(baseMapper).insert(any(PurchaseTracking.class));
    }

    // ===== 送货单图片（变更-094）=====

    private static PurchaseTracking stored(String deliveryNote) {
        PurchaseTracking row = new PurchaseTracking();
        row.setId(1L);
        row.setDeliveryNote(deliveryNote);
        return row;
    }

    @Test
    @DisplayName("★ 换图：旧文件要删掉（成对操作），新值原样保留")
    void deletesReplacedDeliveryImage() {
        when(baseMapper.selectById(1L)).thenReturn(stored("/files/old.png"));
        when(baseMapper.updateById(any(PurchaseTracking.class))).thenReturn(1);
        when(baseMapper.insert(any(PurchaseTracking.class))).thenReturn(1);

        PurchaseTrackingSaveDTO dto = dto();
        dto.setId(1L);
        dto.setDeliveryNote("/files/new.png");
        service.save(dto);

        verify(imgFeignClient).delete("old.png");
    }

    @Test
    @DisplayName("★ 送货单没变（或从图片改成图片同一张）→ 不许重复删")
    void keepsImageWhenUnchanged() {
        when(baseMapper.selectById(1L)).thenReturn(stored("/files/same.png"));
        when(baseMapper.updateById(any(PurchaseTracking.class))).thenReturn(1);

        PurchaseTrackingSaveDTO dto = dto();
        dto.setId(1L);
        dto.setDeliveryNote("/files/same.png");
        service.save(dto);

        verify(imgFeignClient, never()).delete(any());
    }

    @Test
    @DisplayName("★ 清空送货单 → 旧图也要删（不然它成了永远没人认领的孤儿文件）")
    void deletesImageWhenCleared() {
        when(baseMapper.selectById(1L)).thenReturn(stored("/files/gone.png"));
        when(baseMapper.updateById(any(PurchaseTracking.class))).thenReturn(1);

        PurchaseTrackingSaveDTO dto = dto();
        dto.setId(1L);
        dto.setDeliveryNote("");
        service.save(dto);

        verify(imgFeignClient).delete("gone.png");
    }

    @Test
    @DisplayName("★ 删记录连它的送货单图片一起删")
    void deletesImageOnRecordDelete() {
        when(baseMapper.selectById(2L)).thenReturn(stored("/files/pic.png"));
        when(baseMapper.deleteById(2L)).thenReturn(1);

        service.delete(2L);

        verify(imgFeignClient).delete("pic.png");
    }

    @Test
    @DisplayName("★ 但那一列的老数据是**文本**（原先叫送货单号）→ 绝不能当文件名拿去删")
    void leavesLegacyTextDeliveryNoteAlone() {
        when(baseMapper.selectById(3L)).thenReturn(stored("SH-2026-001"));
        when(baseMapper.deleteById(3L)).thenReturn(1);

        service.delete(3L);

        verify(imgFeignClient, never()).delete(any());
    }

    @Test
    @DisplayName("★ 存储侧抖动（删图抛异常）不许连累保存/删除：库那边已经改完了")
    void ignoresStorageFailureOnDelete() {
        when(baseMapper.selectById(1L)).thenReturn(stored("/files/boom.png"));
        when(baseMapper.updateById(any(PurchaseTracking.class))).thenReturn(1);
        when(imgFeignClient.delete(any())).thenThrow(new RuntimeException("minio down"));

        PurchaseTrackingSaveDTO dto = dto();
        dto.setId(1L);
        dto.setDeliveryNote("/files/next.png");

        assertDoesNotThrow(() -> service.save(dto));
        verify(baseMapper).updateById(any(PurchaseTracking.class));
    }

    // ===== 修改 / 删除的 0 行 =====

    @Test
    @DisplayName("修改：改 0 行 = 该 id 不存在，明确报错（不静默当成功）")
    void reportsMissingRowOnUpdate() {
        PurchaseTrackingSaveDTO dto = dto();
        dto.setId(404L);
        when(baseMapper.updateById(any(PurchaseTracking.class))).thenReturn(0);
        BusinessException e = assertThrows(BusinessException.class, () -> service.save(dto));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
    }

    @Test
    @DisplayName("删除：0 行同样明确报错；缺 id 也拒")
    void reportsMissingRowOnDelete() {
        when(baseMapper.deleteById(404L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> service.delete(404L))
                .getMessage().contains("不存在"));
        assertTrue(assertThrows(BusinessException.class, () -> service.delete(null))
                .getMessage().contains("缺少 id"));
    }

    // ===== 列表 =====

    @Test
    @DisplayName("列表：把实体逐字段转成 VO，并按入参归一化页码（空页也不报错）")
    void listPageConvertsAndNormalizes() {
        PurchaseTracking row = new PurchaseTracking();
        row.setId(3L);
        row.setRequester("李四");
        row.setRequestDate(LocalDate.of(2026, 10, 1));
        row.setMaterialName("氟化塑料瓶");
        row.setMaterialCode("");
        row.setOaNo("");
        row.setDeliveryNote("");

        Page<PurchaseTracking> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(baseMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);

        PurchaseTrackingQueryDTO query = new PurchaseTrackingQueryDTO();
        query.setPageNum(0);   // 非法页码：按第 1 页处理，不抛异常
        query.setPageSize(null);

        var result = service.listPage(query);
        assertEquals(1, result.getRecords().size());
        assertEquals("李四", result.getRecords().get(0).getRequester());
        assertEquals("", result.getRecords().get(0).getMaterialCode());
        assertEquals(1, query.resolvedPageNum());
        assertEquals(10, query.resolvedPageSize());
        // 派生状态必须在 VO 上算好（前端不重算口径）：没 OA 单号 = 待提单
        assertEquals("PENDING_OA", result.getRecords().get(0).getStatus());
        assertEquals("待提单", result.getRecords().get(0).getStatusLabel());
        assertNull(result.getRecords().get(0).getOverdueDays());
    }

    // ===== 派生状态（变更-096）=====

    @Test
    @DisplayName("★ 派生状态六态：待提单 / 等货 / 临期 / 超期 / 已到货 / 部分到货")
    void derivesStatusForEveryBranch() {
        LocalDate today = LocalDate.of(2026, 10, 17);

        // 没 OA 单号 → 待提单（此时有没有到货日期都不算超期，因为还没提单，催不到采购头上）
        PurchaseTracking pendingOa = statusRow("", "", LocalDate.of(2026, 10, 1));
        assertEquals("PENDING_OA", PurchaseTrackingStatus.of(pendingOa, today).name());

        // 有单号、没送货单、要求到货日还很远 → 等货
        PurchaseTracking waiting = statusRow("OA-1", "", today.plusDays(10));
        assertEquals("WAITING", PurchaseTrackingStatus.of(waiting, today).name());

        // 提前 3 天（含当天）→ 临期
        PurchaseTracking dueSoon = statusRow("OA-2", "", today.plusDays(3));
        assertEquals("DUE_SOON", PurchaseTrackingStatus.of(dueSoon, today).name());
        PurchaseTracking dueToday = statusRow("OA-3", "", today);
        assertEquals("DUE_SOON", PurchaseTrackingStatus.of(dueToday, today).name());
        assertEquals(0, PurchaseTrackingStatus.daysToDue(dueToday, today));

        // 过了要求到货日 → 超期，且能算出超期天数
        PurchaseTracking overdue = statusRow("OA-4", "", today.minusDays(5));
        assertEquals("OVERDUE", PurchaseTrackingStatus.of(overdue, today).name());
        assertEquals(5, PurchaseTrackingStatus.overdueDays(overdue, today));

        // 有送货单 → 已到货（到货之后即使晚了很多天也不该继续算超期）
        PurchaseTracking arrived = statusRow("OA-5", "/files/a.png", today.minusDays(30));
        assertEquals("ARRIVED", PurchaseTrackingStatus.of(arrived, today).name());
        assertNull(PurchaseTrackingStatus.overdueDays(arrived, today));

        // 到货数量 < 需求数量 → 部分到货
        PurchaseTracking partial = statusRow("OA-6", "/files/b.png", today.minusDays(30));
        partial.setQuantity(new BigDecimal("3"));
        partial.setReceivedQuantity(new BigDecimal("2"));
        assertEquals("PARTIAL", PurchaseTrackingStatus.of(partial, today).name());

        // 数量缺失时判不出缺口 → 按已到货处理（历史数据没有数量列，不能凭空猜）
        PurchaseTracking noQty = statusRow("OA-7", "/files/c.png", today);
        assertEquals("ARRIVED", PurchaseTrackingStatus.of(noQty, today).name());
    }

    @Test
    @DisplayName("汇总：按派生状态分别计数，总数等于各状态之和")
    void summaryCountsEveryStatus() {
        LocalDate today = LocalDate.now();
        PurchaseTracking overdue = statusRow("OA-1", "", today.minusDays(2));
        PurchaseTracking dueSoon = statusRow("OA-2", "", today.plusDays(1));
        PurchaseTracking arrived = statusRow("OA-3", "/files/a.png", today);
        PurchaseTracking pendingOa = statusRow("", "", null);
        when(baseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(overdue, dueSoon, arrived, pendingOa));

        var summary = service.summary(new org.example.dto.PurchaseTrackingSummaryQueryDTO());
        assertEquals(4, summary.getTotal());
        assertEquals(1, summary.getOverdue());
        assertEquals(1, summary.getDueSoon());
        assertEquals(1, summary.getArrived());
        assertEquals(1, summary.getPendingOa());
        assertEquals(0, summary.getWaiting());
        assertEquals(0, summary.getPartial());
    }

    /** 造一行只关心「OA 单号 / 送货单 / 要求到货日」的记录 */
    private static PurchaseTracking statusRow(String oaNo, String deliveryNote, LocalDate requiredArrivalDate) {
        PurchaseTracking row = new PurchaseTracking();
        row.setRequester("张三");
        row.setMaterialName("球阀");
        row.setOaNo(oaNo);
        row.setDeliveryNote(deliveryNote);
        row.setRequiredArrivalDate(requiredArrivalDate);
        return row;
    }
}
