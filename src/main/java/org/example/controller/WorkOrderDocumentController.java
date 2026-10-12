package org.example.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.example.dto.DocumentDetailVO;
import org.example.dto.DocumentRowVO;
import org.example.dto.Result;
import org.example.service.MaterialPickSummaryService;
import org.example.service.ProductionInboundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 单据详情（变更-047）—— 图片识别「单据号已存在 → 载入编辑」的数据来源。
 *
 * <p><b>为什么新开而不是复用 /api/pick/list、/api/inbound/list</b>：
 * 那两个是「列表」接口，日期区间必传（变更-035）、VO 里没有行 id ——
 * 撑不起「查一张单据的全部明细并保留行身份」这件事。
 *
 * <p>路径在 {@code /api/work-order/**} 前缀内，被 {@code SaTokenConfigure} 的
 * 只读闸门（决策-004）收归 admin；注解再收一道，与 confirm 同一权限
 * （同一张编辑页的两个动作，不该一个能查一个不能存）。
 */
@RestController
@RequestMapping("/api/work-order/document")
public class WorkOrderDocumentController {

    private static final String TYPE_PICK_SUMMARY = "material_pick_summary";
    private static final String TYPE_PRODUCTION_INBOUND = "production_inbound";

    @Autowired
    private MaterialPickSummaryService materialPickSummaryService;

    @Autowired
    private ProductionInboundService productionInboundService;

    /**
     * 一张单据的全部明细行。
     *
     * <pre>
     * GET /api/work-order/document/detail?billType=&amp;documentNo=
     * </pre>
     *
     * <p><b>单据号不存在按成功返回</b>（{@code exists=false, rowCount=0, rows=[]}）：
     * 「查无此单」是正常分支 —— 前端据此判定「插入」，不是错误。
     * 真正的错误只有两类：单据类型不合法、单据号为空 —— 文案与 confirm 逐字相同
     * （同一个原因在两个接口里说法不一致，排查时要多花一倍时间）。
     *
     * <p>{@code billType} / {@code documentNo} 声明为非必传再手工校验，而不是用
     * {@code required=true} 交给 Spring：后者会抛 BindException，前端拿到的是
     * 400 + 「请求参数不合法」，而这里要给的是与 confirm 同款的业务文案。
     */
    @SaCheckPermission("work_order:import")
    @GetMapping("/detail")
    public Result<DocumentDetailVO> detail(
            @RequestParam(value = "billType", required = false) String billType,
            @RequestParam(value = "documentNo", required = false) String documentNo) {

        String type = billType == null ? "" : billType.trim();
        if (!TYPE_PICK_SUMMARY.equals(type) && !TYPE_PRODUCTION_INBOUND.equals(type)) {
            return Result.fail("不支持的单据类型：" + billType
                    + "（仅支持 " + TYPE_PICK_SUMMARY + " / " + TYPE_PRODUCTION_INBOUND + "）");
        }
        if (!StringUtils.hasText(documentNo)) {
            return Result.fail("单据号为空，无法入库");
        }

        String docNo = documentNo.trim();
        List<DocumentRowVO> rows = TYPE_PICK_SUMMARY.equals(type)
                ? materialPickSummaryService.listByDocument(docNo)
                : productionInboundService.listByDocument(docNo);

        DocumentDetailVO vo = new DocumentDetailVO();
        vo.setBillType(type);
        vo.setDocumentNo(docNo);
        vo.setExists(!rows.isEmpty());
        vo.setRowCount(rows.size());
        vo.setRows(rows);
        // 单据图 = 第一张有图的行图（一张图就是一张单据）；全都没图则为 null
        for (DocumentRowVO row : rows) {
            if (StringUtils.hasText(row.getImageUrl())) {
                vo.setImageUrl(row.getImageUrl());
                vo.setThumbnailUrl(row.getThumbnailUrl());
                break;
            }
        }
        return Result.success(vo);
    }
}
