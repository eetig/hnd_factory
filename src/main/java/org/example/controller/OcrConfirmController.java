package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.component.ImgUploader;
import org.example.dto.ImportSaveOutcome;
import org.example.dto.OcrConfirmDTO;
import org.example.dto.Result;
import org.example.dto.WorkOrderExcelDTO;
import org.example.entity.MaterialPickSummary;
import org.example.entity.ProductionInbound;
import org.example.service.DocumentImportSaveService;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 */
@RestController
@RequestMapping("/api/work-order/ocr")
public class OcrConfirmController {

    private static final Logger log = LoggerFactory.getLogger(OcrConfirmController.class);

    private static final String TYPE_PICK_SUMMARY = "material_pick_summary";
    private static final String TYPE_PRODUCTION_INBOUND = "production_inbound";

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
        if (dto.getRows() == null || dto.getRows().isEmpty()) {
            return Result.fail("没有可入库的行");
        }

        log.info("图片识别确认入库开始, 单据类型={}, 单据号={}, 行数={}, 带图={}",
                billType, dto.getDocumentNo(), dto.getRows().size(),
                file != null && !file.isEmpty());

        // 图片先传：失败不阻断入库，但要让调用方知道这次没存上单据图
        String fileName = null;
        boolean imageUploadFailed = false;
        if (file != null && !file.isEmpty()) {
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

        try {
            // 先映射 + 校验（整单拒绝），再落库 —— 校验失败要原样返回「哪几行不合法」，
            // 不能被下面的兜底 catch 套上「入库失败」前缀，那会让人以为服务出了问题
            List<String> errors = new ArrayList<>();

            ImportSaveOutcome outcome;
            if (TYPE_PICK_SUMMARY.equals(billType)) {
                List<MaterialPickSummary> rows = mapPickRows(dto, errors);
                if (!errors.isEmpty()) {
                    return Result.fail(describeRowErrors(errors));
                }
                outcome = documentImportSaveService.savePickSummaries(rows, anyIndex -> documentFileName);
            } else {
                List<ProductionInbound> rows = mapInboundRows(dto, errors);
                if (!errors.isEmpty()) {
                    return Result.fail(describeRowErrors(errors));
                }
                outcome = documentImportSaveService.saveInbounds(rows, anyIndex -> documentFileName);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("addCount", outcome.insertCount());
            data.put("updateCount", outcome.updateCount());
            data.put("skipCount", outcome.skipCount());
            data.put("failRows", List.of());
            data.put("imageUploaded", fileName != null);
            data.put("imageUploadFailed", imageUploadFailed);

            log.info("图片识别确认入库完成, 单据号={}, 新增={}, 更新={}, 跳过={}, 图片={}",
                    dto.getDocumentNo(), outcome.insertCount(), outcome.updateCount(),
                    outcome.skipCount(), fileName != null);
            return Result.success("入库完成", data);
        } catch (Exception e) {
            log.error("图片识别确认入库失败, 单据号={}", dto.getDocumentNo(), e);
            return Result.fail("入库失败：" + e.getMessage());
        }
    }

    /** 领料汇总：OCR 行 → WorkOrderExcelDTO → 既有 toEntity/validate */
    private List<MaterialPickSummary> mapPickRows(OcrConfirmDTO dto, List<String> errors) {
        List<MaterialPickSummary> rows = new ArrayList<>();
        int index = 0;
        for (OcrConfirmDTO.Row r : dto.getRows()) {
            index++;
            MaterialPickSummary entity = MaterialPickSummaryImportUtil.toEntity(toExcelRow(dto, r, index));
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
    private List<ProductionInbound> mapInboundRows(OcrConfirmDTO dto, List<String> errors) {
        List<ProductionInbound> rows = new ArrayList<>();
        int index = 0;
        for (OcrConfirmDTO.Row r : dto.getRows()) {
            index++;
            ProductionInbound entity = ProductionInboundImportUtil.toEntity(toExcelRow(dto, r, index));
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
        excel.setInboundDate(dto.getDate());
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
