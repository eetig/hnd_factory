package org.example.util;

import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.MaterialPickSummary;
import org.springframework.util.StringUtils;

import static org.example.util.ExcelValueParser.parseDate;
import static org.example.util.ExcelValueParser.parseDecimal;
import static org.example.util.ExcelValueParser.parseInt;
import static org.example.util.ExcelValueParser.trim;

/**
 * 领料汇总导入：Excel DTO -> MaterialPickSummary 的转换与校验。
 * 列与生产入库单相同（序号/日期/物料名称/物料编码/领料数量/单位/单据），仅落地字段命名不同。
 */
public final class MaterialPickSummaryImportUtil {

    private MaterialPickSummaryImportUtil() {
    }

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

}
