package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.example.component.ImgUploader;
import org.example.dto.PageResult;
import org.example.dto.PurchaseTrackingQueryDTO;
import org.example.dto.PurchaseTrackingSaveDTO;
import org.example.dto.PurchaseTrackingSummaryQueryDTO;
import org.example.dto.PurchaseTrackingSummaryVO;
import org.example.dto.PurchaseTrackingVO;
import org.example.dto.Result;
import org.example.exception.BusinessException;
import org.example.service.PurchaseTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import java.util.Locale;
import java.util.Set;

/**
 * 采购进度跟踪（需求登记 → 提 OA 单 → 到货）。
 *
 * <p>鉴权：**整组（含查询）限 admin**（变更-096 收紧）。原先按决策-003 是「查询免登录、
 * 厂内公开」，但使用方 2026-10-17 明确「这页只有管理员能看」，于是与工单/领料/入库同一档 ——
 * 由 {@code SaTokenConfigure.ADMIN_READ_PATTERNS} 里的 {@code /api/purchase/**} 兜住 GET。
 * 写操作另外还有各自的不变式 1：{@code @SaCheckPermission} 权限位。
 *
 * <p>权限位用 {@code purchase:edit}，而不是复用一个含义不符的旧位
 * （比如 work_order:import）—— 这一页将来要单独授权给采购/仓库岗时，
 * 复用会让「谁能改采购进度」说不清楚。新权限位要同时做两件事，
 * 否则会出现「接口有闸门、但没有任何角色拿得到钥匙」的哑火：
 * ① 在 {@code docs/schema.sql} 第 2 部分的角色种子与迁移步骤里各登记一次；
 * ② 对**既有库**执行一次迁移（生产已执行，见变更-091 的部署记录）。
 */
@RestController
@RequestMapping("/api/purchase")
public class PurchaseTrackingController {

    @Autowired
    private PurchaseTrackingService purchaseTrackingService;

    @Autowired
    private ImgUploader imgUploader;

    /**
     * 列表：后端分页 + 后端关键字 / 日期筛选。
     *
     * <p>{@code pageNum} / {@code pageSize} 必传，缺参由全局异常处理回 400
     * （理由见 {@link org.example.dto.PageQueryDTO}：给默认值会让老客户端安静地只拿到第 1 页）。
     */
    @GetMapping("/list")
    public PageResult<PurchaseTrackingVO> list(@Valid PurchaseTrackingQueryDTO query) {
        IPage<PurchaseTrackingVO> page = purchaseTrackingService.listPage(query);
        return PageResult.of(page.getRecords(), page.getTotal(),
                query.resolvedPageNum(), query.resolvedPageSize());
    }

    /**
     * 顶部汇总栏：待提OA / 等货 / 临期 / 超期 / 已到货 / 部分到货（变更-096）。
     *
     * <p>**不分页**：它给的是全量计数，不是当前页的计数（ADR-0003：聚合由服务端按同一套筛选给）。
     * 筛选参数与 {@code /list} 一致（keyword + 提出需求日期区间），但**不收 pageNum/pageSize** ——
     * 收了会让人以为它分页。
     */
    @GetMapping("/summary")
    public Result<PurchaseTrackingSummaryVO> summary(PurchaseTrackingSummaryQueryDTO query) {
        return Result.success(purchaseTrackingService.summary(query));
    }

    /** 新增或修改（依 {@code id} 是否为空）。保存成功后会把物料编码登记进 material_stock */
    @SaCheckPermission("purchase:edit")
    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody PurchaseTrackingSaveDTO dto) {
        return Result.success(purchaseTrackingService.save(dto));
    }

    @SaCheckPermission("purchase:edit")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        purchaseTrackingService.delete(id);
        return Result.success(null);
    }

    /** 送货单图片的后缀白名单。img-service 只收 jpg/png，这里先拦一道给出人能看懂的话 */
    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("jpg", "jpeg", "png");

    /** img-service 自己限 10MB；在这边先拦，免得使用者等半天换来一句存储侧报错 */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    /**
     * 上传送货单图片（变更-094：使用方说「送货单是一张图片」）。
     *
     * <p>走 {@link ImgUploader} 的「单据图片」那条通路（一张进、回一个 fileName），
     * **不是**底图那条一张进两张出的通路 —— 送货单只用于显示，不需要白纸版/亮线版。
     *
     * <p>上传一律打到本服务再转发：img-service 不对外暴露，而且它那边**没有鉴权注解**，
     * 权限只能关在这一层（同 {@code EquipmentLedgerController#ledgerUploadImage} 的理由）。
     *
     * <p>返回的 fileName 由前端按 ADR-0004 固定拼接成 {@code /files/xxx} 存进
     * {@code purchase_tracking.delivery_note}（那一列 varchar(64) 装得下，不用改表）。
     */
    @SaCheckPermission("purchase:edit")
    @PostMapping(value = "/upload-delivery-note", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadDeliveryNote(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("没有收到图片文件");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("图片太大（上限 10MB），请先压缩再传");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_IMAGE_EXT.contains(ext)) {
            throw new BusinessException("只支持 jpg / png 图片（送货单请拍照或截图后上传）");
        }
        return Result.success(
                imgUploader.upload(file.getBytes(), file.getOriginalFilename(), ext));
    }

    /** 取扩展名（小写）；没有扩展名时返回空串，交由白名单拒掉 */
    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
