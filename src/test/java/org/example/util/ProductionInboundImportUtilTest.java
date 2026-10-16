package org.example.util;

import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.ProductionInbound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 生产入库单导入的映射与校验。
 *
 * <p>这个工具类此前**没有单测**（全仓唯二没有的两个 ImportUtil 之一）。除了列到字段的
 * 对应关系，这里特别钉两条容易踩的：
 * <ul>
 *   <li>入库数量列在共享 DTO 上叫 {@code inboundQty}（列名是「领料数量」），
 *       改成别的字段名会让整列静默变 null；</li>
 *   <li>日期列支持 5 种写法与 Excel 序列号 —— 序列号那一档是 {@code ExcelValueParser} 的兜底，
 *       迁移到共享解析器后仍要生效。</li>
 * </ul>
 */
class ProductionInboundImportUtilTest {

    private WorkOrderExcelDTO dto(String seqNo, String date, String name, String code, String qty, String unit) {
        WorkOrderExcelDTO dto = new WorkOrderExcelDTO();
        dto.setSeqNo(seqNo);
        dto.setDocumentNo("490031271");
        dto.setInboundDate(date);
        dto.setMaterialName(name);
        dto.setMaterialCode(code);
        dto.setInboundQty(qty);
        dto.setUnit(unit);
        return dto;
    }

    @Test
    @DisplayName("列 -> 字段：领料数量列落在 inboundQty / inbound_qty，校验通过")
    void mapsInboundColumns() {
        ProductionInbound p = ProductionInboundImportUtil.toEntity(
                dto("2", "2026-08-06", "硅粉", "111001785", "1,000.5", "KG"));

        assertEquals(2, p.getSeqNo().intValue());
        assertEquals("490031271", p.getDocumentNo());
        assertEquals(LocalDate.of(2026, 8, 6), p.getInboundDate());
        assertEquals("硅粉", p.getMaterialName());
        assertEquals("111001785", p.getMaterialCode());
        assertEquals("KG", p.getUnit());
        assertEquals(0, new BigDecimal("1000.5").compareTo(p.getInboundQty()));
        assertNull(ProductionInboundImportUtil.validate(p));
    }

    @Test
    @DisplayName("日期：五写法与 Excel 序列号都能落地（序列号 = 1970-01-01）")
    void parsesDateVariants() {
        assertEquals(LocalDate.of(2026, 8, 6),
                ProductionInboundImportUtil.toEntity(dto("1", "2026.8.6", "硅粉", "111001785", "10", "KG")).getInboundDate());
        assertEquals(LocalDate.of(2026, 8, 6),
                ProductionInboundImportUtil.toEntity(dto("1", "20260806", "硅粉", "111001785", "10", "KG")).getInboundDate());
        assertEquals(LocalDate.of(1970, 1, 1),
                ProductionInboundImportUtil.toEntity(dto("1", "25569", "硅粉", "111001785", "10", "KG")).getInboundDate());
        assertNull(ProductionInboundImportUtil.toEntity(dto("1", "待确认", "硅粉", "111001785", "10", "KG")).getInboundDate());
    }

    @Test
    @DisplayName("缺唯一键 / 数量解析不出 -> 报错原因可读")
    void reportsMissingKeys() {
        WorkOrderExcelDTO blankDoc = dto("1", "2026-08-06", "硅粉", "111001785", "10", "KG");
        blankDoc.setDocumentNo(" ");
        assertEquals("单据号(document_no)为空",
                ProductionInboundImportUtil.validate(ProductionInboundImportUtil.toEntity(blankDoc)));

        assertEquals("物料编码(material_code)为空",
                ProductionInboundImportUtil.validate(
                        ProductionInboundImportUtil.toEntity(dto("1", "2026-08-06", "硅粉", null, "10", "KG"))));

        assertEquals("入库数量无法解析",
                ProductionInboundImportUtil.validate(
                        ProductionInboundImportUtil.toEntity(dto("1", "2026-08-06", "硅粉", "111001785", "待盘点", "KG"))));
    }
}
