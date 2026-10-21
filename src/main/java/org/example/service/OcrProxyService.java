package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 于是改成：App 只调 {@code /api/tank-level/ocr-tank-name}（这个前缀 relay 会转发），
 * 由本服务再用容器名 {@code http://myocr:8085} 去识别。这样**不用改公网 relay**。
 *
 * <p>顺带解决另一个隐患：myocr 的地址不再暴露给客户端，将来换机器只需改这里。
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
}
