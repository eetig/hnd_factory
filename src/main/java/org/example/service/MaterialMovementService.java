package org.example.service;

import org.example.dto.ExcelResult;
import org.example.dto.GoodsMoveVO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialMovement;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaterialMovementService {
    ExcelResult<Void> importGoodsMove(MultipartFile file, String templateCode);

    // 查询货物移动列表（全量，日期筛选/排序由前端本地完成）
    ExcelResult<GoodsMoveVO> listGoodsMoves();

    /**
     * 保存导入的货物移动数据，按【订单号】整体替换：
     * 订单已存在 -> 先删除该订单下全部货物移动记录，再写入本次数据；
     * 订单不存在 -> 直接写入。
     * 返回 insertCount = 新写入条数，updateCount = 被覆盖删除的旧记录数。
     */
    WorkOrderImportSaveVO saveImported(List<MaterialMovement> list);
}
