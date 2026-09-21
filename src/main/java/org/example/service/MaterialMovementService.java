package org.example.service;

import org.example.dto.ExcelResult;
import org.example.dto.GoodsMoveVO;
import org.example.entity.MaterialMovement;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaterialMovementService {
    ExcelResult<Void> importGoodsMove(MultipartFile file, String templateCode);

    // 查询货物移动列表（全量，日期筛选/排序由前端本地完成）
    ExcelResult<GoodsMoveVO> listGoodsMoves();

    // 批量保存导入的货物移动数据（返回保存条数）
    int saveImported(List<MaterialMovement> list);
}
