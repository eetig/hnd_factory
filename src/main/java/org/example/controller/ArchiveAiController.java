package org.example.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.example.dto.Result;
import org.example.entity.ArchiveDocument;
import org.example.exception.BusinessException;
import org.example.feign.FileFeignClient;
import org.example.service.ArchiveAiService;
import org.example.service.ArchiveService;
import org.example.service.ArchiveTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资料归档的「智能」接口（2026-11-05，review S4 拆出来的）。
 *
 * <h2>为什么单开一个控制器</h2>
 * 它们和增删改查不是一类事：{@code ai-suggest} 要调外部模型、要花钱、只给管理员；
 * {@code preview-spreadsheet} 要把二进制解析成表格、要防 zip 炸弹。
 * 塞在 {@code ArchiveController} 里会让那个类同时承担"CRUD + 上传下载 + AI + 解析"四件事
 * （今天已经 271 行、注入四个依赖），与本项目"按领域拆控制器"的惯例相反 ——
 * 电费就是 {@code Electricity / ElectricityBill / ElectricityMonthly} 三个。
 *
 * <p>路径没变（仍在 {@code /api/archive} 下），前端不用改。
 */
@RestController
@RequestMapping("/api/archive")
public class ArchiveAiController {

    private static final Logger log = LoggerFactory.getLogger(ArchiveAiController.class);

    @Autowired
    private ArchiveAiService archiveAiService;

    @Autowired
    private ArchiveTextExtractor archiveTextExtractor;

    @Autowired
    private ArchiveService archiveService;

    @Autowired
    private FileFeignClient fileFeignClient;

/**
     * AI 归类建议（2026-11-05）。
     *
     * <p>只返回建议，不落库 —— 前端拿它预填「新增资料」表单，由人确认后再 save。
     * 归档是留证据的事，静默写错一条，半年后没人说得清是谁改的。
     */
    @PostMapping(value = "/ai-suggest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> aiSuggest(@RequestPart("file") MultipartFile file) throws IOException {
        /*
         * 只给管理员（2026-11-05，使用方："这个功能只能管理员用，如果所有人都能用，我的 token 成本会很高"）。
         * 用显式 hasRole 而不是注解 —— 与本项目其它 admin 闸门同一口径（注解在某些路由上不生效）。
         */
        if (!StpUtil.hasRole("admin")) {
            throw new BusinessException("只有管理员能用 AI 归类");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("没有收到文件");
        }
        Map<String, Object> suggestion = archiveAiService.suggest(
            file.getOriginalFilename(), file.getContentType(), file.getBytes());
        suggestion.put("llmEnabled", archiveAiService.isLlmEnabled());
        return Result.success(suggestion);
    }

/**
     * Excel 预览（2026-11-05）。
     *
     * <p>浏览器没有原生 xlsx 渲染，所以后端读成「表名 + 二维数组」交给前端画表格。
     * **不返回 HTML**：文件内容是不可信输入，拼 HTML 再 v-html 等于自己开 XSS 口子。
     *
     * <p>上限：前 3 个表、每表前 100 行 / 30 列。预览是为了认出这是哪张表，
     * 不是代替 Excel 打开；被截断时响应里 truncated=true。
     */
    @GetMapping("/preview-spreadsheet/{id}")
    public Result<Map<String, Object>> previewSpreadsheet(@PathVariable("id") Long id) {
        org.example.entity.ArchiveDocument row = archiveService.findDocument(id);
        if (row == null || row.getFileName() == null || row.getFileName().isBlank()) {
            throw new BusinessException("这条资料没有电子件");
        }
        byte[] data;
        try {
            data = fileFeignClient.download(row.getFileName());
        } catch (Exception e) {
            log.error("预览 Excel 失败 id={}", id, e);
            throw new BusinessException("文件读取失败");
        }
        java.util.List<java.util.Map<String, Object>> sheets = archiveTextExtractor.readSpreadsheet(row.getOrigName(), data);
        if (sheets == null) {
            throw new BusinessException("这个文件读不出表格（可能是加密或损坏）");
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("sheets", sheets);
        return Result.success(out);
    }
}
