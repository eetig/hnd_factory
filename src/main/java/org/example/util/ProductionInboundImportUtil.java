package org.example.util;

import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.ProductionInbound;
import org.springframework.util.StringUtils;

import static org.example.util.ExcelValueParser.parseDate;
import static org.example.util.ExcelValueParser.parseDecimal;
import static org.example.util.ExcelValueParser.parseInt;
import static org.example.util.ExcelValueParser.trim;

/**
 * 生产入库单导入：Excel DTO -> ProductionInbound 的转换与校验。
 */
public final class ProductionInboundImportUtil {

    private ProductionInboundImportUtil() {
    }

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

}
