package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.PurchaseTrackingQueryDTO;
import org.example.dto.PurchaseTrackingSaveDTO;
import org.example.dto.PurchaseTrackingSummaryQueryDTO;
import org.example.dto.PurchaseTrackingSummaryVO;
import org.example.dto.PurchaseTrackingVO;

/**
 * 采购需求详情。
 *
 * <p>这一页是「需求登记 → 提 OA 单 → 到货」的流水台账。物料名称与编码按**快照文本**存，
 * 编码会被登记进 {@code material_stock} 的采购登记档（0000 库位），详见实现类里的说明。
 */
public interface PurchaseTrackingService {

    /** 分页列表：关键字（模糊）+ 提出需求日期区间 */
    IPage<PurchaseTrackingVO> listPage(PurchaseTrackingQueryDTO query);

    /**
     * 汇总计数（待提单/等货/临期/超期/已到货/部分到货），筛选口径与列表一致。
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

    /**
     * 库里已经用过的类别（去重，2026-11-09）。
     *
     * 使用方要「类别可以自己填，保存后下次出现在下拉里」——
     * 固定枚举做不到这点，所以下拉 = 基础项 ∪ 这张表里出现过的值。
     */
    java.util.List<String> categories();

    // ===== 2026-11-18：手机端「采购需求首页」要的三件事 =====

    /**
     * 一句话建一张口头卡（领导口头交代、来不及填单的时候用）。
     *
     * <p>使用方原话：「临时记录车间主任或者领导交给我的采购需求（一般是口头交代，
     * 我私下还得时间去整理）」。所以这里只收一句话 + 可选「谁要的」，
     * 编码/数量/到货日期全部留空，等整理时再补。
     *
     * @param text      一句话（落到 materialName，超长截断）
     * @param requester 谁要的（空 = 我自己）
     * @return 新记录主键
     */
    Long quickCreate(String text, String requester);

    /**
     * 记一次催货（留痕，不改需求本身）。
     *
     * @return 这次之后的总催货次数
     */
    int addFollowUp(Long id);

    /**
     * 这张需求的全部催货记录（按时间倒序）。
     *
     * <p>手机端详情里要「看全部催痕」：哪天人催的、催了几次。
     * 只给次数和最后时间答不出这个，所以单独出一条查询。
     */
    java.util.List<org.example.entity.PurchaseFollowUp> listFollowUps(Long id);

    /**
     * 确认验收（第二段）：先把验收单号关联上，再点确认。
     *
     * <p>使用方 2026-11-18 明确分两步：「必须要有验收单号，这只是关联了验收单据；
     * 要确认已经完成验收，这个我来点确认」。所以这里既接受本次补填的单号，
     * 也接受库里已有的单号；两者都没有就报错，不让空着确认。
     *
     * @param id           采购需求主键
     * @param acceptanceNo 验收单号（可空 = 用库里已填的）
     */
    void confirmAcceptance(Long id, String acceptanceNo);

    /**
     * 作废（记错/重复）：列表不再显示，库里留痕可查。
     */
    void voidRow(Long id);
}
