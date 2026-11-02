package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 图片识别（myocr）的**服务端转发**（2026-10-21，变更-137）。
 *
 * <p>为什么不让前端直接调 {@code /api/ocr/recognize}：
 * <ol>
 *   <li>公网 443 不在本机（本机是内网 192.168.3.7），前面还有一道 relay，它的
 *       {@code /api/ocr} 上游是死的 —— 实测公网直接 502，而内网直连 9091 才能通；</li>
 *   <li>内网站点（:9091）原先也没有 {@code /api/ocr} 的转发，请求会落到 hnd_factory，
 *       得到 404/401。</li>
 * </ol>
 *
 * <p>于是改成：App 只调 {@code /api/ocr-proxy/**}（这个前缀 relay 会转发），
 * 由本服务再用容器名 {@code http://myocr:8085} 去识别。这样**不用改公网 relay**。
 *
 * <p>顺带解决另一个隐患：myocr 的地址不再暴露给客户端，将来换机器只需改这里。
 *
 * <h2>分块识别（2026-11-02，使用方：「我有时候就是图省事，不想走太近」）</h2>
 *
 * <p>实测：站远处拍整台罐时，罐体标牌只占画面约 <b>1.5%</b>（1080×1440 里 205×115px，
 * 字高约 20px），腾讯云 OCR 把它读成「三用瓶组」这种乱猜；同一张图**按 3×3 切块、
 * 每块放大 3 倍**再送，含标牌的那块稳定读出「三甲粗品罐」。
 *
 * <p>所以服务端做切块 + 放大 + 并行识别 + 合并文本，客户端仍只发一张原图
 * ⇒ **用户不用走近拍**，代价是多花几次 OCR（几分钱）和多等 1~2 秒。
 *
 * <p>⚠️ 放大不会凭空长出细节，只是把已有像素喂到 OCR 的可用区间；
 * 真拍糊的照片仍然救不回来 —— 这句话也给使用方说清了。
 */
@Service
public class OcrProxyService {

    private static final Logger log = LoggerFactory.getLogger(OcrProxyService.class);

    /** 容器名而不是 127.0.0.1：hnd_factory 与 myocr 同在 nacos_default 网络（实测容器名可达） */
    private final String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate;

    public OcrProxyService(@Value("${app.ocr.base-url:http://myocr:8085}") String baseUrl) {
        this.baseUrl = baseUrl;

        // 超时：myocr 自己给腾讯留了 70 秒，这里要更长，否则先被我们掐断
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 转发一次识别，返回 myocr 的 {@code data} 节点（前端拿到的就是识别结果）。
     *
     * <p>myocr 的业务错误是 HTTP 200 + {@code code != 200}，这里统一翻成 BusinessException，
     * 让前端只处理一种错误形态。
     */
    public JsonNode recognize(byte[] imageBytes, String fileName) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("没有收到图片");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                // 匿名子类只为了给出文件名：myocr 用它做日志与类型提示
                return fileName == null || fileName.isBlank() ? "tank.jpg" : fileName;
            }
        });
        if (fileName != null && !fileName.isBlank()) {
            body.add("fileName", fileName);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/api/ocr/recognize", new HttpEntity<>(body, headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            int code = root.path("code").asInt(200);
            if (code != 200) {
                String message = root.path("message").asText("识别失败");
                throw new BusinessException(message);
            }
            return root.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("调用 myocr 失败: baseUrl={}, 原因={}", baseUrl, e.getMessage());
            throw new BusinessException("识别服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            log.warn("解析 myocr 响应失败: {}", e.getMessage());
            throw new BusinessException("识别结果异常，请稍后重试");
        }
    }

    /**
     * 分块识别：切成 {@code tiles × tiles} 块、每块放大 3 倍、**并行**送 OCR，
     * 再把所有返回里的字符串合并去重（字段名不稳定，我们只取"字"）。
     *
     * @param tiles 每边的块数；小于 2 时退化成普通单张识别
     * @return {@code { tiled, width, height, texts:[...], tileTexts:[[...]] }}
     */
    public JsonNode recognizeTiled(byte[] imageBytes, String fileName, int tiles) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("没有收到图片");
        }
        if (tiles < 2) {
            return recognize(imageBytes, fileName);
        }
        int n = Math.min(tiles, 4); // 上限 4×4=16 次；再多既不便宜也不会更快

        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            throw new BusinessException("这张图片读不出来，换一张试试");
        }
        if (source == null) {
            throw new BusinessException("这张图片读不出来，换一张试试");
        }

        List<Object[]> pieces;
        try {
            pieces = splitAndUpscale(source, n, 3);
        } catch (IOException e) {
            log.warn("切图失败，退回单张识别: {}", e.getMessage());
            return recognize(imageBytes, fileName);
        }

        List<JsonNode> results = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(6, pieces.size()));
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (Object[] piece : pieces) {
                byte[] bytes = (byte[]) piece[0];
                String name = (String) piece[1];
                futures.add(pool.submit(() -> recognize(bytes, name)));
            }
            for (Future<JsonNode> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    // 单块失败不影响其它块：远处拍的图里，多数块本来就是背景
                    log.warn("分块识别中有一块失败: {}", e.getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }

        if (results.isEmpty()) {
            throw new BusinessException("识别服务暂时不可用，请稍后重试");
        }

        Set<String> texts = new LinkedHashSet<>();
        ArrayNode tileTexts = objectMapper.createArrayNode();
        for (JsonNode result : results) {
            ArrayNode one = objectMapper.createArrayNode();
            collectTexts(result, one);
            one.forEach(node -> texts.add(node.asText()));
            tileTexts.add(one);
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.put("tiled", n);
        out.put("width", source.getWidth());
        out.put("height", source.getHeight());
        ArrayNode textArray = out.putArray("texts");
        texts.forEach(textArray::add);
        out.set("tileTexts", tileTexts);
        return out;
    }

    /** 切成 n×n，每块放大 scale 倍，编码成 JPEG */
    private List<Object[]> splitAndUpscale(BufferedImage source, int n, int scale) throws IOException {
        int width = source.getWidth();
        int height = source.getHeight();
        List<Object[]> pieces = new ArrayList<>();
        int index = 0;
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                int x = width * col / n;
                int y = height * row / n;
                int w = width * (col + 1) / n - x;
                int h = height * (row + 1) / n - y;
                BufferedImage tile = source.getSubimage(x, y, w, h);

                BufferedImage scaled = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.drawImage(tile, 0, 0, w * scale, h * scale, null);
                } finally {
                    graphics.dispose();
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ImageIO.write(scaled, "jpg", buffer);
                pieces.add(new Object[]{buffer.toByteArray(), "tile-" + index + ".jpg"});
                index++;
            }
        }
        return pieces;
    }

    /** 把任意 JSON 节点里的字符串抠出来（字段名不稳定，只要字；数字与状态词滤掉） */
    private void collectTexts(JsonNode node, ArrayNode out) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.length() >= 2 && !"不存在".equals(text) && !"success".equals(text)
                    && !"tencent".equals(text) && !text.matches("^\\d+$")) {
                out.add(text);
            }
            return;
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                collectTexts(child, out);
            }
        }
    }
}
