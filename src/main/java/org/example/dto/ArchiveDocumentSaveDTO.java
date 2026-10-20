package org.example.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 资料台账的保存入参（白名单）。
 *
 * <p>只开放使用者真正会填的字段；主键修改时必传、新增时留空（与 PurchaseTrackingSaveDTO 同一套）。
 *
 * <p><b>必填只有「名称」和「分类」</b>，文件与原件位置是"二选一至少填一个"——
 * 这条约束写在 {@code ArchiveServiceImpl#save} 里而不是注解上，因为它是**跨字段**的
 * （单字段注解表达不了"两个都空才拒"），而且报错文案要能直接告诉使用者该补哪个。
 *
 * <p>为什么不给 {@code fileSize}/{@code mimeType} 加最小校验：它们由上一步上传结果带上来，
 * 不是手填的；唯一的可信来源是 file-service 返回的那份。
 */
@Data
public class ArchiveDocumentSaveDTO {

    /** 修改时必传；新增时留空 */
    private Long id;

    /** 资料名称（必填，≤128） */
    @Size(max = 128, message = "资料名称最多 128 个字")
    private String name;

    /** 分类 id（必填） */
    private Long categoryId;

    /** 裸对象名（上传接口返回的 fileName）；空 = 只有纸质 */
    @Size(max = 128, message = "文件名过长")
    private String fileName;

    /** 原文件名（用于下载时还原） */
    @Size(max = 255, message = "原文件名过长")
    private String origName;

    private Long fileSize;

    @Size(max = 128, message = "Content-Type 过长")
    private String mimeType;

    /** 有效期至（证照/校验/培训类才填） */
    private LocalDate expireDate;

    /** 纸质原件在哪：部门/柜子/责任人（与 fileName 二选一至少填一个） */
    @Size(max = 128, message = "原件位置最多 128 个字")
    private String originPlace;

    @Size(max = 64, message = "编号文号最多 64 个字")
    private String docNo;

    @Size(max = 64, message = "责任人最多 64 个字")
    private String owner;

    @Size(max = 500, message = "备注最多 500 个字")
    private String remark;
}
