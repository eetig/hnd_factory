package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领料解析用的 SAP 导入模板 —— 库表 {@code pick_template}，见 docs/schema.sql（2026-11-12）。
 *
 * <h2>为什么把文件存进数据库</h2>
 * 模板来自使用方「从 SAP 导出的空 Excel」，一张几十 KB。放进 {@code longblob} 而不是共享卷，
 * 是因为容器重建、换机器、回滚版本都不该让模板消失，也不用为了它再加一个挂载点。
 *
 * <h2>为什么不写死列号</h2>
 * 使用方明确「模板很多种，必须能自己加」，而 SAP 导出的模板列位置并不统一（有的把「单据数量」
 * 放在第 10 列，有的前后还夹着借贷、需求数量）。所以上传时读表头自动认列，命中不了就报错让人
 * 改模板，而不是猜一个列号把数量写进「需求数量」——那种错到了 SAP 里很难查。
 */
@Data
@TableName("pick_template")
public class PickTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板名，用户自己起（如 P7单资产领用） */
    private String name;

    /** 单据类型（资产领用 / 维修物资领料 / …）；同一部门可能有多张，导入前要选手选一张 */
    private String docType;

    /** 领料部门；为空表示通用模板 */
    private String dept;

    /** 表头所在行（0 基） */
    private Integer headerRow;

    /** 数据起始行（0 基）——表头下一行 */
    private Integer dataStartRow;

    /** 列映射 JSON：{"material":6,"qty":9,"unit":10,"factory":14,"warehouse":15} */
    private String colMap;

    /** 原始文件名，仅用于展示与排错 */
    private String fileName;

    /** 模板 xlsx 原始字节；示例行保留着，导出时用它取工厂/仓库默认值 */
    private byte[] fileData;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
