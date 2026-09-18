package org.example.controller;

import org.example.dto.ExcelResult;
import org.example.dto.V150ProductionVO;
import org.example.service.V150Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v150")
public class V150Controller {

    @Autowired
    private V150Service v150Service;

    // V150 本月报工生产情况（startDate/endDate 可选，默认本月1号~今天）
    @GetMapping("/production")
    public ExcelResult<V150ProductionVO> production(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return v150Service.getV150Production(startDate, endDate);
    }
}
