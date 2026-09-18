package org.example.feign;

import org.example.dto.ExcelResult;
import org.example.dto.GoodsMoveExcelDTO;
import org.example.dto.WorkOrderExcelDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

// name = excel-import-service 和 excel微服务 spring.application.name 保持一致！
// 默认 SpringEncoder 内部已集成 feign-form-spring，multipart 上传无需额外配置
@FeignClient(name = "excel-import-service")
public interface ExcelParseFeign {
    @PostMapping(value = "/excel/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ExcelResult<GoodsMoveExcelDTO> parseExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam("templateCode") String templateCode);

    @PostMapping(value = "/excel/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ExcelResult<WorkOrderExcelDTO> parseWorkOrderExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam("templateCode") String templateCode);
}
