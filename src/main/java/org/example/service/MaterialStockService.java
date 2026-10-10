package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.dto.ImportSaveOutcome;
import org.example.dto.MaterialStockQueryDTO;
import org.example.dto.MaterialStockVO;
import org.example.entity.MaterialStock;

import java.util.List;

public interface MaterialStockService {

    /**
     * 「物料查询」页的分页查询：后端分页 + 后端关键字/库存筛选（整改 2026-10-10）。
     *
     * <p>改造前是 {@link #listAll}「整表返回，排序 / 筛选 / 分页由前端本地完成」，
     * 且两张源表是在内存里合并的。现在合并、筛选、排序、分页全落到 SQL（见
     * {@code resources/mapper/MaterialStockMapper.xml}）。
     *
     * <p>物料名称与规格仍然是**主数据优先、回退库存表那一份**；两边都没有的为 null
     * （前端显示「/」）。
     */
    IPage<MaterialStockVO> listPage(MaterialStockQueryDTO query);

    /**
     * 导入落库：本次文件视为「当前库存快照」。
     *
     * <p>① 先按本次涉及的工厂把已有行的 stock_qty 清零但**保留行** —— 某物料这次没出现在
     * 导出里（用完 / 清空）时，页面上仍要能查到它的物料信息；
     * ② 再按 (工厂+物料编码+存储地点) upsert 本次的行。
     */
    ImportSaveOutcome saveImported(List<MaterialStock> rows);

    /** 手工新增一行（页面先只读，接口先备齐） */
    boolean add(MaterialStock row);

    /** 按 id 修改 */
    boolean update(MaterialStock row);

    /** 按 id 删除 */
    boolean remove(Long id);
}
