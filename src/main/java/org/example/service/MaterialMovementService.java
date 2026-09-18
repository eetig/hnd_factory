package org.example.service;

import org.example.dto.ExcelResult;
import org.example.dto.GoodsMoveVO;
import org.springframework.web.multipart.MultipartFile;

public interface MaterialMovementService {
    ExcelResult<Void> importGoodsMove(MultipartFile file, String templateCode);

    // 查询货物移动列表（全量，日期筛选/排序由前端本地完成）
    ExcelResult<GoodsMoveVO> listGoodsMoves();
}
