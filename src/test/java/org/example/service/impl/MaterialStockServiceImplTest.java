package org.example.service.impl;

import org.example.dto.ImportSaveOutcome;
import org.example.entity.MaterialStock;
import org.example.mapper.MaterialStockMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存汇总落库的「快照语义」单测。
 *
 * <p>这段逻辑有两个容易写错、写错又不好发现的地方，都钉在这里：
 * <ol>
 *   <li>清零点必须是 <b>UPDATE 数量</b>，不能是 DELETE 行 —— 用户明确要求
 *       「只清空库存数量，保留物料信息方便日后查询」；</li>
 *   <li>批内同键要 <b>相加</b>（与领料汇总的「重复即报错」相反）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialStockServiceImplTest {

    @Mock
    private MaterialStockMapper baseMapper;

    private MaterialStockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new MaterialStockServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private static MaterialStock row(String plant, String code, String location, String qty) {
        MaterialStock s = new MaterialStock();
        s.setPlantCode(plant);
        s.setMaterialCode(code);
        s.setStorageLocation(location);
        s.setStockQty(new BigDecimal(qty));
        return s;
    }

    private static MaterialStock existing(Long id, String plant, String code, String location) {
        MaterialStock s = row(plant, code, location, "1");
        s.setId(id);
        return s;
    }

    @Test
    @DisplayName("再次导入：按工厂把数量清零用的是 UPDATE，不是删行")
    void zeroesQtyInsteadOfDeleting() {
        when(baseMapper.selectList(any()))
                .thenReturn(List.of(existing(7L, "1503", "111001785", "1001")));
        doReturn(true).when(service).saveBatch(anyList());
        doReturn(true).when(service).updateBatchById(anyList());

        ImportSaveOutcome outcome = service.saveImported(
                List.of(row("1503", "111001785", "1001", "49482")));

        // 清零只能走 update(null, wrapper)：走 delete 会把「本次没出现在文件里的物料」一起删掉
        verify(baseMapper).update(isNull(), any());
        verify(baseMapper, never()).delete(any());

        // 键已存在 -> 更新，而不是新增
        assertEquals(1, outcome.updateCount());
        assertEquals(0, outcome.insertCount());
    }

    @Test
    @DisplayName("文件内同键多行相加成一条；库里没有的走新增")
    void mergesDuplicatesAndSplitsInsertUpdate() {
        when(baseMapper.selectList(any()))
                .thenReturn(List.of(existing(7L, "1503", "111001785", "1001")));
        doReturn(true).when(service).saveBatch(anyList());
        doReturn(true).when(service).updateBatchById(anyList());

        ImportSaveOutcome outcome = service.saveImported(List.of(
                row("1503", "111001785", "1001", "6000.000"),
                row("1503", "111001785", "1001", "9482.000"),
                row("1503", "111001785", "1001", "34000.000"),
                row("1503", "110000001", "1001", "5")));

        ArgumentCaptor<List<MaterialStock>> updated = ArgumentCaptor.forClass(List.class);
        verify(service).updateBatchById(updated.capture());
        assertEquals(1, updated.getValue().size());
        assertEquals(0, new BigDecimal("49482.000").compareTo(updated.getValue().get(0).getStockQty()));

        ArgumentCaptor<List<MaterialStock>> inserted = ArgumentCaptor.forClass(List.class);
        verify(service).saveBatch(inserted.capture());
        assertEquals(1, inserted.getValue().size());
        assertEquals("110000001", inserted.getValue().get(0).getMaterialCode());

        assertEquals(1, outcome.insertCount());
        assertEquals(1, outcome.updateCount());
    }
}
