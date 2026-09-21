package org.example.util;

import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.ProductionInbound;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 生产入库单导入：Excel DTO -> ProductionInbound 的转换与校验。
 */
public final class ProductionInboundImportUtil {

    private ProductionInboundImportUtil() {
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    /** 生产入库单文件 -> 实体（fileName 在保存阶段上传图片后回填） */
    public static ProductionInbound toEntity(WorkOrderExcelDTO dto) {
        ProductionInbound p = new ProductionInbound();
        p.setSeqNo(parseInt(dto.getSeqNo()));              // 序号
        p.setDocumentNo(trim(dto.getDocumentNo()));        // 单据号（业务唯一键）
        p.setInboundDate(parseDate(dto.getInboundDate())); // 日期
        p.setMaterialName(trim(dto.getMaterialName()));    // 物料名称
        p.setMaterialCode(trim(dto.getMaterialCode()));    // 物料编码
        p.setInboundQty(parseDecimal(dto.getInboundQty()));// 领料数量 -> 入库数量
        p.setUnit(trim(dto.getUnit()));                    // 单位
        return p;
    }

    /** 校验，返回错误原因；通过返回 null */
    public static String validate(ProductionInbound p) {
        if (!StringUtils.hasText(p.getDocumentNo())) {
            return "单据号(document_no)为空";
        }
        if (!StringUtils.hasText(p.getMaterialCode())) {
            return "物料编码(material_code)为空";
        }
        if (p.getInboundQty() == null) {
            return "入库数量无法解析";
        }
        return null;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static Integer parseInt(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            // 序号列可能是小数形式（如 5.0）
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim().replace(",", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String v = s.trim();
        for (DateTimeFormatter f : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        // 兜底：Excel 日期序列号
        try {
            long serial = (long) Double.parseDouble(v);
            return LocalDate.of(1899, 12, 30).plusDays(serial);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
