package org.example.util;

import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.MaterialPickSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 领料汇总导入的映射与校验。
 *
 * <p>这个工具类此前**没有单测**（全仓唯二没有的两个 ImportUtil 之一），而它正是
 * 「同一份数字在库存汇总能解析、在这里报错」的那一侧。这里把两件事钉住：
 * 列到字段的对应关系（日期取的是 {@code inbound_date}、数量落在 {@code pick_qty}），
 * 以及千分位容错口径与其它单据类型一致。
 */
class MaterialPickSummaryImportUtilTest {

    private WorkOrderExcelDTO dto(String seqNo, String documentNo, String date,
                                  String name, String code, String qty, String unit) {
        WorkOrderExcelDTO dto = new WorkOrderExcelDTO();
        dto.setSeqNo(seqNo);
        dto.setDocumentNo(documentNo);
        dto.setInboundDate(date);
        dto.setMaterialName(name);
        dto.setMaterialCode(code);
        dto.setInboundQty(qty);
        dto.setUnit(unit);
        return dto;
    }

    @Test
    @DisplayName("列 -> 字段：序号/单据号/日期/物料/数量/单位各就各位，校验通过")
    void mapsPickColumns() {
        MaterialPickSummary p = MaterialPickSummaryImportUtil.toEntity(
                dto("1", "490031271", "2026-08-06", "硅粉", "111001785", "1,000.5", "KG"));

        assertEquals(1, p.getSeqNo().intValue());
        assertEquals("490031271", p.getDocumentNo());
        assertEquals(LocalDate.of(2026, 8, 6), p.getPickDate());
        assertEquals("硅粉", p.getMaterialName());
        assertEquals("111001785", p.getMaterialCode());
        assertEquals("KG", p.getUnit());
        assertEquals(0, new BigDecimal("1000.5").compareTo(p.getPickQty()));
        assertNull(MaterialPickSummaryImportUtil.validate(p));
    }

    @Test
    @DisplayName("序号写成 5.0、数量带全角千分位：与库存汇总同口径，不再整行失败")
    void acceptsExcelStyleNumbers() {
        MaterialPickSummary p = MaterialPickSummaryImportUtil.toEntity(
                dto("5.0", "490031271", "20260806", "硅粉", "111001785", "1，000.5", "KG"));

        assertEquals(5, p.getSeqNo().intValue());
        assertEquals(0, new BigDecimal("1000.5").compareTo(p.getPickQty()));
        assertNull(MaterialPickSummaryImportUtil.validate(p));
    }

    @Test
    @DisplayName("缺唯一键 / 数量解析不出 -> 报错原因可读")
    void reportsMissingKeys() {
        assertEquals("单据号(document_no)为空",
                MaterialPickSummaryImportUtil.validate(
                        MaterialPickSummaryImportUtil.toEntity(dto("1", " ", "2026-08-06", "硅粉", "111001785", "10", "KG"))));
        assertEquals("物料编码(material_code)为空",
                MaterialPickSummaryImportUtil.validate(
                        MaterialPickSummaryImportUtil.toEntity(dto("1", "490031271", "2026-08-06", "硅粉", null, "10", "KG"))));
        assertEquals("领料数量无法解析",
                MaterialPickSummaryImportUtil.validate(
                        MaterialPickSummaryImportUtil.toEntity(dto("1", "490031271", "2026-08-06", "硅粉", "111001785", "待盘点", "KG"))));
    }
}
