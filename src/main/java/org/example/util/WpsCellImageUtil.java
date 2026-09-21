package org.example.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 提取 WPS 表格「单元格图片」（DISPIMG）中的内嵌图片。
 *
 * WPS 把单元格图片存在 xlsx 包内：
 *   xl/cellimages.xml        单元格图片清单：ID -> rId，并带 descr（原始文件名）
 *   xl/_rels/cellimages.xml.rels   rId -> media/imageN.jpeg
 *   xl/media/imageN.jpeg      图片二进制
 * 单元格里存的是 =DISPIMG("ID_xxx",1) 文本，EasyExcel 读得到文本、但读不到图片，故单独解析。
 */
public final class WpsCellImageUtil {

    private static final Logger log = LoggerFactory.getLogger(WpsCellImageUtil.class);

    /** 从单元格文本 =DISPIMG("ID_xxx",1) 中取出 ID */
    private static final Pattern DISPIMG_ID = Pattern.compile("DISPIMG\\(\\s*\"([^\"]+)\"");

    private WpsCellImageUtil() {
    }

    /** 一张内嵌图片 */
    public static class ExtractedImage {
        private final byte[] data;          // 图片二进制
        private final String originalName;  // 原始文件名（可能不含扩展名）
        private final String extension;     // 扩展名，如 jpg/png

        public ExtractedImage(byte[] data, String originalName, String extension) {
            this.data = data;
            this.originalName = originalName;
            this.extension = extension;
        }

        public byte[] getData() {
            return data;
        }

        public String getOriginalName() {
            return originalName;
        }

        public String getExtension() {
            return extension;
        }
    }

    /**
     * 解析 xlsx，返回 DISPIMG ID -> 图片。
     * 非 WPS 内嵌图（没有 cellimages.xml）返回空 Map，不报错。
     */
    public static Map<String, ExtractedImage> extract(byte[] xlsxBytes) {
        Map<String, byte[]> media = new HashMap<>();      // media/imageN.jpeg -> bytes
        byte[] cellImagesXml = null;
        byte[] cellImagesRels = null;

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("xl/media/")) {
                    media.put(name, readAll(zin));
                } else if ("xl/cellimages.xml".equals(name)) {
                    cellImagesXml = readAll(zin);
                } else if (name.startsWith("xl/_rels/cellimages.xml.rels")) {
                    cellImagesRels = readAll(zin);
                }
                zin.closeEntry();
            }
        } catch (Exception e) {
            log.warn("解析 xlsx 内嵌图片失败: {}", e.getMessage());
            return Map.of();
        }

        if (cellImagesXml == null || cellImagesRels == null || media.isEmpty()) {
            log.info("该文件不含 WPS 单元格图片（无 cellimages.xml）");
            return Map.of();
        }

        Map<String, String> ridToTarget = parseRelationships(cellImagesRels);
        Map<String, ExtractedImage> result = new LinkedHashMap<>();

        for (CellImage ci : parseCellImages(cellImagesXml)) {
            String target = ridToTarget.get(ci.rid);
            if (target == null) {
                continue;
            }
            // Target 形如 media/image1.jpeg，相对 xl/ 目录
            String mediaKey = target.startsWith("/") ? target.substring(1) : "xl/" + target;
            byte[] data = media.get(mediaKey);
            if (data == null) {
                continue;
            }
            result.put(ci.id, new ExtractedImage(data, ci.descr, extensionOf(target)));
        }

        log.info("提取到 WPS 内嵌图片 {} 张", result.size());
        return result;
    }

    /** 从单元格文本中解析 DISPIMG 的 ID；不是 DISPIMG 返回 null */
    public static String parseDispimgId(String cellText) {
        if (cellText == null) {
            return null;
        }
        Matcher m = DISPIMG_ID.matcher(cellText);
        return m.find() ? m.group(1) : null;
    }

    // ================= 内部解析 =================

    private static class CellImage {
        String id;
        String descr;
        String rid;
    }

    private static List<CellImage> parseCellImages(byte[] xml) {
        List<CellImage> list = new ArrayList<>();
        try {
            Document doc = parseXml(xml);
            NodeList pics = doc.getElementsByTagNameNS("*", "pic");
            for (int i = 0; i < pics.getLength(); i++) {
                Element pic = (Element) pics.item(i);
                CellImage ci = new CellImage();

                NodeList nv = pic.getElementsByTagNameNS("*", "cNvPr");
                if (nv.getLength() > 0) {
                    Element cNvPr = (Element) nv.item(0);
                    ci.id = cNvPr.getAttribute("name");
                    ci.descr = cNvPr.getAttribute("descr");
                }
                NodeList blip = pic.getElementsByTagNameNS("*", "blip");
                if (blip.getLength() > 0) {
                    Element b = (Element) blip.item(0);
                    // 属性名形如 r:embed，按 localName 取
                    for (int a = 0; a < b.getAttributes().getLength(); a++) {
                        if ("embed".equals(b.getAttributes().item(a).getLocalName())) {
                            ci.rid = b.getAttributes().item(a).getNodeValue();
                            break;
                        }
                    }
                }
                if (ci.id != null && ci.rid != null) {
                    list.add(ci);
                }
            }
        } catch (Exception e) {
            log.warn("解析 cellimages.xml 失败: {}", e.getMessage());
        }
        return list;
    }

    private static Map<String, String> parseRelationships(byte[] xml) {
        Map<String, String> map = new HashMap<>();
        try {
            Document doc = parseXml(xml);
            NodeList rels = doc.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < rels.getLength(); i++) {
                Element r = (Element) rels.item(i);
                map.put(r.getAttribute("Id"), r.getAttribute("Target"));
            }
        } catch (Exception e) {
            log.warn("解析 cellimages.xml.rels 失败: {}", e.getMessage());
        }
        return map;
    }

    private static Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // 关闭外部实体，避免 XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml));
    }

    private static String extensionOf(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? "jpg" : path.substring(i + 1).toLowerCase();
    }

    private static byte[] readAll(ZipInputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
