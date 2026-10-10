package org.example.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.GoodsMoveSummaryVO;
import org.example.dto.GoodsMoveVO;
import org.example.dto.PageQueryDTO;
import org.example.dto.PageResult;
import org.example.dto.Result;
import org.example.service.MaterialMovementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 货物移动。
 *
 * <p>读接口全部落在 {@code /api/goods-move/**} 前缀下，因此都被 {@code SaTokenConfigure} 的
 * 只读闸门（决策-004）收归 admin。新增接口时**不要**挪到别的前缀下，那会让它变成免登录。
 */
@RestController
@RequestMapping("/api/goods-move")
public class GoodsMoveController {

    @Autowired
    private MaterialMovementService materialMovementService;

    /**
     * 货物移动列表 —— 后端分页 + 后端日期筛选。
     *
     * <p>改造前这条接口「全量返回，前端本地做日期筛选与分页」（原注释），而且控制器
     * 连参数都没接 —— 前端拼进 query 的 startDate / endDate 被静默丢掉。两处都修了。
     *
     * <p>{@code pageNum} / {@code pageSize} 必传，缺参回 400（理由见 {@link PageQueryDTO}）。
     */
    @GetMapping("/list")
    public PageResult<GoodsMoveVO> list(@Valid PageQueryDTO query) {
        IPage<GoodsMoveVO> page = materialMovementService.listPage(query);
        return PageResult.of(page.getRecords(), page.getTotal(),
                query.resolvedPageNum(), query.resolvedPageSize());
    }

    /**
     * 按「物料 + 来源库位」汇总移动数量 —— 原辅料核算页的「已报工数」来源。
     *
     * <p>不标 {@code @Valid}：它是聚合不是记录页，不收分页参数（传了也会被忽略）。
     */
    @GetMapping("/summary-by-material")
    public Result<List<GoodsMoveSummaryVO>> summaryByMaterial(DateRangeQueryDTO query) {
        return Result.success(materialMovementService.summarizeByMaterial(query));
    }
}
