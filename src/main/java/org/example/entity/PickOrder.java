package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 手机端「发到电脑」的领料单暂存队列 —— 库表 {@code pick_order}（2026-11-13）。
 *
 * <h2>为什么要有这张表</h2>
 * 使用方的工作流是：手机拍纸质《物资领料单》→ 核对 → 在电脑上把数据粘进 SAP。
 * 手机上导出 xlsx 再传回电脑要经过微信/文件管理器好几步，比他重新敲编码还慢。
 * 所以手机只负责「拍 + 核对 + 发」，数据先落到这张队列里，电脑端一键取用后直接导出。
 *
 * <p>队列而不是「手机直传 Excel」还有个好处：车间里没网也能先拍、先核对，
 * 等有网再发；电脑端什么时候取都行，不会因为手机锁屏丢数据。
 */
@Data
@TableName("pick_order")
public class PickOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 领料部门：决定落哪个 sheet，也决定电脑端默认选哪张模板 */
    private String dept;

    private String requester;

    /** 纸质单号（0000352 这类），仅用于人工核对，不写进 SAP */
    private String docNo;

    /** 单据日期 yyyy-MM-dd */
    private String docDate;

    private Integer itemCount;

    /**
     * 明细 JSON（[{materialCode,materialName,quantity,unit}]）。
     *
     * <p>不另建明细表：这些行只为「取用一次」而存在，用完即标记失效，
     * 建一张一对多的表只会多一次 join 和一堆级联删除。
     */
    private String itemsJson;

    /** 0=待取用，1=已取用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime usedAt;

    /** 谁发的（登录账号 id），便于排查「这条是谁传的」 */
    private Long createdBy;
}
