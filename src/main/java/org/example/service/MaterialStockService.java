package org.example.service;

import org.example.dto.ImportSaveOutcome;
import org.example.dto.MaterialStockVO;
import org.example.entity.MaterialStock;

import java.util.List;

public interface MaterialStockService {

    /**
     * 整表查询（排序 / 筛选 / 分页由前端本地完成，与 /api/pick/list 的既有约定一致）。
     *
     * <p>物料名称与规格**联查 material_master** 带出（按物料编码）；主数据里没有的为 null。
     */
    List<MaterialStockVO> listAll();

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
