package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.component.ImgUploader;
import org.example.dto.DocSaveOptions;
import org.example.dto.ImportSaveOutcome;
import org.example.dto.OcrConfirmDTO;
import org.example.dto.Result;
import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.MaterialPickSummary;
import org.example.entity.ProductionInbound;
import org.example.exception.BusinessException;
import org.example.exception.DocumentConflictException;
import org.example.service.DocumentImportSaveService;
import org.example.util.DocMaterialDateKey;
import org.example.util.MaterialPickSummaryImportUtil;
import org.example.util.ProductionInboundImportUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 图片识别辅助录入的「确认入库」。
 *
 * <pre>
 * 与文件导入的关系（变更-003 §2）：
 *   图片识别只是【多了一个数据来源】，不是多了一套业务逻辑。
 *   因此本入口不写任何自己的落库规则 ——
 *     · 行映射与校验：复用 MaterialPickSummaryImportUtil / ProductionInboundImportUtil
 *     · 去重与写入： 复用 DocumentImportSaveService（与文件导入同一份实现）
 * </pre>
 *
 * <p>与文件导入的差异只有一处：数据不经 Excel，行由前端在页面上人工校准后提交。
 *
 * <p><b>变更-047 新增 document 模式</b>（payload 带 {@code mode:"document"}）：
 * 「单据号已存在 → 载入该单全部明细编辑 → 按行差量保存」。
 * 缺省仍是老路径 —— 老客户端（无 mode 字段）的请求逐字走原逻辑，新契约只增不改。
 */
@RestController
@RequestMapping("/api/work-order/ocr")
public class OcrConfirmController {

    private static final Logger log = LoggerFactory.getLogger(OcrConfirmController.class);

    private static final String TYPE_PICK_SUMMARY = "material_pick_summary";
    private static final String TYPE_PRODUCTION_INBOUND = "production_inbound";

    /** 唯一的保存模式取值（变更-047）；空/缺省 = legacy */
    private static final String MODE_DOCUMENT = "document";

    @Autowired
    private DocumentImportSaveService documentImportSaveService;

    @Autowired
    private ImgUploader imgUploader;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 确认入库。
     *
     * <pre>
     * POST /api/work-order/ocr/confirm    multipart/form-data
     *   payload  JSON 字符串，见 {@link OcrConfirmDTO}
     *   file     单据图片（可选）；会存入记录的 file_name，供汇总列表的「线下单据」列回溯
     * </pre>
     *
     * <p>任何一行校验不过就<b>整单拒绝</b>并返回行级原因 —— 一张单据要么全进要么全不进，
     * 半张单进库比整单失败更难收拾。
     *
     * <p>document 模式额外允许「整单删除」（rows=[] + allowEmpty=true），
     * 并发冲突（库中行数/行 id 已被他人改动）回业务码 409，前端据此自动重查。
     */
    @SaCheckPermission("work_order:import")
    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> confirm(
            @RequestParam("payload") String payloadJson,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        OcrConfirmDTO dto;
        try {
            dto = objectMapper.readValue(payloadJson, OcrConfirmDTO.class);
        } catch (Exception e) {
            return Result.fail("请求体解析失败：" + e.getMessage());
        }

        String billType = dto.getBillType() == null ? "" : dto.getBillType().trim();
        if (!TYPE_PICK_SUMMARY.equals(billType) && !TYPE_PRODUCTION_INBOUND.equals(billType)) {
            return Result.fail("不支持的单据类型：" + dto.getBillType()
                    + "（仅支持 " + TYPE_PICK_SUMMARY + " / " + TYPE_PRODUCTION_INBOUND + "）");
        }
        if (!StringUtils.hasText(dto.getDocumentNo())) {
            return Result.fail("单据号为空，无法入库");
        }

        String mode = dto.getMode() == null ? "" : dto.getMode().trim();
        if (!mode.isEmpty() && !MODE_DOCUMENT.equals(mode)) {
            return Result.fail("不支持的保存模式：" + dto.getMode() + "（仅支持 " + MODE_DOCUMENT + "）");
        }
        boolean documentMode = MODE_DOCUMENT.equals(mode);

        int rowCount = dto.getRows() == null ? 0 : dto.getRows().size();
        if (rowCount == 0 && !documentMode) {
            return Result.fail("没有可入库的行");
        }

        log.info("图片识别确认入库开始, 单据类型={}, 单据号={}, 行数={}, 模式={}, 带图={}",
                billType, dto.getDocumentNo(), rowCount,
                documentMode ? MODE_DOCUMENT : "legacy", file != null && !file.isEmpty());

        // 图片先传（legacy 路径）：失败不阻断入库，但要让调用方知道这次没存上单据图。
        // document 模式走下面的惰性提供者 —— 没有实际写入时一张图都不该上传。
        String fileName = null;
        boolean imageUploadFailed = false;
        if (!documentMode && file != null && !file.isEmpty()) {
            try {
                fileName = imgUploader.upload(file.getBytes(), file.getOriginalFilename(),
                        extensionOf(file.getOriginalFilename()));
            } catch (Exception e) {
                // 图片传不上去不该让整单数据丢掉：记录照存，只是没有线下单据图
                imageUploadFailed = true;
                log.warn("单据图片上传失败，本次入库将不带线下单据图, 文件名={}, 原因={}",
                        file.getOriginalFilename(), e.getMessage());
            }
        }

        // 该单据的所有行绑同一张图：一张图就是一张单据
        final String documentFileName = fileName;

        // document 模式的惰性图片：至多上传一次（插入行 / 补图行确实存在时）。
        // 上传失败与 legacy 同口径：不阻断入库，只标记 imageUploadFailed
        AtomicReference<String> lazyFileName = new AtomicReference<>();
        AtomicBoolean lazyUploadTried = new AtomicBoolean(false);
        AtomicBoolean lazyUploadFailed = new AtomicBoolean(false);
        DocumentImportSaveService.RowImageProvider lazyImages = anyIndex -> {
            if (!lazyUploadTried.compareAndSet(false, true)) {
                return lazyFileName.get();
            }
            if (file == null || file.isEmpty()) {
                return null;
            }
            try {
                String name = imgUploader.upload(file.getBytes(), file.getOriginalFilename(),
                        extensionOf(file.getOriginalFilename()));
                lazyFileName.set(name);
                return name;
            } catch (Exception e) {
                lazyUploadFailed.set(true);
                log.warn("单据图片上传失败，本次入库将不带线下单据图, 文件名={}, 原因={}",
                        file.getOriginalFilename(), e.getMessage());
                return null;
            }
        };

        try {
            // 先映射 + 校验（整单拒绝），再落库 —— 校验失败要原样返回「哪几行不合法」，
            // 不能被下面的兜底 catch 套上「入库失败」前缀，那会让人以为服务出了问题
            List<String> errors = new ArrayList<>();

            ImportSaveOutcome outcome;
            if (TYPE_PICK_SUMMARY.equals(billType)) {
                List<MaterialPickSummary> rows = mapPickRows(dto, errors, documentMode);
                if (!errors.isEmpty()) {
                    return Result.fail(describeRowErrors(errors));
                }
                if (documentMode) {
                    String bad = validateDocumentRows(dto.getDocumentNo(), rows,
                            MaterialPickSummary::getMaterialCode, MaterialPickSummary::getPickDate);
                    if (bad != null) {
                        return Result.fail(bad);
                    }
                    outcome = documentImportSaveService.saveDocumentPick(dto.getDocumentNo().trim(),
                            rows, docOptions(dto), lazyImages);
                } else {
                    outcome = documentImportSaveService.savePickSummaries(rows, anyIndex -> documentFileName);
                }
            } else {
                List<ProductionInbound> rows = mapInboundRows(dto, errors, documentMode);
                if (!errors.isEmpty()) {
                    return Result.fail(describeRowErrors(errors));
                }
                if (documentMode) {
                    String bad = validateDocumentRows(dto.getDocumentNo(), rows,
                            ProductionInbound::getMaterialCode, ProductionInbound::getInboundDate);
                    if (bad != null) {
                        return Result.fail(bad);
                    }
                    outcome = documentImportSaveService.saveDocumentInbound(dto.getDocumentNo().trim(),
                            rows, docOptions(dto), lazyImages);
                } else {
                    outcome = documentImportSaveService.saveInbounds(rows, anyIndex -> documentFileName);
                }
            }

            String uploadedName = documentMode ? lazyFileName.get() : documentFileName;
            Map<String, Object> data = new HashMap<>();
            data.put("addCount", outcome.insertCount());
            data.put("updateCount", outcome.updateCount());
            data.put("deleteCount", outcome.deleteCount());
            data.put("skipCount", outcome.skipCount());
            data.put("failRows", List.of());
            data.put("imageUploaded", uploadedName != null);
            data.put("imageUploadFailed", documentMode ? lazyUploadFailed.get() : imageUploadFailed);

            log.info("图片识别确认入库完成, 单据号={}, 新增={}, 更新={}, 删除={}, 跳过={}, 图片={}",
                    dto.getDocumentNo(), outcome.insertCount(), outcome.updateCount(),
                    outcome.deleteCount(), outcome.skipCount(), uploadedName != null);
            return Result.success("入库完成", data);
        } catch (DocumentConflictException e) {
            // 并发冲突不是「服务出错」而是「库已经不是载入时那副样子」：
            // 回 409 让前端自动重查单据并提示重新载入（变更-047 契约）
            log.warn("图片识别确认入库并发冲突, 单据号={}, 原因={}", dto.getDocumentNo(), e.getMessage());
            return Result.error(409, e.getMessage());
        } catch (BusinessException e) {
            // 业务性拒绝（如「整单删除需要 allowEmpty=true」）：原文案回 500，不加「入库失败」前缀
            log.warn("图片识别确认入库被拒, 单据号={}, 原因={}", dto.getDocumentNo(), e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("图片识别确认入库失败, 单据号={}", dto.getDocumentNo(), e);
            return Result.fail("入库失败：" + e.getMessage());
        }
    }

    /** document 模式的开关注入；legacy 路径不读它 */
    private DocSaveOptions docOptions(OcrConfirmDTO dto) {
        return new DocSaveOptions(dto.getExpectedRowCount(), Boolean.TRUE.equals(dto.getAllowEmpty()));
    }

    /**
     * document 模式的追加校验（在既有整单校验之后、落库之前）：
     * 行日期非空、行内「物料编码 + 日期」不重复（与库表唯一键同口径）。
     *
     * <p>返回第一条错误的完整文案（已冻结，见变更-047 §接口契约），通过返回 null。
     * 走到这里时前面的行校验已全过，所以下标 i 与用户看到的行号（1 起）严格对齐。
     */
    private <T> String validateDocumentRows(String documentNo, List<T> rows,
                                            Function<T, String> materialCode,
                                            Function<T, LocalDate> date) {
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            T row = rows.get(i);
            int rowNo = i + 1;
            if (date.apply(row) == null) {
                return "第" + rowNo + "行：日期不能为空（日期是单据行身份的一部分）";
            }
            String key = DocMaterialDateKey.of(documentNo, materialCode.apply(row), date.apply(row));
            Integer first = seen.putIfAbsent(key, rowNo);
            if (first != null) {
                return "第" + rowNo + "行：与第" + first
                        + "行「物料编码 + 日期」重复，同一单据下同物料同日期只能有一行";
            }
        }
        return null;
    }

    /** 领料汇总：OCR 行 → WorkOrderExcelDTO → 既有 toEntity/validate */
    private List<MaterialPickSummary> mapPickRows(OcrConfirmDTO dto, List<String> errors, boolean keepRowIds) {
        List<MaterialPickSummary> rows = new ArrayList<>();
        int index = 0;
        for (OcrConfirmDTO.Row r : dto.getRows()) {
            index++;
            MaterialPickSummary entity = MaterialPickSummaryImportUtil.toEntity(toExcelRow(dto, r, index));
            // 行 id 只在 document 模式下生效：legacy 的 upsert 是「按三键认行」，
            // 客户端误带的 id 只会让它以指定主键插库，必须丢掉
            entity.setId(keepRowIds ? r.getId() : null);
            String err = MaterialPickSummaryImportUtil.validate(entity);
            if (err != null) {
                errors.add("第" + index + "行：" + err);
                continue;
            }
            rows.add(entity);
        }
        return rows;
    }

    /** 生产入库单：同上，走入库侧的映射与校验 */
    private List<ProductionInbound> mapInboundRows(OcrConfirmDTO dto, List<String> errors, boolean keepRowIds) {
        List<ProductionInbound> rows = new ArrayList<>();
        int index = 0;
        for (OcrConfirmDTO.Row r : dto.getRows()) {
            index++;
            ProductionInbound entity = ProductionInboundImportUtil.toEntity(toExcelRow(dto, r, index));
            entity.setId(keepRowIds ? r.getId() : null);
            String err = ProductionInboundImportUtil.validate(entity);
            if (err != null) {
                errors.add("第" + index + "行：" + err);
                continue;
            }
            rows.add(entity);
        }
        return rows;
    }

    private String describeRowErrors(List<String> errors) {
        String hint = "物料编码为空（请在物料名称旁搜索选择）或数量无法解析";
        return "有 " + errors.size() + " 行无法入库（" + hint + "）：" + String.join("；", errors);
    }

    /**
     * 借道 {@link WorkOrderExcelDTO} 复用既有的映射链。
     *
     * <p>刻意不在本类里重写「日期/数量怎么解析、哪些字段必填」——
     * 那是 ImportUtil 的职责，两处各写一份就会出现「Excel 能导、图片识别导不了」这类分歧。
     */
    private WorkOrderExcelDTO toExcelRow(OcrConfirmDTO dto, OcrConfirmDTO.Row row, int index) {
        WorkOrderExcelDTO excel = new WorkOrderExcelDTO();
        excel.setSeqNo(row.getSeqNo() == null ? String.valueOf(index) : String.valueOf(row.getSeqNo()));
        excel.setDocumentNo(dto.getDocumentNo());
        // 变更-047：行日期优先，缺省回退单据级日期 —— 老客户端不发 rows[].date，行为不变
        excel.setInboundDate(StringUtils.hasText(row.getDate()) ? row.getDate() : dto.getDate());
        excel.setMaterialName(row.getMaterialName());
        excel.setMaterialCode(row.getMaterialCode());
        excel.setInboundQty(row.getQty());
        excel.setUnit(row.getUnit());
        return excel;
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "jpg";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 && dot < filename.length() - 1 ? filename.substring(dot + 1) : "jpg";
    }
}
