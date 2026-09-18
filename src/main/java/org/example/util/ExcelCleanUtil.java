package org.example.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 清洗 SAP 导出的 xlsx。
 * 部分 SAP 导出的数值单元格 <v> 值带有首尾空格（如 <v>0 </v>），
 * EasyExcel 底层 new BigDecimal("0 ") 会抛 NumberFormatException。
 * 这里把 sheet XML 里 <v> 的首尾空白去掉，再交给 EasyExcel 解析。
 */
public final class ExcelCleanUtil {

    private ExcelCleanUtil() {
    }

    public static byte[] clean(byte[] xlsxBytes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(xlsxBytes));
             ZipOutputStream zout = new ZipOutputStream(bos)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putNextEntry(new ZipEntry(entry.getName()));
                String name = entry.getName();
                if (name.matches("xl/worksheets/sheet\\d*\\.xml")) {
                    String xml = new String(readAll(zin), StandardCharsets.UTF_8);
                    xml = xml.replaceAll("(?s)<v>\\s*(.*?)\\s*</v>", "<v>$1</v>");
                    zout.write(xml.getBytes(StandardCharsets.UTF_8));
                } else {
                    copy(zin, zout);
                }
                zout.closeEntry();
                zin.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos);
        return bos.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
    }
}
