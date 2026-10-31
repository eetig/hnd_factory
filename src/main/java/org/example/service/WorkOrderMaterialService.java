package org.example.service;

import org.example.dto.WorkOrderMaterialVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 按工单查投料（2026-10-31，变更-172）。 */
public interface WorkOrderMaterialService {

    /**
     * 批量取多张工单的投料情况（弹层一次展示多张工单，不能一个工单一次请求）。
     *
     * @param orderNos 工单号；空集合直接返回空 Map
     * @return 工单号 → 投料；完全没有投料记录的工单不会出现在 Map 里
     */
    Map<String, WorkOrderMaterialVO> byOrderNos(Collection<String> orderNos);

    /** 方便测试/单查 */
    List<WorkOrderMaterialVO> list(Collection<String> orderNos);
}
