package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import org.example.dto.PickExportDTO;
import org.example.dto.PickTemplateVO;
import org.example.dto.Result;
import org.example.service.PickTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 领料解析：SAP 模板与导出。
 *
 * <h2>为什么模板要「上传 SAP 导出的空 Excel」</h2>
 * 使用方的工作流是「把 Excel 里的数据复制、粘进 SAP」。所以导出的文件必须与 SAP 模板**长得一模一样**
 * ——列顺序、表头文字、样式都一致，粘贴才对得上列。我们不去猜 SAP 的格式，直接拿他导出的那份当底。
 *
 * <p>鉴权：写操作（上传/删除）走 {@code purchase:edit} 权限位，与采购录入同档；
 * 查询与导出需要登录（{@code /api/pick-template/**} 已加入 {@code LOGIN_READ_PATTERNS}），
 * 因为导出结果里有部门名与物料清单，不该匿名可拉。
 */
@RestController
@RequestMapping("/api/pick-template")
public class PickTemplateController {

    @Autowired
    private PickTemplateService pickTemplateService;

    @GetMapping("/list")
    public Result<java.util.List<PickTemplateVO>> list(@RequestParam(required = false) String dept) {
        return Result.success(pickTemplateService.list(dept));
    }

    /**
     * 上传模板。名称为空时用文件名；部门为空表示通用模板。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("purchase:edit")
    public Result<PickTemplateVO> upload(@RequestPart("file") MultipartFile file,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(required = false) String docType,
                                         @RequestParam(required = false) String dept) throws IOException {
        return Result.success(pickTemplateService.upload(file, name, docType, dept));
    }

    @DeleteMapping("/delete")
    @SaCheckPermission("purchase:edit")
    public Result<Void> delete(@RequestParam Long id) {
        pickTemplateService.delete(id);
        return Result.success(null);
    }

    /**
     * 导出可粘进 SAP 的 Excel。
     *
     * <p>返回二进制而不是给一个下载链接：文件是**按当次核对结果即时生成**的，没有留存价值，
     * 也没有必要为了下载再造一套文件服务。
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody PickExportDTO dto) {
        byte[] bytes = pickTemplateService.export(dto);
        String fileName = "领料_" + LocalDate.now() + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
