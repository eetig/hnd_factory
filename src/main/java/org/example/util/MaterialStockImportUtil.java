package org.example.util;

import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.MaterialStock;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 库存汇总导入：Excel DTO -> MaterialStock 的转换与校验。
 *
 * <p>列（SAP 库存导出）：工厂 / 物料 / 物料描述 / 规格型号 / 存储地点 / 基本计量单位 /
 * 非限制使用的库存 / 存储地点描述。
 *
 * <p>其中 工厂、物料、物料描述、存储地点、基本计量单位 在
 * {@link org.example.listener.WorkOrderImportExcelListener} 里已有固定映射；
 * **规格型号 / 非限制使用的库存 / 存储地点描述** 没有对应字段，落在 dto 的 extMap 里
 * （监听器的 default 分支兜底），这里按键名取。
 */
public final class MaterialStockImportUtil {

    private MaterialStockImportUtil() {
    }

    /** extMap 的键就是（归一化后的）表头名 */
    private static final String COL_SPEC = "规格型号";
    private static final String COL_STOCK_QTY = "非限制使用的库存";
    private static final String COL_STORAGE_DESC = "存储地点描述";

    public static MaterialStock toEntity(WorkOrderExcelDTO dto) {
        MaterialStock s = new MaterialStock();
        s.setPlantCode(trim(dto.getPlantCode()));             // 工厂
        s.setMaterialCode(trim(dto.getMaterialCode()));       // 物料
        s.setMaterialDesc(trim(dto.getMaterialDesc()));       // 物料描述
        s.setSpec(trim(ext(dto, COL_SPEC)));                  // 规格型号
        s.setStorageLocation(trim(dto.getStorageLocation())); // 存储地点
        s.setStorageDesc(trim(ext(dto, COL_STORAGE_DESC)));   // 存储地点描述
        s.setUnit(trim(dto.getUnit()));                       // 基本计量单位
        s.setStockQty(parseDecimal(ext(dto, COL_STOCK_QTY))); // 非限制使用的库存
        return s;
    }

    /** 校验，返回错误原因；通过返回 null */
    public static String validate(MaterialStock s) {
        // 工厂与存储地点都是唯一键的组成部分，缺了会让不同仓库的行挤成一条，宁可整行报错
        if (!StringUtils.hasText(s.getPlantCode())) {
            return "工厂(plant_code)为空";
        }
        if (!StringUtils.hasText(s.getMaterialCode())) {
            return "物料(material_code)为空";
        }
        if (!StringUtils.hasText(s.getStorageLocation())) {
            return "存储地点(storage_location)为空";
        }
        if (s.getStockQty() == null) {
            return "非限制使用的库存无法解析";
        }
        return null;
    }

    private static String ext(WorkOrderExcelDTO dto, String header) {
        Object value = dto.getExtMap() == null ? null : dto.getExtMap().get(header);
        return value == null ? null : String.valueOf(value);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** 千分位（6,000.000）与空白都要能解析 —— 与 ImportUtil 系列的容错口径一致 */
    private static BigDecimal parseDecimal(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim().replace(",", "").replace("，", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
