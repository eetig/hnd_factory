package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.ArchiveCategoryVO;
import org.example.dto.ArchiveDocumentQueryDTO;
import org.example.dto.ArchiveDocumentSaveDTO;
import org.example.dto.ArchiveDocumentVO;
import org.example.dto.ArchiveFileResult;
import org.example.dto.PageResult;
import org.example.dto.Result;
import org.example.entity.ArchiveDocument;
import org.example.feign.FileFeignClient;
import org.example.service.ArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 资料归档（变更-126）。设计决策见 {@code docs/资料归档-开工方案.md}。
 *
 * <p><b>权限口径（Q5）</b>：查询与下载**全员可用**（只要登录 —— 登录由 SaTokenConfigure 的
 * 全局闸门保证），增/改/删要 {@code archive:edit}。该权限位只授 admin，
 * 而且 {@code SaTokenConfigure} 对所有写方法还有一道「非 admin 一律 403」的集中兜底 ——
 * 两层都在，所以"只有 admin 能删"不是靠前端藏按钮。
 *
 * <p><b>为什么上传要转发</b>：file-service 不对外暴露、也没有鉴权注解，
 * 上传一律打到本服务再转发，权限与文件校验都关在这一层（与采购送货单图片同一套）。
 */
@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private static final Logger log = LoggerFactory.getLogger(ArchiveController.class);

    /**
     * 资料允许的扩展名。与 file-service 的白名单保持一致，这里先拦一道给出人能看懂的话。
     *
     * <p>为什么两端都拦：前端拦是为了体验（选错文件立刻提示），本服务拦是因为**它才是入口**
     * （任何客户端都能直接调接口），file-service 那层拦是最后一道。三层都不多余。
     */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "png", "jpg", "jpeg", "txt", "csv", "zip");

    /** 资料上限 30MB —— 必须与 application.yml 的 multipart 上限、file-service 的上限三者一致 */
    private static final long MAX_FILE_BYTES = 30L * 1024 * 1024;

    @Autowired
    private ArchiveService archiveService;

    @Autowired
    private FileFeignClient fileFeignClient;

    // ================= 资料 =================

    @GetMapping("/list")
    public PageResult<ArchiveDocumentVO> list(@Valid ArchiveDocumentQueryDTO query) {
        IPage<ArchiveDocumentVO> page = archiveService.listPage(query);
        return PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    @SaCheckPermission("archive:edit")
    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody ArchiveDocumentSaveDTO dto) {
        return Result.success(archiveService.saveDocument(dto, currentUsername()));
    }

    @SaCheckPermission("archive:edit")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        archiveService.deleteDocument(id);
        return Result.success(null);
    }

    /**
     * 下载。
     *
     * <p>为什么由本服务转发而不是给前端一个 file-service 的 URL：资料是内部文件，
     * 走公共 URL 等于把访问权交给"知道链接的人"；转发才能每次校验登录态。
     * 文件名用 DB 里的 {@code origName} 还原 —— 使用者下载下来应当看到自己当初传的名字，
     * 而不是 {@code <32hex>.pdf}。
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable("id") Long id) {
        ArchiveDocument row = archiveService.findDocument(id);
        byte[] data;
        try {
            data = fileFeignClient.download(row.getFileName());
        } catch (Exception e) {
            log.error("下载资料失败 id={}, fileName={}", id, row.getFileName(), e);
            return ResponseEntity.internalServerError().build();
        }
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(parseMediaType(row.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentOf(row.getOrigName(), row.getFileName()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate")
                .body(data);
    }

    /**
     * 上传：转发给 file-service（**原样存**，不重编码）。
     *
     * <p>前端拿到返回值后，把 fileName/origName/fileSize/mimeType 一并与表单提交到 /save。
     */
    @SaCheckPermission("archive:edit")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ArchiveFileResult> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return Result.error(400, "文件不能超过 30MB");
        }
        String origName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(origName);
        if (!ALLOWED_EXT.contains(ext)) {
            return Result.error(400, "不支持的文件类型：" + (ext.isEmpty() ? "（无扩展名）" : ext));
        }
        var res = fileFeignClient.upload(file);
        // ⚠️ file-service 的返回体是 {code, message, data}（不是本服务的 {success, msg}），
        // 镜像类 ImgResult 就是按它写的 —— 别照本服务的 Result 习惯写 getSuccess()/getMsg()。
        if (res == null || res.getCode() != 200 || res.getData() == null) {
            return Result.error(400, res == null ? "文件服务无响应" : res.getMessage());
        }
        return Result.success(res.getData());
    }

    // ================= 分类 =================

    @GetMapping("/categories")
    public Result<List<ArchiveCategoryVO>> categories() {
        return Result.success(archiveService.categoryTree());
    }

    @SaCheckPermission("archive:edit")
    @PostMapping("/category/save")
    public Result<Long> saveCategory(@RequestBody ArchiveCategorySaveRequest request) {
        return Result.success(archiveService.saveCategory(
                request.getId(), request.getParentId(), request.getName(), request.getSortNo()));
    }

    @SaCheckPermission("archive:edit")
    @DeleteMapping("/category/delete")
    public Result<Void> deleteCategory(@RequestParam("id") Long id) {
        archiveService.deleteCategory(id);
        return Result.success(null);
    }

    /** 分类保存入参（只有四个字段，单独一个静态类，不单开文件） */
    @lombok.Data
    public static class ArchiveCategorySaveRequest {
        private Long id;
        private Long parentId;
        private String name;
        private Integer sortNo;
    }

    // ================= 内部 =================

    private static String currentUsername() {
        try {
            return StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            // 全局闸门保证到这里一定已登录；真取不到也不该让保存失败，留空即可
            return "";
        }
    }

    private static String extensionOf(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1);
    }

    private static MediaType parseMediaType(String mimeType) {
        try {
            if (mimeType != null && !mimeType.isBlank()) {
                return MediaType.parseMediaType(mimeType);
            }
        } catch (Exception e) {
            log.debug("非法 Content-Type，回落到 octet-stream：{}", mimeType);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Content-Disposition：优先给原文件名（UTF-8 + RFC 5987 的 filename*），
     * 同时给一个 ASCII 兜底名，老浏览器也能存下来。
     */
    private static String attachmentOf(String origName, String storedName) {
        String name = (origName == null || origName.isBlank()) ? storedName : origName;
        String cleaned = name.replace("\"", "").replace("\r", "").replace("\n", "");
        String encoded = URLEncoder.encode(cleaned, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + storedName + "\"; filename*=UTF-8''" + encoded;
    }
}
