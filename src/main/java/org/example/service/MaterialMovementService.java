package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.DateRangeQueryDTO;
import org.example.dto.ExcelResult;
import org.example.dto.GoodsMoveSummaryVO;
import org.example.dto.GoodsMoveVO;
import org.example.dto.PageQueryDTO;
import org.example.dto.WorkOrderImportSaveVO;
import org.example.entity.MaterialMovement;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaterialMovementService {
    ExcelResult<Void> importGoodsMove(MultipartFile file, String templateCode);

    /**
     * 货物移动列表：后端分页 + 后端日期筛选（整改 2026-10-10）。
     *
     * <p>改造前这个方法既不收参数也不做筛选 —— 前端明明把
     * {@code startDate} / {@code endDate} 拼进了 query（{@code useGoodsMoveData.js}），
     * 但控制器签名里一个 {@code @RequestParam} 都没有，参数被**静默丢掉**，
     * 前端只好把整表拿回来再本地按日期过滤。这次顺手把这个漏接收的参数补上。
     */
    IPage<GoodsMoveVO> listPage(PageQueryDTO query);

    /**
     * 货物移动「按物料 + 来源库位汇总」—— 原辅料核算页的「已报工数」来源。
     *
     * <p>那一页不是列表：它按物料编码累计移动数量，另有两行派生数据要按
     * 「物料编码 + 来源库位」取数（如「只统计来源库位 5003 的氯铂酸」），
     * 所以分组键必须带上来源库位。行数极少（物料数 × 库位数），全量返回。
     */
    List<GoodsMoveSummaryVO> summarizeByMaterial(DateRangeQueryDTO query);

    /**
     * 保存导入的货物移动数据，按【订单号】整体替换：
     * 订单已存在 -> 先删除该订单下全部货物移动记录，再写入本次数据；
     * 订单不存在 -> 直接写入。
     * 返回 insertCount = 新写入条数，updateCount = 被覆盖删除的旧记录数。
     */
    WorkOrderImportSaveVO saveImported(List<MaterialMovement> list);
}
