package org.example.util;

import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.MaterialMovement;
import org.springframework.util.StringUtils;

import static org.example.util.ExcelValueParser.parseDate;
import static org.example.util.ExcelValueParser.parseDecimal;
import static org.example.util.ExcelValueParser.parseInt;
import static org.example.util.ExcelValueParser.trim;

/**
 * 货物移动导入：Excel DTO -> MaterialMovement 的转换与校验。
 */
public final class GoodsMoveImportUtil {

    private GoodsMoveImportUtil() {
    }

    /** 货物移动文件 -> 物料移动实体 */
    public static MaterialMovement toEntity(WorkOrderExcelDTO dto) {
        MaterialMovement m = new MaterialMovement();
        m.setOrderNo(trim(dto.getOrderNo()));                 // 订单
        m.setMaterialCode(trim(dto.getMaterialCode()));       // 物料
        m.setMaterialDesc(trim(dto.getMaterialDesc()));       // 物料描述
        m.setMaterialDocItem(parseInt(dto.getItem()));        // 项目 -> 物料文档项目号
        m.setBatchNo(trim(dto.getBatchNo()));                 // 批次
        m.setStorageLocation(trim(dto.getStorageLocation())); // 存储地点
        m.setUnit(trim(dto.getUnit()));                       // 基本计量单位
        m.setMovementType(trim(dto.getMoveType()));           // 移动类型
        m.setMaterialDoc(trim(dto.getMaterialDoc()));         // 物料凭证
        m.setCreditFlag(trim(dto.getCreditFlag()));           // 借/贷标识
        m.setQuantity(parseDecimal(dto.getQuantity()));       // 数量
        m.setPostingDate(parseDate(dto.getPostingDate()));    // 过账日期
        // movement_item(货物移动项目行号) 无对应列，留空
        return m;
    }

    /** 校验，返回错误原因；通过返回 null */
    public static String validate(MaterialMovement m) {
        if (!StringUtils.hasText(m.getOrderNo())) {
            return "订单(order_no)为空";
        }
        if (!StringUtils.hasText(m.getMaterialCode())) {
            return "物料(material_code)为空";
        }
        if (m.getQuantity() == null) {
            return "数量无法解析";
        }
        return null;
    }

}
