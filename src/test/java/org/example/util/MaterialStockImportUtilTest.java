package org.example.util;

import com.alibaba.excel.EasyExcel;
import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.MaterialStock;
import org.example.listener.WorkOrderImportExcelListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存汇总导入的映射与校验。
 *
 * <p>最要紧的是**表头到字段/扩展Map 的那一步**：工厂、物料、物料描述、存储地点、
 * 基本计量单位在监听器里有固定映射，而 规格型号 / 非限制使用的库存 / 存储地点描述
 * 是落在 extMap 里的（键名必须与表头逐字相同）。这几个键名靠「看着像」是猜不出来的，
 * 所以这里用 EasyExcel 真写一个 xlsx 再按导入链路读回来钉住。
 */
class MaterialStockImportUtilTest {

    private static final List<List<String>> STOCK_HEAD = List.of(
            List.of("工厂"), List.of("物料"), List.of("物料描述"), List.of("规格型号"),
            List.of("存储地点"), List.of("基本计量单位"), List.of("非限制使用的库存"), List.of("存储地点描述"));

    private WorkOrderImportExcelListener readStockSheet(List<List<Object>> data) throws Exception {
        File file = File.createTempFile("stock-import-", ".xlsx");
        file.deleteOnExit();
        EasyExcel.write(file).head(STOCK_HEAD).sheet("库存").doWrite(data);

        WorkOrderImportExcelListener listener = new WorkOrderImportExcelListener();
        try (FileInputStream in = new FileInputStream(file)) {
            EasyExcel.read(in, listener).sheet().headRowNumber(1).doRead();
        }
        return listener;
    }

    @Test
    @DisplayName("SAP 库存导出：表头 -> 字段 / extMap 全部取得到")
    void mapsStockColumns() throws Exception {
        WorkOrderImportExcelListener listener = readStockSheet(List.of(
                List.of("1503", "111001785", "硅粉", "500KG/袋", "1001", "KG", "6,000.000", "原材料仓")));

        // detectType 就是靠这个表头判「库存汇总」，改列名会让整类文件落到工单汇总去
        assertTrue(listener.getHeaderNameList().contains("非限制使用的库存"));

        MaterialStock s = MaterialStockImportUtil.toEntity(listener.getDataList().get(0));
        assertEquals("1503", s.getPlantCode());
        assertEquals("111001785", s.getMaterialCode());
        assertEquals("硅粉", s.getMaterialDesc());
        assertEquals("500KG/袋", s.getSpec());          // extMap
        assertEquals("1001", s.getStorageLocation());
        assertEquals("KG", s.getUnit());
        assertEquals("原材料仓", s.getStorageDesc());    // extMap
        assertEquals(0, new BigDecimal("6000.000").compareTo(s.getStockQty()));  // 千分位能解析
        assertNull(MaterialStockImportUtil.validate(s));
    }

    @Test
    @DisplayName("缺唯一键组成部分 / 数量解析不出 -> 报错原因可读")
    void validatesStockRow() throws Exception {
        MaterialStock noPlant = MaterialStockImportUtil.toEntity(
                new WorkOrderExcelDTO());
        assertEquals("工厂(plant_code)为空", MaterialStockImportUtil.validate(noPlant));

        WorkOrderImportExcelListener listener = readStockSheet(List.of(
                List.of("1503", "111001785", "硅粉", "500KG/袋", "1001", "KG", "待盘点", "原材料仓")));
        MaterialStock badQty = MaterialStockImportUtil.toEntity(listener.getDataList().get(0));
        assertNull(badQty.getStockQty());
        assertEquals("非限制使用的库存无法解析", MaterialStockImportUtil.validate(badQty));
    }

    @Test
    @DisplayName("同一物料的重复行：调用方（预览/落库）负责相加，工具类只做逐行映射")
    void keepsDuplicateRowsForCallerToMerge() throws Exception {
        WorkOrderImportExcelListener listener = readStockSheet(List.of(
                List.of("1503", "111001785", "硅粉", "500KG/袋", "1001", "KG", "6,000.000", "原材料仓"),
                List.of("1503", "111001785", "硅粉", "500KG/袋", "1001", "KG", "9,482.000", "原材料仓"),
                List.of("1503", "111001785", "硅粉", "500KG/袋", "1001", "KG", "34,000.000", "原材料仓")));

        assertEquals(3, listener.getDataList().size());
        for (WorkOrderExcelDTO dto : listener.getDataList()) {
            assertNotNull(MaterialStockImportUtil.toEntity(dto).getStockQty());
        }
    }
}
