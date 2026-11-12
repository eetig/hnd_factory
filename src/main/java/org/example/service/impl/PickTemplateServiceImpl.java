package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.dto.PickExportDTO;
import org.example.dto.PickTemplateVO;
import org.example.entity.PickTemplate;
import org.example.exception.BusinessException;
import org.example.mapper.PickTemplateMapper;
import org.example.service.PickTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 领料解析：模板上传（认列）+ 导出（填数）。
 *
 * <h2>认列的边界</h2>
 * 只认 SAP 导出模板里那几列固定说法：{@code 物料/物料编码}、{@code 单据数量}、{@code 单位}、
 * {@code 工厂}、{@code 仓库}。**刻意不猜**：认不出「单据数量」就直接报错，绝不退化成
 * 「写进第一个叫数量的列」—— 模板里往往同时有「需求数量」「提货数量」，写错列到了 SAP 里
 * 表现为数量翻倍或为零，比报错难查得多。
 *
 * <h2>工厂/仓库从哪来</h2>
 * 使用方的模板里已经有一两行示例（把 1503/9001 填好了），这些值就是他要的默认值。
 * 所以导出时**读示例行**取值，再写到新行上；不用在页面里再让他配一遍。
 */
@Service
public class PickTemplateServiceImpl implements PickTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PickTemplateServiceImpl.class);

    /** 数量单元格样式：整数与小数各一套，避免继承模板那一行的 #,##0.000 */
    private CellStyle integerStyle;
    private CellStyle decimalStyle;

    /** 物料列的候选表头（归一化后严格相等） */
    private static final List<String> MATERIAL_ALIASES = List.of("物料", "物料编码", "物料号", "物料代码");
    /** 数量列只认这一个：模板里通常还有「需求数量」「提货数量」，认错就出错数 */
    private static final List<String> QTY_ALIASES = List.of("单据数量");
    private static final List<String> UNIT_ALIASES = List.of("单位");
    private static final List<String> FACTORY_ALIASES = List.of("工厂");
    private static final List<String> WAREHOUSE_ALIASES = List.of("仓库");

    /**
     * 工厂/仓库兜底值。
     *
     * <p>P7 那份模板在示例行里预填了 1503 / 9001，直接照抄即可；但 E1/E5/E6 三份模板的数据区是**空的**
     * （使用方导出来就是干净的空单），读不到示例值。这时不能留空 —— SAP 里工厂/仓库为空的行贴不进去，
     * 等于导出白做。所以按公司实际值兜底，界面上也会把这组值显示出来，便于发现不对。
     */
    private static final String DEFAULT_FACTORY = "1503";
    private static final String DEFAULT_WAREHOUSE = "9001";

    @Autowired
    private PickTemplateMapper pickTemplateMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<PickTemplateVO> list(String dept) {
        QueryWrapper<PickTemplate> qw = new QueryWrapper<>();
        // 列表不取 file_data（几十 KB × N 张），需要字节时按 id 单独查
        qw.select("id", "name", "doc_type", "dept", "header_row", "data_start_row",
                "col_map", "file_name", "enabled", "create_time");
        qw.eq("enabled", 1);
        if (StringUtils.hasText(dept)) {
            qw.and(w -> w.eq("dept", dept).or().isNull("dept").or().eq("dept", ""));
        }
        qw.orderByDesc("id");
        List<PickTemplate> rows = pickTemplateMapper.selectList(qw);
        List<PickTemplateVO> out = new ArrayList<>(rows.size());
        for (PickTemplate t : rows) {
            out.add(toVO(t, null));
        }
        return out;
    }

    @Override
    public PickTemplateVO upload(MultipartFile file, String name, String docType, String dept) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择 SAP 导出的 Excel 模板");
        }
        String original = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "template.xlsx";
        if (!original.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("只支持 .xlsx（SAP 导出的一般就是这个格式）");
        }
        byte[] bytes = file.getBytes();
        if (bytes.length > 5 * 1024 * 1024) {
            throw new BusinessException("模板文件不要超过 5MB");
        }

        int headerRow;
        Map<String, Integer> cols;
        String[] defaults;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            headerRow = findHeaderRow(sheet);
            if (headerRow < 0) {
                throw new BusinessException("没找到表头行：模板里需要有「物料」和「单据数量」这两列");
            }
            cols = detectColumns(sheet.getRow(headerRow));
            defaults = readDefaults(sheet, cols, headerRow + 1);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("这个文件读不出来，确认是 SAP 导出的 xlsx：" + e.getMessage());
        }

        PickTemplate t = new PickTemplate();
        t.setName(StringUtils.hasText(name) ? name.trim() : stripExt(original));
        t.setDocType(StringUtils.hasText(docType) ? docType.trim() : null);
        t.setDept(StringUtils.hasText(dept) ? dept.trim() : null);
        t.setHeaderRow(headerRow);
        t.setDataStartRow(headerRow + 1);
        t.setColMap(objectMapper.writeValueAsString(cols));
        t.setFileName(original);
        t.setFileData(bytes);
        t.setEnabled(1);
        pickTemplateMapper.insert(t);
        log.info("领料模板已入库 id={} name={} dept={} cols={}", t.getId(), t.getName(), t.getDept(), cols);
        return toVO(t, defaults);
    }

    @Override
    public void delete(Long id) {
        pickTemplateMapper.deleteById(id);
    }

    @Override
    public byte[] export(PickExportDTO dto) {
        if (dto == null || dto.getTemplateId() == null) {
            throw new BusinessException("请先选择单据模板");
        }
        if (dto.getOrders() == null || dto.getOrders().isEmpty()) {
            throw new BusinessException("没有可导出的单据");
        }
        PickTemplate t = pickTemplateMapper.selectById(dto.getTemplateId());
        if (t == null || t.getFileData() == null) {
            throw new BusinessException("模板不存在或文件已丢失，请重新上传");
        }
        Map<String, Integer> cols = parseCols(t.getColMap());

        // 一部门一 sheet：按首次出现顺序建，保证导出顺序与核对界面的顺序一致
        Map<String, List<PickExportDTO.Order>> byDept = new LinkedHashMap<>();
        for (PickExportDTO.Order o : dto.getOrders()) {
            if (o.getItems() == null || o.getItems().isEmpty()) {
                continue;
            }
            String dept = firstNonBlank(o.getDept(), t.getDept(), "领料");
            byDept.computeIfAbsent(dept, k -> new ArrayList<>()).add(o);
        }
        if (byDept.isEmpty()) {
            throw new BusinessException("每张单至少要有 1 行物料");
        }

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(t.getFileData()))) {
            Sheet base = wb.getSheetAt(0);
            // cloneSheet 会复制样式与列宽；不 clone 就得自己抄一遍，抄不全就成了「粘进 SAP 变丑」
            List<String> depts = new ArrayList<>(byDept.keySet());
            for (int i = 1; i < depts.size(); i++) {
                wb.cloneSheet(0);
            }
            for (int i = 0; i < depts.size(); i++) {
                wb.setSheetName(i, sheetName(depts.get(i)));
            }
            String[] defaults = readDefaults(wb.getSheetAt(0), cols, t.getDataStartRow());
            if (!StringUtils.hasText(defaults[0])) {
                defaults[0] = DEFAULT_FACTORY;
            }
            if (!StringUtils.hasText(defaults[1])) {
                defaults[1] = DEFAULT_WAREHOUSE;
            }
            for (int i = 0; i < depts.size(); i++) {
                fillSheet(wb.getSheetAt(i), cols, t.getDataStartRow(), defaults, byDept.get(depts.get(i)));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("生成 Excel 失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 认列

    /** 找表头行：第一行里同时有「物料」与「单据数量」的行 */
    private int findHeaderRow(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), 20);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            boolean hasMaterial = false;
            boolean hasQty = false;
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                String text = cellText(row.getCell(c));
                if (MATERIAL_ALIASES.contains(text)) {
                    hasMaterial = true;
                }
                if (QTY_ALIASES.contains(text)) {
                    hasQty = true;
                }
            }
            if (hasMaterial && hasQty) {
                return r;
            }
        }
        return -1;
    }

    private Map<String, Integer> detectColumns(Row header) {
        Map<String, Integer> cols = new LinkedHashMap<>();
        int last = header.getLastCellNum();
        for (int c = 0; c < last; c++) {
            String text = cellText(header.getCell(c));
            if (text.isEmpty()) {
                continue;
            }
            if (!cols.containsKey("material") && MATERIAL_ALIASES.contains(text)) {
                cols.put("material", c);
            } else if (!cols.containsKey("qty") && QTY_ALIASES.contains(text)) {
                cols.put("qty", c);
            } else if (!cols.containsKey("unit") && UNIT_ALIASES.contains(text)) {
                cols.put("unit", c);
            } else if (!cols.containsKey("factory") && FACTORY_ALIASES.contains(text)) {
                cols.put("factory", c);
            } else if (!cols.containsKey("warehouse") && WAREHOUSE_ALIASES.contains(text)) {
                cols.put("warehouse", c);
            }
        }
        if (!cols.containsKey("material")) {
            throw new BusinessException("没认出「物料」列：表头里要有「物料」或「物料编码」");
        }
        if (!cols.containsKey("qty")) {
            throw new BusinessException("没认出「单据数量」列：请确认 SAP 导出的表头里有这一列（不是「需求数量」）");
        }
        return cols;
    }

    /** 从示例数据行读工厂/仓库（模板里预填好的值就是默认值） */
    private String[] readDefaults(Sheet sheet, Map<String, Integer> cols, int dataStartRow) {
        String factory = "";
        String warehouse = "";
        Row row = sheet.getRow(dataStartRow);
        if (row != null) {
            if (cols.containsKey("factory")) {
                factory = cellText(row.getCell(cols.get("factory")));
            }
            if (cols.containsKey("warehouse")) {
                warehouse = cellText(row.getCell(cols.get("warehouse")));
            }
        }
        return new String[]{factory, warehouse};
    }

    // ---------------------------------------------------------------- 填数

    private void fillSheet(Sheet sheet, Map<String, Integer> cols, int dataStartRow,
                           String[] defaults, List<PickExportDTO.Order> orders) {
        Workbook book = sheet.getWorkbook();
        integerStyle = integerStyleOf(book);
        decimalStyle = decimalStyleOf(book);
        int lastRow = sheet.getLastRowNum();
        // 先把模板里的示例行清空（保留样式）：不清会把示例编码留在导出文件里，粘进 SAP 就多领一台泵
        for (int r = dataStartRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Integer c : cols.values()) {
                Cell cell = row.getCell(c);
                if (cell != null) {
                    cell.setBlank();
                }
            }
        }

        int r = dataStartRow;
        for (PickExportDTO.Order order : orders) {
            for (PickExportDTO.Item item : order.getItems()) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    row = sheet.createRow(r);
                }
                writeText(row, cols.get("material"), item.getMaterialCode());
                writeNumber(row, cols.get("qty"), item.getQuantity());
                if (cols.containsKey("unit") && StringUtils.hasText(item.getUnit())) {
                    writeText(row, cols.get("unit"), item.getUnit());
                }
                if (cols.containsKey("factory")) {
                    writeText(row, cols.get("factory"), defaults[0]);
                }
                if (cols.containsKey("warehouse")) {
                    writeText(row, cols.get("warehouse"), defaults[1]);
                }
                r++;
            }
            // 单与单之间空一行（使用方口径），最后一单后面不留空行
            r++;
        }
    }

    private void writeText(Row row, Integer col, String value) {
        if (col == null || value == null) {
            return;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
        }
        cell.setCellValue(value);
    }

    private void writeNumber(Row row, Integer col, Double value) {
        if (col == null) {
            return;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
        }
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value);
            /*
             * 数量格式统一压成"整数"，不看模板那一行原本是 #,##0 还是 #,##0.000：
             * 模板各行格式不一致（有的行 1、有的行 20.000），粘进 SAP 会带着小数点，
             * SAP 的单据数量按整数收，多一位小数就得人工删。
             */
            if (value == Math.rint(value)) {
                cell.setCellStyle(integerStyle);
            } else {
                cell.setCellStyle(decimalStyle);
            }
        }
    }

    private CellStyle integerStyleOf(Workbook book) {
        CellStyle style = book.createCellStyle();
        DataFormat format = book.createDataFormat();
        style.setDataFormat(format.getFormat("0"));
        return style;
    }

    private CellStyle decimalStyleOf(Workbook book) {
        CellStyle style = book.createCellStyle();
        DataFormat format = book.createDataFormat();
        style.setDataFormat(format.getFormat("0.###"));
        return style;
    }

    // ---------------------------------------------------------------- 小工具

    private PickTemplateVO toVO(PickTemplate t, String[] defaults) {
        PickTemplateVO vo = new PickTemplateVO();
        vo.setId(t.getId());
        vo.setName(t.getName());
        vo.setDocType(t.getDocType());
        vo.setDept(t.getDept());
        vo.setHeaderRow(t.getHeaderRow() == null ? null : t.getHeaderRow() + 1);
        vo.setEnabled(t.getEnabled());
        vo.setCreateTime(t.getCreateTime());
        Map<String, Integer> cols = parseCols(t.getColMap());
        StringBuilder sb = new StringBuilder();
        appendCol(sb, "物料", cols.get("material"));
        appendCol(sb, "单据数量", cols.get("qty"));
        appendCol(sb, "单位", cols.get("unit"));
        appendCol(sb, "工厂", cols.get("factory"));
        appendCol(sb, "仓库", cols.get("warehouse"));
        vo.setColumns(sb.toString());
        if (defaults != null) {
            vo.setFactory(StringUtils.hasText(defaults[0]) ? defaults[0] : DEFAULT_FACTORY);
            vo.setWarehouse(StringUtils.hasText(defaults[1]) ? defaults[1] : DEFAULT_WAREHOUSE);
        } else {
            // 列表接口不解析模板文件（几十 KB × N 张，没必要为了一行提示反复读 blob），
            // 直接把导出时实际会写的兜底值告诉界面，免得「界面显示空、导出却有值」这种不一致。
            vo.setFactory(DEFAULT_FACTORY);
            vo.setWarehouse(DEFAULT_WAREHOUSE);
        }
        return vo;
    }

    private void appendCol(StringBuilder sb, String label, Integer col) {
        if (col == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(label).append(" 第").append(col + 1).append("列");
    }

    private Map<String, Integer> parseCols(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Integer>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("模板列映射损坏，请重新上传");
        }
    }

    /** 表头文字归一化：去空白（含全角空格）、去冒号星号、去括号里的必填标记 */
    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter fmt = new DataFormatter();
        String raw;
        if (cell.getCellType() == CellType.NUMERIC) {
            raw = fmt.formatCellValue(cell);
        } else {
            raw = fmt.formatCellValue(cell);
        }
        return raw.replaceAll("[\\s\\u00a0\\u3000]", "")
                .replace("*", "")
                .replace("：", "")
                .replace(":", "");
    }

    private String sheetName(String dept) {
        String s = dept.replaceAll("[\\\\/?*\\[\\]:]", "").trim();
        if (s.isEmpty()) {
            s = "领料";
        }
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return "";
    }
}
