package org.example.controller;

import org.example.dto.ExcelResult;
import org.example.dto.PickSummaryVO;
import org.example.service.MaterialPickSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pick")
public class MaterialPickController {

    @Autowired
    private MaterialPickSummaryService materialPickSummaryService;

    @GetMapping("/test")
    public String test() {
        return "ok";
    }

    // 领料汇总列表（全量返回，日期筛选/排序/分页由前端本地完成）—— 查询类接口，免登录
    @GetMapping("/list")
    public ExcelResult<PickSummaryVO> list() {
        List<PickSummaryVO> list = materialPickSummaryService.listAll();
        ExcelResult<PickSummaryVO> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("查询成功");
        result.setDataList(list);
        result.setTotalRow(list.size());
        result.setSuccessRow(list.size());
        return result;
    }
}
