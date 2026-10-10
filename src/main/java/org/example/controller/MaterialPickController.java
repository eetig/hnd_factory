package org.example.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.MaterialDocQueryDTO;
import org.example.dto.PickSummaryGroupVO;
import org.example.dto.PageResult;
import org.example.dto.PickSummaryVO;
import org.example.dto.Result;
import org.example.service.MaterialPickSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 领料汇总。
 *
 * <p>读接口全部落在 {@code /api/pick/**} 前缀下，因此都被 {@code SaTokenConfigure} 的
 * 只读闸门（决策-004）收归 admin —— 与前端「领料汇总」Tab 的 {@code adminOnly: true} 一致。
 * 新增接口时**不要**挪到别的前缀下，那会让它变成免登录。
 */
@RestController
@RequestMapping("/api/pick")
public class MaterialPickController {

    @Autowired
    private MaterialPickSummaryService materialPickSummaryService;

    @GetMapping("/test")
    public String test() {
        return "ok";
    }

    /**
     * 领料汇总列表 —— 后端分页 + 后端条件查询。
     *
     * <p>改造前这条接口是「全量返回，日期筛选/排序/分页由前端本地完成」（原注释），
     * 前端因此持有一份页面打开那一刻的快照：图片解析入库后回到本页，无论怎么改筛选日期
     * 都看不到新记录，非重启 App 不可。现在每个筛选条件、每次翻页都是一次真实查询。
     *
     * <p>{@code pageNum} / {@code pageSize} 必传，缺参回 400（理由见 {@link org.example.dto.PageQueryDTO}）。
     *
     * @param query 日期区间 + 物料名称 / 编码 / 单据号 + 分页
     */
    @GetMapping("/list")
    public PageResult<PickSummaryVO> list(@Valid MaterialDocQueryDTO query) {
        IPage<PickSummaryVO> page = materialPickSummaryService.listPage(query);
        return PageResult.of(page.getRecords(), page.getTotal(),
                query.resolvedPageNum(), query.resolvedPageSize());
    }

    /**
     * 物料名称候选（筛选弹窗的下拉项）。
     *
     * <p>改造前是前端从「全量数据」里 {@code new Set(...)} 现算的 —— 分页之后前端不再持有
     * 全量数据，候选必须由服务端给。只按日期区间取，不带物料条件（否则选中一个之后就改选不了）。
     */
    @GetMapping("/material-options")
    public Result<List<String>> materialOptions(DateRangeQueryDTO query) {
        return Result.success(materialPickSummaryService.listMaterialNames(query));
    }

    /**
     * 按物料汇总领料数 —— 原辅料核算页的数据来源。
     *
     * <p>那一页不是列表，是「以领料汇总当前筛选后的物料名称去重为行、领料数按物料累加」的
     * 聚合表；列表改成分页之后，聚合必须由服务端按同一套筛选条件算完再给。
     *
     * <p>不标 {@code @Valid}：它是聚合不是记录页，不收分页参数（传了也会被忽略）。
     */
    @GetMapping("/summary-by-material")
    public Result<List<PickSummaryGroupVO>> summaryByMaterial(MaterialDocQueryDTO query) {
        return Result.success(materialPickSummaryService.summarizeByMaterial(query));
    }
}
