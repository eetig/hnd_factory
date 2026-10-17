package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.PurchaseTrackingQueryDTO;
import org.example.dto.PurchaseTrackingSaveDTO;
import org.example.dto.PurchaseTrackingSummaryQueryDTO;
import org.example.dto.PurchaseTrackingSummaryVO;
import org.example.dto.PurchaseTrackingVO;

/**
 * 采购进度跟踪。
 *
 * <p>这一页是「需求登记 → 提 OA 单 → 到货」的流水台账。物料名称与编码按**快照文本**存，
 * 编码会被登记进 {@code material_stock} 的采购登记档（0000 库位），详见实现类里的说明。
 */
public interface PurchaseTrackingService {

    /** 分页列表：关键字（模糊）+ 提出需求日期区间 */
    IPage<PurchaseTrackingVO> listPage(PurchaseTrackingQueryDTO query);

    /**
     * 汇总计数（待提OA/等货/临期/超期/已到货/部分到货），筛选口径与列表一致。
     *
     * <p>给页面顶部那条汇总栏用。**不分页**，返回的是全量计数而不是当前页的计数（ADR-0003）。
     */
    PurchaseTrackingSummaryVO summary(PurchaseTrackingSummaryQueryDTO query);

    /**
     * 保存（新增或修改）。
     *
     * @return 落库后的主键
     */
    Long save(PurchaseTrackingSaveDTO dto);

    /** 删除一条 */
    void delete(Long id);
}
