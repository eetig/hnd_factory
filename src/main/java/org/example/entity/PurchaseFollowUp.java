package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采购催货留痕（2026-11-18）。
 *
 * <p>为什么要单独一张表而不是在 {@code purchase_tracking} 上写一个「最后催货时间」：
 * 使用方的原话是「每次都要留痕」。只存最后时间的话，事后问「这单我催过几次、
 * 是不是三天前刚催过又催了一遍」就答不出来了 —— 而催货恰恰最需要知道间隔。
 *
 * <p>一行 = 一次「催一下」。数量不会多（一单一年也就几次），不做分页、不做删除，
 * 只按 {@code tracking_id} 索引查出来倒序展示。
 */
@Data
@TableName("purchase_follow_up")
public class PurchaseFollowUp {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** purchase_tracking.id */
    private Long trackingId;

    /** 催的时间 */
    private LocalDateTime pushedAt;

    /** 谁催的（登录账号的显示名） */
    private String pushedBy;
}
