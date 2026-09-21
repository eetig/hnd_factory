package org.example.util;

import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.MaterialPickSummary;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 领料汇总导入：Excel DTO -> MaterialPickSummary 的转换与校验。
 * 列与生产入库单相同（序号/日期/物料名称/物料编码/领料数量/单位/单据），仅落地字段命名不同。
 */
public final class MaterialPickSummaryImportUtil {

    private MaterialPickSummaryImportUtil() {
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    /** 领料汇总文件 -> 实体（fileName 在保存阶段上传图片后回填） */
    public static MaterialPickSummary toEntity(WorkOrderExcelDTO dto) {
        MaterialPickSummary p = new MaterialPickSummary();
        p.setSeqNo(parseInt(dto.getSeqNo()));               // 序号
        p.setDocumentNo(trim(dto.getDocumentNo()));         // 单据号（业务唯一键）
        p.setPickDate(parseDate(dto.getInboundDate()));     // 日期
        p.setMaterialName(trim(dto.getMaterialName()));     // 物料名称
        p.setMaterialCode(trim(dto.getMaterialCode()));     // 物料编码
        p.setPickQty(parseDecimal(dto.getInboundQty()));    // 领料数量
        p.setUnit(trim(dto.getUnit()));                     // 单位
        return p;
    }

    /** 校验，返回错误原因；通过返回 null */
    public static String validate(MaterialPickSummary p) {
        if (!StringUtils.hasText(p.getDocumentNo())) {
            return "单据号(document_no)为空";
        }
        if (!StringUtils.hasText(p.getMaterialCode())) {
            return "物料编码(material_code)为空";
        }
        if (p.getPickQty() == null) {
            return "领料数量无法解析";
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
        try {
            long serial = (long) Double.parseDouble(v);
            return LocalDate.of(1899, 12, 30).plusDays(serial);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
