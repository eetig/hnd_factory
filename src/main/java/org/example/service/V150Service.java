package org.example.service;

import org.example.dto.ExcelResult;
import org.example.dto.V150ProductionVO;

import java.time.LocalDate;

public interface V150Service {
    // V150 本月报工生产情况（startDate/endDate 可选，null 时默认本月1号~今天）
    ExcelResult<V150ProductionVO> getV150Production(LocalDate startDate, LocalDate endDate);
}
