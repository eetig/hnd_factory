package org.example.feign.fallback;

import org.example.dto.FileAttachmentDTO;
import org.example.dto.FileQueryDTO;
import org.example.feign.FileFeignClient;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

@Component
public class FileFeignFallback implements FileFeignClient {
    @Override
    public List<FileAttachmentDTO> getAttachmentList(FileQueryDTO query) {
        // 文件服务不可用时返回空数组，不中断领料单查询
        return Collections.emptyList();
    }
}
