package org.example.service.impl;

import org.example.entity.MaterialMaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 物料候选的"一码一行"规则（2026-10-23，变更-159）。
 *
 * <p>钉住的是导入真实主数据（30,434 行）之后暴露的两条：
 * <ol>
 *   <li>一个编码可能有 8 行（主档 + 7 个评估类型），列表候选必须只出一个，
 *       否则 20 个候选位被一个罐吃掉 8 个；</li>
 *   <li>同编码优先保留<b>主档</b>（评估类型为空），因为价格是主档的价。</li>
 * </ol>
 */
class MaterialMasterDedupeTest {

    private static MaterialMaster row(String code, String valuationType, String name) {
        MaterialMaster m = new MaterialMaster();
        m.setMaterialCode(code);
        m.setValuationType(valuationType);
        m.setMaterialName(name);
        return m;
    }

    @Test
    @DisplayName("8 行同编码 → 只留 1 行，且是评估类型为空的『主档』")
    void collapsesValuationRowsToOneMaterial() {
        List<MaterialMaster> rows = List.of(
                row("220000445", "A", "吨桶PE 白色_华耐德"),
                row("220000445", "B", "吨桶PE 白色_华耐德"),
                row("220000445", "", "吨桶PE 白色_华耐德"),
                row("220000445", "翻新", "吨桶PE 白色_华耐德"));

        List<MaterialMaster> deduped = MaterialMasterServiceImpl.dedupeByCode(rows);

        assertEquals(1, deduped.size());
        assertTrue(MaterialMasterServiceImpl.isMainRow(deduped.get(0)), "应保留主档（评估类型为空）那一行");
    }

    @Test
    @DisplayName("不同编码各留一行，顺序按首次出现")
    void keepsDistinctCodesInOrder() {
        List<MaterialMaster> rows = List.of(
                row("115000005", "", "ZS-8738_1000kg罐"),
                row("231013298", "", "法兰"),
                row("115000005", "A", "ZS-8738_1000kg罐"));

        List<MaterialMaster> deduped = MaterialMasterServiceImpl.dedupeByCode(rows);

        assertEquals(2, deduped.size());
        assertEquals("115000005", deduped.get(0).getMaterialCode());
        assertEquals("231013298", deduped.get(1).getMaterialCode());
    }

    @Test
    @DisplayName("空编码的行被丢掉（脏数据不进候选）")
    void dropsBlankCodes() {
        List<MaterialMaster> rows = List.of(row("", "", "脏行"), row("115000005", "", "正常"));

        List<MaterialMaster> deduped = MaterialMasterServiceImpl.dedupeByCode(rows);

        assertEquals(1, deduped.size());
        assertEquals("115000005", deduped.get(0).getMaterialCode());
    }
}
