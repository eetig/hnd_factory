package org.example.feign;

import org.example.dto.FileAttachmentDTO;
import org.example.dto.FileQueryDTO;
import org.example.feign.fallback.FileFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "hnd-file", fallback = FileFeignFallback.class)
public interface FileFeignClient {
    @PostMapping("/api/file/attachment/list")
    List<FileAttachmentDTO> getAttachmentList(@RequestBody FileQueryDTO query);
}
