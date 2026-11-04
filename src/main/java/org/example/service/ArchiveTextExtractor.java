package org.example.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 从资料文件里抽出正文（2026-11-05）。
 *
 * <h2>为什么需要它</h2>
 * 资料归档的 AI 归类，一开始只有图片能拿到文字（走 myocr），PDF / Word / Excel 只能靠文件名。
 * 但真正有价值的字段 —— 合同编号、许可证有效期、甲方名称 —— 几乎都在正文里。
 * 所以补这一层：把正文抽出来交给模型，编号和有效期才抽得到。
 *
 * <h2>边界</h2>
 * <ul>
 *   <li>只读，不解析版式：拿到的是"文字流"，不保留表格位置。归类够用，做不了版面还原。</li>
 *   <li>有上限：单文件 20 MB、每类只取前 20000 字符，防止一个几百页的 PDF 把内存和 token 打爆。</li>
 *   <li>失败即返回 null：老式 .doc（HWPF）、加密 PDF、图片型 PDF（扫描件没有文字层）都抽不到，
 *       这时上层退回"按文件名判断"，并在理由里说明"正文缺失"。</li>
 * </ul>
 */
@Service
public class ArchiveTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ArchiveTextExtractor.class);

    /** 超过这个大小就不抽了（正文抽取对内存不友好） */
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    /** 单次最多返回多少字符（模型侧也只截前 2000 字，这里留余量给正则抽字段） */
    private static final int MAX_CHARS = 20000;

    /**
     * 抽正文。抽不到返回 null（调用方据此退回文件名判断）。
     *
     * @param fileName 原始文件名，用来判类型（比 Content-Type 可靠：微信/钉钉传上来的常常是 octet-stream）
     * @param mimeType Content-Type，可为空
     * @param bytes    文件字节
     */
    public String extract(String fileName, String mimeType, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            return null;
        }
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        try {
            String text;
            if (lower.endsWith(".pdf") || "application/pdf".equals(mimeType)) {
                text = fromPdf(bytes);
            } else if (lower.endsWith(".docx")) {
                text = fromDocx(bytes);
            } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                text = fromWorkbook(bytes);
            } else if (lower.endsWith(".txt") || lower.endsWith(".csv") || lower.endsWith(".md")) {
                text = new String(bytes, StandardCharsets.UTF_8);
            } else {
                return null;
            }
            if (text == null) {
                return null;
            }
            text = text.replace("\u0000", "").trim();
            if (text.isEmpty()) {
                return null;
            }
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        } catch (Exception e) {
            // 加密 PDF、损坏文件、老式 .doc —— 都走这里，不抛给上层
            log.info("资料正文抽取失败（{}）：{}", fileName, e.getMessage());
            return null;
        }
    }

    private String fromPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                log.info("PDF 有加密，跳过正文抽取");
                return null;
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            // 先只读前 15 页：合同/证照的编号和有效期都在前面，读全本没意义还慢
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(15, Math.max(1, document.getNumberOfPages())));
            return stripper.getText(document);
        }
    }

    private String fromDocx(byte[] bytes) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String line = paragraph.getText();
                if (line != null && !line.isBlank()) {
                    builder.append(line).append('\n');
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String value = cell.getText();
                        if (value != null && !value.isBlank()) {
                            if (rowText.length() > 0) {
                                rowText.append(" | ");
                            }
                            rowText.append(value.trim());
                        }
                    }
                    if (rowText.length() > 0) {
                        builder.append(rowText).append('\n');
                    }
                }
            }
        }
        return builder.toString();
    }

    private String fromWorkbook(byte[] bytes) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Iterator<Sheet> sheets = workbook.sheetIterator();
            int sheetCount = 0;
            while (sheets.hasNext() && sheetCount < 5) {
                Sheet sheet = sheets.next();
                sheetCount++;
                builder.append("# ").append(sheet.getSheetName()).append('\n');
                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount >= 200) {
                        break;
                    }
                    rowCount++;
                    StringBuilder rowText = new StringBuilder();
                    for (Cell cell : row) {
                        String value = cellValue(cell);
                        if (!value.isEmpty()) {
                            if (rowText.length() > 0) {
                                rowText.append(" | ");
                            }
                            rowText.append(value);
                        }
                    }
                    if (rowText.length() > 0) {
                        builder.append(rowText).append('\n');
                    }
                    if (builder.length() > MAX_CHARS) {
                        return builder.toString();
                    }
                }
            }
        }
        return builder.toString();
    }


    /**
     * 把 Excel 读成「表名 + 二维数组」，给前端画预览表格用（2026-11-05）。
     *
     * <p>不返回 HTML：文件内容是不可信输入，拼 HTML 再 v-html 等于自己开 XSS 口子。
     * 这里只取数据，转义交给前端框架。
     *
     * @return 表列表；不是 Excel 或读不出时返回 null
     */
    public List<Map<String, Object>> readSpreadsheet(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            return null;
        }
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv")) {
            return null;
        }
        List<Map<String, Object>> sheets = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            int sheetIndex = 0;
            for (Sheet sheet : workbook) {
                if (sheetIndex >= 3) {
                    break;
                }
                sheetIndex++;
                List<List<String>> rows = new ArrayList<>();
                int rowCount = 0;
                boolean truncated = false;
                for (Row row : sheet) {
                    if (rowCount >= 100) {
                        truncated = true;
                        break;
                    }
                    rowCount++;
                    List<String> cells = new ArrayList<>();
                    int lastCol = row.getLastCellNum();
                    for (int c = 0; c < Math.min(lastCol, 30); c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cells.add(cell == null ? "" : cellValue(cell));
                    }
                    rows.add(cells);
                }
                if (sheet.getLastRowNum() + 1 > 100) {
                    truncated = true;
                }
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("name", sheet.getSheetName());
                one.put("rows", rows);
                one.put("truncated", truncated);
                sheets.add(one);
            }
            return sheets;
        } catch (Exception e) {
            log.info("Excel 预览读取失败（{}）：{}", fileName, e.getMessage());
            return null;
        }
    }

    private String cellValue(Cell cell) {
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> {
                    double value = cell.getNumericCellValue();
                    yield value == Math.floor(value) && !Double.isInfinite(value)
                        ? String.valueOf((long) value)
                        : String.valueOf(value);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> cell.getCellFormula();
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
