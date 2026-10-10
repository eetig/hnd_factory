package org.example.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.MaterialDocQueryDTO;
import org.example.dto.InboundSummaryGroupVO;
import org.example.dto.PageResult;
import org.example.dto.ProductionInboundVO;
import org.example.dto.Result;
import org.example.service.ProductionInboundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 入库汇总。
 *
 * <p>读接口全部落在 {@code /api/inbound/**} 前缀下，因此都被 {@code SaTokenConfigure} 的
 * 只读闸门（决策-004）收归 admin —— 与前端「入库汇总」Tab 的 {@code adminOnly: true} 一致。
 * 新增接口时**不要**挪到别的前缀下，那会让它变成免登录。
 */
@RestController
@RequestMapping("/api/inbound")
public class ProductionInboundController {

    @Autowired
    private ProductionInboundService productionInboundService;

    /**
     * 入库汇总列表 —— 后端分页 + 后端条件查询。
     *
     * <p>改造前这条接口是「全量返回，日期筛选/排序/分页由前端本地完成」（原注释）。
     *
     * <p>{@code pageNum} / {@code pageSize} 必传，缺参回 400（理由见 {@link org.example.dto.PageQueryDTO}）。
     */
    @GetMapping("/list")
    public PageResult<ProductionInboundVO> list(@Valid MaterialDocQueryDTO query) {
        IPage<ProductionInboundVO> page = productionInboundService.listPage(query);
        return PageResult.of(page.getRecords(), page.getTotal(),
                query.resolvedPageNum(), query.resolvedPageSize());
    }

    /**
     * 物料名称候选（筛选弹窗的下拉项）—— 分页之后前端不再持有全量数据，候选必须由服务端给。
     */
    @GetMapping("/material-options")
    public Result<List<String>> materialOptions(DateRangeQueryDTO query) {
        return Result.success(productionInboundService.listMaterialNames(query));
    }

    /**
     * 按物料汇总入库数 —— 工单核算页的数据来源（那一页不是列表，是按物料 group by 的聚合表）。
     *
     * <p>不标 {@code @Valid}：它是聚合不是记录页，不收分页参数（传了也会被忽略）。
     */
    @GetMapping("/summary-by-material")
    public Result<List<InboundSummaryGroupVO>> summaryByMaterial(MaterialDocQueryDTO query) {
        return Result.success(productionInboundService.summarizeByMaterial(query));
    }
}
