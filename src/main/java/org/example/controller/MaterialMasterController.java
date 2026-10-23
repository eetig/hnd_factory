package org.example.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.MaterialDetailVO;
import org.example.dto.MaterialFacetsVO;
import org.example.dto.MaterialMatchVO;
import org.example.dto.MaterialQueryDTO;
import org.example.dto.MaterialVO;
import org.example.dto.PageResult;
import org.example.dto.Result;
import org.example.service.MaterialMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物料主数据查询。
 *
 * <p>查询类接口，免登录 —— 与 /api/pick/list、/api/inbound/list 的既有约定一致
 * （本工程用注解式鉴权，未加 @SaCheckLogin 即为放行）。
 *
 * <p>三个入口各司其职（2026-10-23，变更-159）：
 * <ul>
 *   <li>{@code /match}、{@code /search} —— 图片解析按名称反查/人工挑物料，返回精简 VO；</li>
 *   <li>{@code /page} —— 物料主数据页的列表（一个物料一行，带类型/组/价格/状态）；</li>
 *   <li>{@code /detail}、{@code /facets} —— 详情（含评估类型价格表）与左栏计数。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/material")
public class MaterialMasterController {

    @Autowired
    private MaterialMasterService materialMasterService;

    /**
     * 按识别出的物料名称匹配编码。
     *
     * <p>返回 { matched, candidates }：matched 仅在唯一严格命中时非空；
     * 其余情况下编码留空，由前端展示 candidates 供人工选择。
     */
    @GetMapping("/match")
    public Result<MaterialMatchVO> match(@RequestParam("name") String name) {
        return Result.success(materialMasterService.match(name));
    }

    /**
     * 人工检索物料（前端编码单元格的搜索框用）。
     */
    @GetMapping("/search")
    public Result<List<MaterialVO>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(materialMasterService.search(keyword, limit));
    }

    /** 物料主数据页列表：一个物料一行，默认不含停用 */
    @GetMapping("/page")
    public PageResult<MaterialVO> page(MaterialQueryDTO query) {
        IPage<MaterialVO> page = materialMasterService.page(query);
        return PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    /** 物料详情：主档 + 评估类型价格表（罐类物料才有） */
    @GetMapping("/detail")
    public Result<MaterialDetailVO> detail(@RequestParam("code") String code) {
        MaterialDetailVO vo = materialMasterService.detail(code);
        if (vo == null) {
            return Result.fail("没有这个物料：" + code);
        }
        return Result.success(vo);
    }

    /** 左栏计数：类型分布 + 状态分布 + 物料总数 */
    @GetMapping("/facets")
    public Result<MaterialFacetsVO> facets() {
        return Result.success(materialMasterService.facets());
    }
}
