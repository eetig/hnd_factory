package org.example.util;

import org.example.constant.WorkOrderTypeEnum;
import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.WorkOrder;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 工单导入：Excel DTO -> 实体 的转换、校验、类型识别。
 */
public final class WorkOrderImportUtil {

    private WorkOrderImportUtil() {
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    public static WorkOrder toEntity(WorkOrderExcelDTO dto) {
        WorkOrder w = new WorkOrder();
        w.setOrderNo(trim(dto.getOrderNo()));
        w.setStorageTank(trim(dto.getStorageTank()));
        w.setPlantCode(trim(dto.getPlantCode()));
        w.setMaterialCode(trim(dto.getMaterialCode()));
        w.setMaterialDesc(trim(dto.getMaterialDesc()));
        w.setTeamName(trim(dto.getTeamName()));
        w.setCustomerBrand(trim(dto.getCustomerBrand()));
        w.setOrderType(trim(dto.getOrderType()));
        w.setMrpController(trim(dto.getMrpController()));
        w.setProducerCount(parseInt(dto.getProducerCount()));
        w.setBatchNo(trim(dto.getBatchNo()));
        w.setOrderQty(parseDecimal(dto.getOrderQty()));
        w.setUnit(trim(dto.getUnit()));
        w.setProdVersion(trim(dto.getProdVersion()));
        w.setPlanStartDate(parseDate(dto.getPlanStartDate()));
        w.setPlanFinishDate(parseDate(dto.getPlanFinishDate()));
        w.setConfirmedQty(parseDecimal(dto.getConfirmedQty()));
        w.setConfQty(parseDecimal(dto.getConfQty()));
        w.setDeliveredQty(parseDecimal(dto.getDeliveredQty()));
        w.setActualFinishDate(parseDate(dto.getActualFinishDate()));
        w.setLastChangedBy(trim(dto.getLastChangedBy()));
        w.setSysStatus(trim(dto.getSysStatus()));
        w.setStorageTankName(trim(dto.getStorageTankName()));
        w.setChangeDate(parseDate(dto.getChangeDate()));
        w.setChangeTime(parseChangeTime(dto.getChangeDate(), dto.getChangeTime()));
        w.setPerBarrelWeight(parseDecimal(dto.getPerBarrelWeight()));
        return w;
    }

    /** 校验工单，返回错误原因（不含行号前缀）；通过返回 null */
    public static String validate(WorkOrder w) {
        if (!StringUtils.hasText(w.getOrderNo())) {
            return "订单号(order_no)为空";
        }
        if (!StringUtils.hasText(w.getMaterialCode())) {
            return "物料编码(material_code)为空";
        }
        return null;
    }

    /** 工单号 -> 类型中文名（识别不出为"未知类型"） */
    public static String typeName(String orderNo) {
        WorkOrderTypeEnum t = WorkOrderTypeEnum.detect(orderNo);
        return t == null ? "未知类型" : t.getTypeName();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static Integer parseInt(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
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

    // 更改时间：用「更改日期 + 更改时间(一天的分数)」拼出完整时间
    private static LocalDateTime parseChangeTime(String dateStr, String timeStr) {
        LocalDate date = parseDate(dateStr);
        if (date == null) {
            return null;
        }
        if (!StringUtils.hasText(timeStr)) {
            return date.atStartOfDay();
        }
        String t = timeStr.trim();
        try {
            double frac = Double.parseDouble(t);
            if (frac >= 0 && frac < 1) {
                long seconds = Math.round(frac * 24 * 3600);
                return date.atStartOfDay().plusSeconds(seconds);
            }
        } catch (NumberFormatException ignored) {
            // try time string below
        }
        try {
            String[] parts = t.split(":");
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            int s = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            return date.atTime(h, m, s);
        } catch (Exception ignored) {
            return date.atStartOfDay();
        }
    }
}
