package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 资料归档的「AI 归类」建议（2026-11-05）。
 *
 * <h2>使用方的诉求</h2>
 * <blockquote>资料归档是个很麻烦的事，能在里面集成一个 AI 吗，比如 deepseek，
 * 我把文件上传，它自动帮我分类</blockquote>
 *
 * <h2>设计取舍</h2>
 * <ul>
 *   <li><b>只给建议，不自动落库</b>：返回值填进「新增资料」表单，由人确认后再保存。
 *       归档是留证据的事，静默写错一条，半年后没人说得清是谁改的。</li>
 *   <li><b>没有 key 也能用</b>：没配 DeepSeek 时退回文件名/OCR 文本的规则匹配。
 *       准确率低一些，但不至于"没配 key 就完全不能用"。</li>
 *   <li><b>只发必要内容</b>：发给模型的是「文件名 + 识别出的前 2000 字」，
 *       不发整个文件、不发附件、不发路径。</li>
 * </ul>
 *
 * <h2>怎么开真 AI</h2>
 * 在 application.yml / 环境变量里给一个 key 即可，其余不用改：
 * <pre>
 * archive:
 *   ai:
 *     deepseek-key: ${DEEPSEEK_API_KEY:}
 *     model: deepseek-chat
 * </pre>
 */
@Service
public class ArchiveAiService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveAiService.class);

    /** 分类名 → id。跟库里的 archive_category 对齐（7 个一级分类） */
    private static final Map<String, Long> CATEGORY_IDS = new LinkedHashMap<>();

    static {
        CATEGORY_IDS.put("证照与资质", 1L);
        CATEGORY_IDS.put("安全与环保", 2L);
        CATEGORY_IDS.put("设备与图纸", 3L);
        CATEGORY_IDS.put("质量与检验", 4L);
        CATEGORY_IDS.put("台账与记录", 5L);
        CATEGORY_IDS.put("合同与采购", 6L);
        CATEGORY_IDS.put("人员与培训", 7L);
    }

    /**
     * 关键词表：没配 key 时的兜底，也作为给模型的提示词素材。
     * 顺序有意义 —— 越靠前越优先（"安全生产许可证"要落"证照与资质"而不是"安全与环保"）。
     */
    private static final Map<String, String[]> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("证照与资质", new String[] {
            "营业执照", "许可证", "资质", "证书", "认证", "iso", "备案", "批准", "执照", "检验检测机构"
        });
        KEYWORDS.put("安全与环保", new String[] {
            "安全", "环保", "环评", "排污", "消防", "应急", "预案", "危险", "固废", "危废", "检测报告", "职业卫生"
        });
        KEYWORDS.put("设备与图纸", new String[] {
            "图纸", "竣工图", "pi&d", "pid", "设备", "说明书", "manu al", "操作手册", "铭牌", "参数表", "装配图"
        });
        KEYWORDS.put("质量与检验", new String[] {
            "质量", "检验", "检测", "试验", "化验", "合格", "报告", "证书", "校准", "计量"
        });
        KEYWORDS.put("台账与记录", new String[] {
            "台账", "记录", "巡检", "日志", "登记", "清单", "报表", "汇总", "交接班"
        });
        KEYWORDS.put("合同与采购", new String[] {
            "合同", "协议", "订单", "采购", "发票", "报价", "供应商", "付款", "验收单"
        });
        KEYWORDS.put("人员与培训", new String[] {
            "培训", "人员", "花名册", "考勤", "资格", "上岗", "演练", "签到", "考核"
        });
    }

    /** 文号/编号的粗提取：常见形式"编号：XXX""No. XXX""SH-2024-001" */
    private static final Pattern DOC_NO = Pattern.compile(
        "(?:编号|文号|证书号|合同号|No\\.?|NO\\.?)\\s*[:：]?\\s*([A-Za-z0-9\\-—/()（）]{4,40})");

    /** 有效期：2026-12-31 / 2026年12月31日 / 有效期至 2026.12.31 */
    private static final Pattern EXPIRE = Pattern.compile(
        "(?:有效期至|有效期截止|截止日期|到期日|valid\\s*(?:until|to))\\s*[:：]?\\s*"
            + "(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})",
        Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    @Autowired
    private OcrProxyService ocrProxyService;

    @Value("${archive.ai.deepseek-key:}")
    private String deepseekKey;

    @Value("${archive.ai.model:deepseek-chat}")
    private String model;

    @Value("${archive.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    /** 是否已配置可用的模型 key（前端据此显示「AI 归类」是否走真模型） */
    public boolean isLlmEnabled() {
        return deepseekKey != null && !deepseekKey.isBlank();
    }

    /**
     * 给一份文件出归类建议。
     *
     * @param origName 原始文件名（含扩展名）
     * @param mimeType Content-Type，可为空
     * @param bytes    文件字节；图片会送去 OCR 取文字，其它类型只按文件名判断
     */
    public Map<String, Object> suggest(String origName, String mimeType, byte[] bytes) {
        String name = origName == null ? "" : origName;
        String lower = name.toLowerCase(Locale.ROOT);

        String text = "";
        String textSource = "文件名";
        if (isImage(lower, mimeType) && bytes != null && bytes.length > 0) {
            try {
                JsonNode ocr = ocrProxyService.recognize(bytes, name);
                String recognized = firstText(ocr);
                if (recognized != null && !recognized.isBlank()) {
                    text = recognized;
                    textSource = "OCR";
                }
            } catch (Exception e) {
                log.info("归档 AI：OCR 不可用，退回文件名判断（{}）", e.getMessage());
            }
        }

        Map<String, Object> result;
        if (isLlmEnabled()) {
            result = askModel(name, text, textSource);
            if (result != null) {
                return result;
            }
            log.warn("归档 AI：模型调用失败，退回规则匹配");
        }
        return heuristic(name, text, textSource);
    }

    // ─────────────────────────── 真模型 ───────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> askModel(String fileName, String text, String textSource) {
        String categories = String.join("、", CATEGORY_IDS.keySet());
        String snippet = text.length() > 2000 ? text.substring(0, 2000) : text;

        String system = "你是企业资料归档助手。根据文件名和正文片段，判断这份资料属于下面哪个分类，"
            + "并尽量抽取编号、责任人、有效期、原件存放位置。只输出一个 JSON 对象，不要解释。";
        String user = "分类只能是这些之一：" + categories + "\n"
            + "今天是 " + LocalDate.now() + "\n"
            + "文件名：" + fileName + "\n"
            + (snippet.isBlank() ? "正文：（无，只能靠文件名）" : "正文片段：" + snippet) + "\n\n"
            + "输出 JSON，字段：\n"
            + "{\"category\":\"分类名\",\"name\":\"建议的资料名称（去掉扩展名，20 字以内）\","
            + "\"docNo\":\"编号或空串\",\"owner\":\"责任人姓名或空串\","
            + "\"expireDate\":\"YYYY-MM-DD 或空串\",\"originPlace\":\"原件位置或空串\","
            + "\"confidence\":\"high|medium|low\",\"reason\":\"一句话说明判断依据\"}";

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0.1);
            body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
            body.put("response_format", Map.of("type", "json_object"));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + deepseekKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("归档 AI：模型返回 {}：{}", response.statusCode(), abbreviate(response.body()));
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return null;
            }
            JsonNode parsed = objectMapper.readTree(content);

            String category = parsed.path("category").asText("");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("source", "deepseek");
            out.put("model", model);
            out.put("categoryName", CATEGORY_IDS.containsKey(category) ? category : "");
            out.put("categoryId", CATEGORY_IDS.getOrDefault(category, null));
            out.put("name", trim(parsed.path("name").asText(""), 128));
            out.put("docNo", trim(parsed.path("docNo").asText(""), 64));
            out.put("owner", trim(parsed.path("owner").asText(""), 64));
            out.put("expireDate", safeDate(parsed.path("expireDate").asText("")));
            out.put("originPlace", trim(parsed.path("originPlace").asText(""), 128));
            out.put("confidence", normalizeConfidence(parsed.path("confidence").asText("low")));
            out.put("reason", trim(parsed.path("reason").asText(""), 200));
            out.put("textSource", textSource);
            return out;
        } catch (Exception e) {
            log.warn("归档 AI：调用失败 {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────── 规则兜底 ───────────────────────────

    private Map<String, Object> heuristic(String fileName, String text, String textSource) {
        String haystack = (fileName + "\n" + text).toLowerCase(Locale.ROOT);

        String bestCategory = "";
        int bestScore = 0;
        for (Map.Entry<String, String[]> entry : KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (haystack.contains(keyword)) {
                    // 文件名里命中权重更高：文件名是人写的，最能说明这是什么
                    score += fileName.toLowerCase(Locale.ROOT).contains(keyword) ? 3 : 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "rule");
        out.put("model", null);
        out.put("categoryName", bestCategory);
        out.put("categoryId", bestCategory.isEmpty() ? null : CATEGORY_IDS.get(bestCategory));
        out.put("name", suggestName(fileName));
        out.put("docNo", firstMatch(DOC_NO, text, 1));
        out.put("owner", "");
        out.put("expireDate", firstDate(EXPIRE, text));
        out.put("originPlace", "");
        out.put("confidence", bestScore >= 3 ? "medium" : "low");
        out.put("reason", bestScore > 0
            ? "按文件名词频判断（命中 " + bestScore + " 分）"
            : "没有命中关键词，请手动选分类");
        out.put("textSource", textSource);
        return out;
    }

    // ─────────────────────────── 小工具 ───────────────────────────

    private static boolean isImage(String lowerName, String mimeType) {
        if (mimeType != null && mimeType.startsWith("image/")) {
            return true;
        }
        return lowerName.matches(".*\\.(jpe?g|png|bmp|webp|gif|tiff?)$");
    }

    private String firstText(JsonNode ocr) {
        if (ocr == null) {
            return null;
        }
        for (String field : new String[] { "text", "content", "fullText", "result" }) {
            String value = ocr.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        JsonNode lines = ocr.path("lines");
        if (lines.isArray()) {
            List<String> parts = new ArrayList<>();
            lines.forEach(node -> parts.add(node.asText("")));
            return String.join("\n", parts);
        }
        return null;
    }

    private static String suggestName(String fileName) {
        String base = fileName == null ? "" : fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return trim(base, 128);
    }

    private static String firstMatch(Pattern pattern, String text, int group) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return trim(matcher.group(group), 64);
        }
        return "";
    }

    private static String firstDate(Pattern pattern, String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return LocalDate.of(year, month, day).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeDate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeConfidence(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("high") || lower.equals("medium") || lower.equals("low")) {
            return lower;
        }
        return "low";
    }

    private static String trim(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }
}
