package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.alibaba.excel.EasyExcel;
import org.example.component.WorkOrderImportCache;
import org.example.dto.ImgResult;
import org.example.dto.Result;
import org.example.dto.UploadResult;
import org.example.dto.WorkOrderExcelDTO;
import org.example.dto.WorkOrderImportPayload;
import org.example.dto.WorkOrderImportPreviewVO;
import org.example.dto.WorkOrderImportResultVO;
import org.example.dto.WorkOrderImportSaveDTO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialMovement;
import org.example.entity.MaterialPickSummary;
import org.example.entity.ProductionInbound;
import org.example.entity.WorkOrder;
import org.example.feign.ImgFeignClient;
import org.example.listener.WorkOrderImportExcelListener;
import org.example.service.MaterialMovementService;
import org.example.service.MaterialPickSummaryService;
import org.example.service.ProductionInboundService;
import org.example.service.WorkOrderService;
import org.example.util.ExcelCleanUtil;
import org.example.util.DocMaterialKey;
import org.example.util.GoodsMoveImportUtil;
import org.example.util.InMemoryMultipartFile;
import org.example.util.MaterialPickSummaryImportUtil;
import org.example.util.ProductionInboundImportUtil;
import org.example.util.WorkOrderImportUtil;
import org.example.util.WpsCellImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导入（两阶段）：preview 解析并缓存 -> save 从缓存批量落库。
 * 按单据大类分流：
 *   工单汇总   -> work_order 表
 *   货物移动   -> material_movement 表
 *   生产入库单 -> production_inbound 表（单据列为 WPS 内嵌图片，保存时上传 MinIO）
 */
@RestController
@RequestMapping("/api/work-order/import")
public class WorkOrderImportController {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderImportController.class);

    private static final String TYPE_WORK_ORDER = "工单汇总";
    private static final String TYPE_GOODS_MOVE = "货物移动";
    private static final String TYPE_PRODUCTION_INBOUND = "生产入库单";
    private static final String TYPE_PICK_SUMMARY = "领料汇总";

    @Autowired
    private WorkOrderImportCache importCache;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private MaterialMovementService materialMovementService;

    @Autowired
    private ProductionInboundService productionInboundService;

    @Autowired
    private MaterialPickSummaryService materialPickSummaryService;

    @Autowired
    private ImgFeignClient imgFeignClient;

    // ================= 接口1：解析预览（只解析，不入库） =================
    @SaCheckPermission("work_order:import")
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<WorkOrderImportPreviewVO> preview(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "templateCode", required = false) String templateCode) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的Excel文件");
        }
        log.info("导入解析开始, 文件名={}, 大小={}字节, templateCode={}",
                file.getOriginalFilename(), file.getSize(), templateCode);

        byte[] bytes;
        String filename = decodeFilename(file.getOriginalFilename());
        SheetReadResult read;
        try {
            // 清洗：SAP 导出数值单元格可能带首尾空格（<v>0 </v>），先去掉再解析
            bytes = file.getBytes();
            if (filename != null && filename.toLowerCase().endsWith(".xlsx")) {
                bytes = ExcelCleanUtil.clean(bytes);
            }
            read = readSheet(bytes);
        } catch (Exception e) {
            log.error("Excel解析失败, 文件名={}", file.getOriginalFilename(), e);
            return Result.fail(describeError(e));
        }

        List<WorkOrderExcelDTO> dtoList = read.listener.getDataList();
        String billType = detectType(read.listener.getHeaderNameList(), filename, templateCode);

        WorkOrderImportPayload payload;
        List<Map<String, Object>> rows;
        List<WorkOrderImportResultVO.RowError> errorRows = new ArrayList<>();

        if (TYPE_GOODS_MOVE.equals(billType)) {
            List<MaterialMovement> validList = new ArrayList<>();
            rows = buildGoodsMoveRows(dtoList, validList, errorRows);
            payload = WorkOrderImportPayload.ofMovements(billType, validList);
        } else if (TYPE_PRODUCTION_INBOUND.equals(billType)) {
            List<ProductionInbound> validList = new ArrayList<>();
            List<String> documentIds = new ArrayList<>();
            rows = buildProductionInboundRows(dtoList, validList, documentIds, errorRows);
            payload = WorkOrderImportPayload.ofInbounds(billType, validList, documentIds, bytes);
        } else if (TYPE_PICK_SUMMARY.equals(billType)) {
            List<MaterialPickSummary> validList = new ArrayList<>();
            List<String> documentIds = new ArrayList<>();
            rows = buildPickSummaryRows(dtoList, validList, documentIds, errorRows);
            payload = WorkOrderImportPayload.ofPickSummaries(billType, validList, documentIds, bytes);
        } else {
            List<WorkOrder> validList = new ArrayList<>();
            rows = buildWorkOrderRows(dtoList, validList, errorRows);
            payload = WorkOrderImportPayload.ofWorkOrders(billType, validList);
        }

        String taskId = importCache.put(payload);

        WorkOrderImportPreviewVO vo = new WorkOrderImportPreviewVO();
        vo.setTaskId(taskId);
        vo.setWorkOrderType(billType);
        vo.setTotal(dtoList.size());
        vo.setList(rows);
        vo.setErrorRows(errorRows);

        log.info("导入解析完成, taskId={}, 单据类型={}, 总行数={}, 有效={}, 错误={}",
                taskId, billType, dtoList.size(), payload.size(), errorRows.size());
        return Result.success("解析成功", vo);
    }

    // ================= 接口2：确认保存（按单据大类路由到对应表） =================
    @SaCheckPermission("work_order:import")
    @PostMapping("/save")
    public Result<Map<String, Object>> save(@RequestBody WorkOrderImportSaveDTO body) {
        String taskId = body == null ? null : body.getTaskId();
        if (!StringUtils.hasText(taskId)) {
            return Result.fail("taskId 不能为空");
        }
        log.info("导入保存开始, taskId={}", taskId);

        WorkOrderImportPayload payload = importCache.take(taskId);
        if (payload == null) {
            log.warn("导入保存失败, taskId 不存在或已过期, taskId={}", taskId);
            return Result.fail("taskId 不存在或已过期，请重新上传解析");
        }
        if (payload.size() == 0) {
            return Result.fail("没有可保存的数据");
        }

        try {
            Map<String, Object> data = new HashMap<>();
            if (TYPE_GOODS_MOVE.equals(payload.getBillType())) {
                WorkOrderImportSaveVO save = materialMovementService.saveImported(payload.getMovements());
                log.info("货物移动导入保存完成, taskId={}, 新写入={}, 覆盖删除={}",
                        taskId, save.getInsertCount(), save.getUpdateCount());
                data.put("addCount", save.getInsertCount());
                data.put("updateCount", save.getUpdateCount());
            } else if (TYPE_PRODUCTION_INBOUND.equals(payload.getBillType())) {
                List<String> fileNames = resolveDocumentFileNames(payload);
                List<ProductionInbound> list = payload.getInbounds();
                for (int i = 0; i < list.size() && i < fileNames.size(); i++) {
                    list.get(i).setFileName(fileNames.get(i));
                }
                WorkOrderImportSaveVO save = productionInboundService.saveImported(list);
                log.info("生产入库单导入保存完成, taskId={}, 新增={}, 更新={}, 图片={}张",
                        taskId, save.getInsertCount(), save.getUpdateCount(), uploadedCount(fileNames));
                data.put("addCount", save.getInsertCount());
                data.put("updateCount", save.getUpdateCount());
            } else if (TYPE_PICK_SUMMARY.equals(payload.getBillType())) {
                List<String> fileNames = resolveDocumentFileNames(payload);
                List<MaterialPickSummary> list = payload.getPickSummaries();
                for (int i = 0; i < list.size() && i < fileNames.size(); i++) {
                    list.get(i).setFileName(fileNames.get(i));
                }
                WorkOrderImportSaveVO save = materialPickSummaryService.saveImported(list);
                log.info("领料汇总导入保存完成, taskId={}, 新增={}, 更新={}, 图片={}张",
                        taskId, save.getInsertCount(), save.getUpdateCount(), uploadedCount(fileNames));
                data.put("addCount", save.getInsertCount());
                data.put("updateCount", save.getUpdateCount());
            } else {
                WorkOrderImportSaveVO save = workOrderService.saveImported(payload.getWorkOrders());
                log.info("工单导入保存完成, taskId={}, 新增={}, 更新={}", taskId, save.getInsertCount(), save.getUpdateCount());
                data.put("addCount", save.getInsertCount());
                data.put("updateCount", save.getUpdateCount());
            }
            data.put("failRows", List.of());
            return Result.success("导入完成", data);
        } catch (Exception e) {
            log.error("导入保存失败, taskId={}", taskId, e);
            return Result.fail("保存失败：" + describeError(e));
        }
    }

    // ================= 解析与类型识别 =================

    /**
     * 读取 sheet。兼容两种表头位置：
     * 领料汇总等常规文件表头在第 1 行；生产入库单第 1 行是空行、表头在第 2 行。
     */
    private SheetReadResult readSheet(byte[] bytes) {
        WorkOrderImportExcelListener listener = readOnce(bytes, 1);
        if (!listener.getHeaderNameList().isEmpty()) {
            return new SheetReadResult(listener, 1);
        }
        log.info("第1行为空，按表头在第2行重新解析");
        return new SheetReadResult(readOnce(bytes, 2), 2);
    }

    /** sheet 读取结果：监听器 + 表头所在行号 */
    private static class SheetReadResult {
        final WorkOrderImportExcelListener listener;
        final int headerRowNumber;

        SheetReadResult(WorkOrderImportExcelListener listener, int headerRowNumber) {
            this.listener = listener;
            this.headerRowNumber = headerRowNumber;
        }
    }

    /**
     * 还原 multipart 中文文件名。
     * 若原值已含非 Latin-1 字符（说明 Tomcat 已正确解码 UTF-8），直接返回；
     * 否则原值是被当成 ISO-8859-1 解析的乱码，按 UTF-8 重新解码。
     */
    private String decodeFilename(String raw) {
        if (raw == null) {
            return null;
        }
        boolean looksDecoded = raw.chars().anyMatch(c -> c > 0xFF);
        if (looksDecoded) {
            return raw;
        }
        return new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private WorkOrderImportExcelListener readOnce(byte[] bytes, int headRowNumber) {
        WorkOrderImportExcelListener listener = new WorkOrderImportExcelListener();
        EasyExcel.read(new ByteArrayInputStream(bytes), listener)
                .sheet()
                .headRowNumber(headRowNumber)
                .doRead();
        return listener;
    }

    /**
     * 识别单据大类。
     * 优先级：前端显式 templateCode > 表头特征 > 文件名关键字兜底。
     *
     * 注意：生产入库单与领料汇总的表头完全相同（序号/日期/物料名称/物料编码/领料数量/单位/单据），
     * 且表头都在 Excel 第 2 行，仅靠内容无法区分。因此这两类必须由前端显式传 templateCode，
     * 未传时按文件名关键字（入库/领料）兜底判断。
     */
    private String detectType(List<String> headers, String filename, String templateCode) {
        String byCode = typeOfTemplateCode(templateCode);
        if (byCode != null) {
            return byCode;
        }
        if (headers.contains("移动类型")) {
            return TYPE_GOODS_MOVE;
        }
        if (headers.contains("领料数量") || headers.contains("单据")) {
            if (filename != null) {
                if (filename.contains("入库")) {
                    return TYPE_PRODUCTION_INBOUND;
                }
                if (filename.contains("领料")) {
                    return TYPE_PICK_SUMMARY;
                }
            }
            log.warn("生产入库单与领料汇总表头相同，无法从内容区分，已按生产入库单处理，文件名={}；"
                    + "建议前端显式传 templateCode=production_inbound 或 material_pick_summary", filename);
            return TYPE_PRODUCTION_INBOUND;
        }
        return TYPE_WORK_ORDER;
    }

    private String typeOfTemplateCode(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            return null;
        }
        switch (templateCode.trim()) {
            case "work_order": return TYPE_WORK_ORDER;
            case "goods_move": return TYPE_GOODS_MOVE;
            case "production_inbound": return TYPE_PRODUCTION_INBOUND;
            case "material_pick_summary": return TYPE_PICK_SUMMARY;
            default: return null;
        }
    }

    // ================= 行构建 =================

    /** 工单汇总：订单/物料编码/物料描述/订单数量/基本开始日期/确认的产量 */
    private List<Map<String, Object>> buildWorkOrderRows(List<WorkOrderExcelDTO> dtoList,
                                                         List<WorkOrder> validList,
                                                         List<WorkOrderImportResultVO.RowError> errorRows) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int rowNum = 0;
        for (WorkOrderExcelDTO dto : dtoList) {
            rowNum++;
            WorkOrder w = WorkOrderImportUtil.toEntity(dto);
            String err = WorkOrderImportUtil.validate(w);
            if (err != null) {
                errorRows.add(new WorkOrderImportResultVO.RowError(rowNum, "第" + rowNum + "行：" + err));
                continue;
            }
            validList.add(w);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", w.getOrderNo());             // 订单
            row.put("materialCode", w.getMaterialCode());   // 物料编码
            row.put("materialDesc", w.getMaterialDesc());   // 物料描述
            row.put("orderQty", w.getOrderQty());           // 订单数量
            row.put("planStartDate", w.getPlanStartDate()); // 基本开始日期
            row.put("confirmedQty", w.getConfirmedQty());   // 确认的产量
            rows.add(row);
        }
        return rows;
    }

    /** 货物移动：原样返回文件的全部 14 列 */
    private List<Map<String, Object>> buildGoodsMoveRows(List<WorkOrderExcelDTO> dtoList,
                                                         List<MaterialMovement> validList,
                                                         List<WorkOrderImportResultVO.RowError> errorRows) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int rowNum = 0;
        for (WorkOrderExcelDTO dto : dtoList) {
            rowNum++;
            MaterialMovement m = GoodsMoveImportUtil.toEntity(dto);
            String err = GoodsMoveImportUtil.validate(m);
            if (err != null) {
                errorRows.add(new WorkOrderImportResultVO.RowError(rowNum, "第" + rowNum + "行：" + err));
                continue;
            }
            validList.add(m);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", dto.getOrderNo());                // 订单
            row.put("materialCode", dto.getMaterialCode());      // 物料
            row.put("movementFlag", dto.getMovementFlag());      // 移动标识
            row.put("item", dto.getItem());                      // 项目
            row.put("materialDesc", dto.getMaterialDesc());      // 物料描述
            row.put("entryQty", dto.getEntryQty());              // 以录入单位表示的数量
            row.put("batchNo", dto.getBatchNo());                // 批次
            row.put("storageLocation", dto.getStorageLocation());// 存储地点
            row.put("unit", dto.getUnit());                      // 基本计量单位
            row.put("movementType", dto.getMoveType());          // 移动类型
            row.put("materialDoc", dto.getMaterialDoc());        // 物料凭证
            row.put("creditFlag", dto.getCreditFlag());          // 借/贷标识
            row.put("quantity", dto.getQuantity());              // 数量
            row.put("postingDate", dto.getPostingDate());        // 过账日期
            rows.add(row);
        }
        return rows;
    }

    /**
     * 领料汇总：序号/单据号/日期/物料名称/物料编码/领料数量/单位/单据
     * 单据号为业务唯一键，导入前做两重重复校验：
     *   1) 文件内自身重复
     *   2) 数据库中已存在
     * 重复行进 errorRows（含行号与原因），其余合法行正常进入预览与导入。
     */
    private List<Map<String, Object>> buildPickSummaryRows(List<WorkOrderExcelDTO> dtoList,
                                                           List<MaterialPickSummary> validList,
                                                           List<String> documentIds,
                                                           List<WorkOrderImportResultVO.RowError> errorRows) {
        // 1) 逐行映射 + 基础字段校验
        List<MaterialPickSummary> candidates = new ArrayList<>();
        List<Integer> candidateRows = new ArrayList<>();
        List<String> candidateDocIds = new ArrayList<>();

        int rowNum = 0;
        for (WorkOrderExcelDTO dto : dtoList) {
            rowNum++;
            MaterialPickSummary p = MaterialPickSummaryImportUtil.toEntity(dto);
            String err = MaterialPickSummaryImportUtil.validate(p);
            if (err != null) {
                errorRows.add(new WorkOrderImportResultVO.RowError(rowNum, "第" + rowNum + "行：" + err));
                continue;
            }
            candidates.add(p);
            candidateRows.add(rowNum);
            candidateDocIds.add(WpsCellImageUtil.parseDispimgId(dto.getDocumentCell()));
        }

        // 2) 文件内组合键重复检测
        Set<String> duplicatedInFile = detectInFileDuplicates(
                candidates.stream().map(p -> DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode())).collect(Collectors.toList()),
                candidates.stream().map(p -> DocMaterialKey.label(p.getDocumentNo(), p.getMaterialCode())).collect(Collectors.toList()),
                candidateRows, errorRows);

        // 3) 查数据库中已存在的记录（存在 -> 保存时更新，不存在 -> 新增）
        Set<String> docs = candidates.stream()
                .map(MaterialPickSummary::getDocumentNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Set<String> existingInDb = materialPickSummaryService.findIdByDocAndMaterial(docs).keySet();

        // 4) 组装预览行（仅文件内重复的行不进预览）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            MaterialPickSummary p = candidates.get(i);
            String key = DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode());
            if (duplicatedInFile.contains(key)) {
                continue;
            }
            String documentId = candidateDocIds.get(i);
            validList.add(p);
            documentIds.add(documentId);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seqNo", p.getSeqNo());                 // 序号
            row.put("documentNo", p.getDocumentNo());       // 单据号
            row.put("pickDate", p.getPickDate());           // 日期
            row.put("materialName", p.getMaterialName());   // 物料名称
            row.put("materialCode", p.getMaterialCode());   // 物料编码
            row.put("pickQty", p.getPickQty());             // 领料数量
            row.put("unit", p.getUnit());                   // 单位
            row.put("hasDocument", documentId != null);     // 是否带单据图片
            row.put("isUpdate", existingInDb.contains(key)); // true=更新已有，false=新增
            rows.add(row);
        }
        return rows;
    }

    /**
     * 生产入库单：序号/单据号/日期/物料名称/物料编码/领料数量/单位/单据
     * 单据号为业务唯一键，导入前做两重重复校验（同领料汇总）：
     *   1) 文件内自身重复 -> 报错拦截
     *   2) 数据库中已存在   -> 保存时更新
     */
    private List<Map<String, Object>> buildProductionInboundRows(List<WorkOrderExcelDTO> dtoList,
                                                                 List<ProductionInbound> validList,
                                                                 List<String> documentIds,
                                                                 List<WorkOrderImportResultVO.RowError> errorRows) {
        // 1) 逐行映射 + 基础字段校验
        List<ProductionInbound> candidates = new ArrayList<>();
        List<Integer> candidateRows = new ArrayList<>();
        List<String> candidateDocIds = new ArrayList<>();

        int rowNum = 0;
        for (WorkOrderExcelDTO dto : dtoList) {
            rowNum++;
            ProductionInbound p = ProductionInboundImportUtil.toEntity(dto);
            String err = ProductionInboundImportUtil.validate(p);
            if (err != null) {
                errorRows.add(new WorkOrderImportResultVO.RowError(rowNum, "第" + rowNum + "行：" + err));
                continue;
            }
            candidates.add(p);
            candidateRows.add(rowNum);
            candidateDocIds.add(WpsCellImageUtil.parseDispimgId(dto.getDocumentCell()));
        }

        // 2) 文件内组合键重复检测
        Set<String> duplicatedInFile = detectInFileDuplicates(
                candidates.stream().map(p -> DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode())).collect(Collectors.toList()),
                candidates.stream().map(p -> DocMaterialKey.label(p.getDocumentNo(), p.getMaterialCode())).collect(Collectors.toList()),
                candidateRows, errorRows);

        // 3) 查数据库中已存在的记录（存在 -> 保存时更新，不存在 -> 新增）
        Set<String> docs = candidates.stream()
                .map(ProductionInbound::getDocumentNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Set<String> existingInDb = productionInboundService.findIdByDocAndMaterial(docs).keySet();

        // 4) 组装预览行（仅文件内重复的行不进预览）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ProductionInbound p = candidates.get(i);
            String key = DocMaterialKey.of(p.getDocumentNo(), p.getMaterialCode());
            if (duplicatedInFile.contains(key)) {
                continue;
            }
            String documentId = candidateDocIds.get(i);
            validList.add(p);
            documentIds.add(documentId);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seqNo", p.getSeqNo());                 // 序号
            row.put("documentNo", p.getDocumentNo());       // 单据号
            row.put("inboundDate", p.getInboundDate());     // 日期
            row.put("materialName", p.getMaterialName());   // 物料名称
            row.put("materialCode", p.getMaterialCode());   // 物料编码
            row.put("inboundQty", p.getInboundQty());       // 领料数量（入库数量）
            row.put("unit", p.getUnit());                   // 单位
            row.put("hasDocument", documentId != null);     // 是否带单据图片
            row.put("isUpdate", existingInDb.contains(key)); // true=更新已有，false=新增
            rows.add(row);
        }
        return rows;
    }

    /**
     * 文件内业务唯一键重复检测：重复的行写入 errorRows，返回重复的键集合。
     * 唯一键为 (单据号 + 物料编码) —— 同一张单据下不同物料是不同记录，不算重复。
     *
     * @param keys   每行的组合键（与 rowNums 一一对应）
     * @param labels 每行组合键的可读描述，用于错误提示
     * @param rowNums 每行的 Excel 行号
     */
    private Set<String> detectInFileDuplicates(List<String> keys, List<String> labels, List<Integer> rowNums,
                                               List<WorkOrderImportResultVO.RowError> errorRows) {
        Map<String, List<Integer>> rowsByKey = new LinkedHashMap<>();
        Map<String, String> labelByKey = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            rowsByKey.computeIfAbsent(keys.get(i), k -> new ArrayList<>()).add(rowNums.get(i));
            labelByKey.put(keys.get(i), labels.get(i));
        }

        Set<String> duplicated = new HashSet<>();
        for (Map.Entry<String, List<Integer>> e : rowsByKey.entrySet()) {
            if (e.getValue().size() > 1) {
                duplicated.add(e.getKey());
                String rows = e.getValue().stream().map(String::valueOf).collect(Collectors.joining("、"));
                String label = labelByKey.get(e.getKey());
                for (Integer r : e.getValue()) {
                    errorRows.add(new WorkOrderImportResultVO.RowError(r,
                            "第" + r + "行：" + label + " 在文件中重复（第 " + rows + " 行）"));
                }
            }
        }
        return duplicated;
    }

    // ================= 单据图片 =================

    /**
     * 提取 xlsx 内嵌单据图片并上传 MinIO，返回与 documentIds 对齐的 fileName 列表。
     * 同一张图被多行引用时只上传一次。
     */
    private List<String> resolveDocumentFileNames(WorkOrderImportPayload payload) {
        List<String> documentIds = payload.getDocumentIds();
        if (documentIds == null || documentIds.isEmpty() || payload.getSourceBytes() == null) {
            return List.of();
        }

        Map<String, WpsCellImageUtil.ExtractedImage> images = WpsCellImageUtil.extract(payload.getSourceBytes());
        List<String> fileNames = new ArrayList<>();
        if (images.isEmpty()) {
            for (int i = 0; i < documentIds.size(); i++) {
                fileNames.add(null);
            }
            return fileNames;
        }

        Map<String, String> uploaded = new HashMap<>();   // DISPIMG ID -> 上传后的 fileName
        for (String docId : documentIds) {
            if (docId == null) {
                fileNames.add(null);
                continue;
            }
            String fileName = uploaded.get(docId);
            if (fileName == null) {
                WpsCellImageUtil.ExtractedImage img = images.get(docId);
                if (img == null) {
                    fileNames.add(null);
                    continue;
                }
                try {
                    fileName = uploadImage(img);
                    uploaded.put(docId, fileName);
                } catch (Exception e) {
                    log.warn("单据图片上传失败, DISPIMG ID={}, 原因={}", docId, e.getMessage());
                    fileNames.add(null);
                    continue;
                }
            }
            fileNames.add(fileName);
        }
        return fileNames;
    }

    /** 统计成功上传的唯一图片数 */
    private int uploadedCount(List<String> fileNames) {
        return (int) fileNames.stream().filter(java.util.Objects::nonNull).distinct().count();
    }

    /** 上传单张内嵌图片到 img-service */
    private String uploadImage(WpsCellImageUtil.ExtractedImage img) {
        // img-service 只接受 jpg/png，jpeg 统一按 jpg 提交（格式相同）
        String ext = "png".equalsIgnoreCase(img.getExtension()) ? "png" : "jpg";
        String contentType = "png".equals(ext) ? "image/png" : "image/jpeg";
        String base = StringUtils.hasText(img.getOriginalName()) ? img.getOriginalName() : "document";
        // 去掉原始名里可能带的后缀，统一追加白名单后缀
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\.[A-Za-z0-9]+$", "");

        MultipartFile file = new InMemoryMultipartFile("file", base + "." + ext, contentType, img.getData());
        ImgResult<UploadResult> res = imgFeignClient.upload(file);
        if (res == null || res.getCode() != 200 || res.getData() == null) {
            throw new IllegalStateException("img-service 返回异常");
        }
        return res.getData().getFileName();
    }

    // EasyExcel 底层异常会层层包装，取最里层 cause 才是真正原因
    private String describeError(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof NumberFormatException) {
            return "文件中存在含非法字符的数字单元格（常见于数量/重量列混入空格或千分位），请检查该列数据格式。原始错误："
                    + root.getMessage();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
