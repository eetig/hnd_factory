package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办事项 —— 库表 {@code todo_item}（2026-11-16）。
 *
 * <h2>为什么是「服务器上的一张表」而不是手机本地</h2>
 * 使用方原话：「我的工作内容非常杂，事特别多，而且有多种时间周期跨度大的工作」。
 * 这种用法最怕的是「事记在手机上、人到了电脑前又看不见」，所以待办存在服务器上：
 * 手机上记、手机上看，换一台手机照样在，丢了手机也不丢事。
 *
 * <h2>为什么只有截止日期，没有提醒和重复</h2>
 * 先把「记得住」这件事解决：每条事只需要回答一个问题 —— 什么时候要办。
 * 提醒要系统通知权限、重复要一套规则引擎，两样都会把录入变复杂；
 * 等确实缺了再加，不预先塞进界面。
 *
 * <h2>为什么附图存 JSON</h2>
 * 一条待办的图最多几张，且永远跟着这条待办一起读、一起删；
 * 另建一张一对多的表只会多一次 join，删除时还要级联。存 JSON 数组最简单。
 */
@Data
@TableName("todo_item")
public class TodoItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 一句话说明要办什么。界面上的主文案，必填 */
    private String title;

    /** 补充说明：怎么办、找谁、卡在哪。可空 */
    private String note;

    /** 什么时候要办；null = 没定日期（界面归到「没定」） */
    private LocalDate dueDate;

    /** 0=未完成，1=已完成 */
    private Integer done;

    /** 勾完的时间，用于「已完成」按时间倒序 */
    private LocalDateTime doneAt;

    /** 附图：JSON 数组，元素是 {@code /files/xxx.jpg} */
    private String images;

    /** 谁加的（登录账号 id） */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
